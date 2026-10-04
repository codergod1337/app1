import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import type { OriginState } from '../../components/Origin.ts'
import { AccessRoleBadge } from '../accessrole/AccessRoleBadge.tsx'
import { FileSubClassPicker } from '../filesubclass/FileSubClassPicker.tsx'
import { FileSubClassSymbol } from '../filesubclass/FileSubClassSymbol.tsx'
import { solrHookValuesOf } from '../solrmanager/SolrHook.ts'
import { SolrHookFields } from '../solrmanager/SolrHookFields.tsx'
import { DMS_STATE_DELETED, editDmsSolrDocument, FILE_SUB_CLASS_FIELD_NAME, type DmsSolrDocumentForClient } from './Dms.ts'
import { DmsSolrDocumentActionButtons } from './DmsSolrDocumentActionButtons.tsx'
import { DmsSolrDocumentIdentifiersHint, DmsSolrDocumentInfobox } from './DmsSolrDocumentFacts.tsx'
import { DmsSolrDocumentGrantsHint } from './DmsSolrDocumentGrantsHint.tsx'

interface DmsSolrDocumentDetailsBoxProps {
  loaded: DmsSolrDocumentForClient
  origin?: OriginState
  /** Knopf „Schließen“ rechts im Kopf */
  onClose: () => void
  /** nach dem weichen Löschen: das Dokument neu laden oder die Seite verlassen */
  onDeleted: () => void
}

/** Eine Liste, egal ob Solr einen Wert oder mehrere liefert */
function asList(value: unknown): string[] {
  if (value === null || value === undefined) {
    return []
  }
  return (Array.isArray(value) ? value : [value]).map((entry) => String(entry))
}

/** Die Dateiart-Kachel in den Details ist gesperrt: Hier wird nichts gewählt, nur gezeigt */
function ignoreChoice() {
  // gesperrte Kachel, nur Anzeige
}

/**
 * Die Metadaten-Ansicht eines DMS-Dokuments, nur eine ContentBox ohne eigenen Rahmen (nach DmsDetails der Vorlage),
 * damit sie auf der eigenen Seite und später im Dokumente-Tab der Artikelmappe gleich aussieht. Von oben nach unten:
 * Kopf mit Dateiart-Symbol, Name, Dateiart, dem Schlüssel mit den Kennungen im Hoverlay und den Aktionen; die Infobox
 * mit dem Feststehenden; Beschreibung; die Dateiart als gesperrte Kachel; Hooks nur lesend; dann, was die Dateiart
 * bewirkt: Vorschaubild, Volltext und Zugriffsrechte als Aufklapper, nur wenn der User die Felder lesen darf. Ein Feld,
 * das fehlt, ist für diesen User nicht sichtbar, kein Fehler.
 */
export function DmsSolrDocumentDetailsBox({ loaded, origin, onClose, onDeleted }: DmsSolrDocumentDetailsBoxProps) {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const { fileSubClasses, solrHooks, accessRoles } = useMasterData()
  const [fullTextOpen, setFullTextOpen] = useState(false)
  const [aclOpen, setAclOpen] = useState(false)
  const [deleting, setDeleting] = useState(false)

  const document = loaded.document
  const id = String(document.id ?? '')
  const fileName = typeof document.fileName === 'string' ? document.fileName : id
  const fileSubClassKey = typeof document[FILE_SUB_CLASS_FIELD_NAME] === 'string' ? document[FILE_SUB_CLASS_FIELD_NAME] : null
  const fileSubClass = fileSubClasses?.find((candidate) => candidate.key === fileSubClassKey) ?? null
  const description = typeof document.fileDescription === 'string' ? translate(document.fileDescription, language) : ''
  const fullText = typeof document.searchableWords === 'string' ? document.searchableWords.trim() : null
  const readAcl = asList(document.readAcl)
  const writeAcl = asList(document.writeAcl)
  const grantedUsers = asList(document.userGrantedAccess)
  const hasAcl = 'readAcl' in document || 'writeAcl' in document

  function accessRoleBadges(keys: string[]) {
    if (keys.length === 0) {
      return <span className="text-muted">—</span>
    }
    return (
      <span className="d-inline-flex flex-wrap gap-1">
        {keys.map((key) => {
          const accessRole = accessRoles?.find((candidate) => candidate.key === key)
          return accessRole ? <AccessRoleBadge key={key} badge={accessRole} size="compact" /> : <code key={key}>{key}</code>
        })}
      </span>
    )
  }

  const title = (
    <span className="d-inline-flex align-items-center gap-2 text-break">
      {fileSubClass && <FileSubClassSymbol fileSubClass={fileSubClass} />}
      {fileName}
      {fileSubClass && (
        <span className="fw-normal text-muted">({translate(fileSubClass.displayName, language) || fileSubClass.key})</span>
      )}
      <DmsSolrDocumentIdentifiersHint document={document} />
      <DmsSolrDocumentGrantsHint id={id} />
    </span>
  )

  const actions = (
    <span className="d-inline-flex align-items-center gap-2">
      <DmsSolrDocumentActionButtons
        document={document}
        origin={origin}
        onDetailsPage
        writeAclMatchForCurrentUser={loaded.writeAclMatchForCurrentUser}
        onDelete={() => setDeleting(true)}
      />
      <button type="button" className="button-cancel" onClick={onClose}>
        {t('common.close')}
      </button>
    </span>
  )

  return (
    <>
      <ContentBox title={title} actions={actions}>
        <div className="d-flex flex-column gap-3">
          <DmsSolrDocumentInfobox document={document} />

          <div>
            <div className="small text-muted">{t('dms.document.description')}</div>
            <div className="text-break">{description !== '' ? description : '—'}</div>
          </div>

          <hr className="dms-section-divider" />

          {fileSubClass && (
            <FileSubClassPicker
              fileSubClasses={[fileSubClass]}
              value={fileSubClass.key}
              onChange={ignoreChoice}
              language={language}
              disabled
              title={t('dms.document.fileSubClass')}
            />
          )}

          <hr className="dms-section-divider" />

          <SolrHookFields hooks={solrHookValuesOf(document, solrHooks ?? [])} language={language} />

          <hr className="dms-section-divider" />

          <div className="d-flex flex-column gap-2">
            {typeof document.bildVorschauBase64Png === 'string' && (
              <img
                className="dms-preview-image"
                src={`data:image/png;base64,${document.bildVorschauBase64Png}`}
                alt={fileName}
              />
            )}

            {fullText !== null && fullText === '' && <span className="small text-muted">{t('dms.document.noFullText')}</span>}
            {fullText !== null && fullText !== '' && (
              <>
                <button type="button" className="dms-collapsible" aria-expanded={fullTextOpen} onClick={() => setFullTextOpen(!fullTextOpen)}>
                  <Icon name={fullTextOpen ? 'expand' : 'collapsed'} />
                  {t('dms.document.fullText', { count: fullText.split(/\s+/).length })}
                </button>
                {fullTextOpen && <div className="dms-full-text">{fullText}</div>}
              </>
            )}

            {hasAcl && (
              <>
                <button type="button" className="dms-collapsible" aria-expanded={aclOpen} onClick={() => setAclOpen(!aclOpen)}>
                  <Icon name={aclOpen ? 'expand' : 'collapsed'} />
                  {t('dms.document.accessRights')}
                </button>
                {aclOpen && (
                  <div className="d-flex flex-column gap-2 small">
                    <div>
                      <div className="text-muted">readAcl</div>
                      {accessRoleBadges(readAcl)}
                    </div>
                    <div>
                      <div className="text-muted">writeAcl</div>
                      {accessRoleBadges(writeAcl)}
                    </div>
                    {grantedUsers.length > 0 && (
                      <div>
                        <div className="text-muted">userGrantedAccess</div>
                        <span className="d-inline-flex flex-wrap gap-1">
                          {grantedUsers.map((guid) => (
                            <code key={guid}>{guid}</code>
                          ))}
                        </span>
                      </div>
                    )}
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      </ContentBox>

      <DeletePopup
        open={deleting}
        title={t('dms.document.deleteTitle')}
        onConfirm={() => editDmsSolrDocument({ id, status: DMS_STATE_DELETED })}
        onDeleted={onDeleted}
        onClose={() => setDeleting(false)}
      >
        <Trans i18nKey="dms.document.deleteQuestion" values={{ name: fileName }} components={{ strong: <strong /> }} />
      </DeletePopup>
    </>
  )
}
