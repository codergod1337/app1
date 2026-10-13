import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { FileSubClassSymbol } from '../filesubclass/FileSubClassSymbol.tsx'
import type { SearchResultListProps } from '../search/searchCores.ts'
import { SearchHitReasons } from '../search/SearchHitReasons.tsx'
import { SolrHookValues } from '../solrmanager/SolrHookValues.tsx'
import {
  DMS_CORE_KEY,
  DMS_STATE_ACTIVE,
  DOCUMENT_LANGUAGE_FIELD_NAME,
  dmsFieldLabel,
  dmsSolrDocumentPath,
  FILE_SUB_CLASS_FIELD_NAME,
  STATUS_FIELD_NAME,
} from './Dms.ts'
import { readableBytes } from '../../components/readableBytes.ts'
import { DmsDocumentLanguageFlag } from './DmsDocumentLanguageFlag.tsx'
import { DmsSolrDocumentActionButtons } from './DmsSolrDocumentActionButtons.tsx'
import { DmsSolrDocumentIdentifiersHint } from './DmsSolrDocumentFacts.tsx'

/** Eine Liste, egal ob Solr einen Wert oder mehrere liefert */
function asList(value: unknown): string[] {
  if (value === null || value === undefined) {
    return []
  }
  return (Array.isArray(value) ? value : [value]).map((entry) => String(entry))
}

/**
 * Die Treffer des Kerns DMS in der Suche, nach DmsTreffer der Vorlage: je Treffer das Symbol der Dateiart oder das
 * Vorschaubild, der Dateiname als Weg zur Dokumentseite mit der Beschreibung im Hoverlay, die Fakten in einer Zeile,
 * die Hooks mit ihren Werten, darunter der Treffergrund, rechts die bekannten Aktionen (Schreibknöpfe nur, wenn das
 * Backend den Treffer als writable meldet). Ein Klick auf einen Hook-Wert loggt ihn als Filter ein, hat sein Hook eine
 * Mappe als Ziel, öffnet er die Mappe (SolrHookValueBadge). Die Felder eines
 * Treffers (fieldValues) sind die der Trefferbox plus die durchsuchten, nicht das ganze Dokument.
 */
export function DmsSearchResultList({ hits, origin, onFilter }: SearchResultListProps) {
  const { t, i18n } = useTranslation()
  const { language } = useLanguage()
  const navigate = useNavigate()
  const { fileSubClasses, solrHooks, entityStates, solrFields } = useMasterData()
  const labelOf = (name: string, fallback: string) => dmsFieldLabel(solrFields, name, fallback, language)

  function day(value: unknown): string | null {
    if (typeof value !== 'string') {
      return null
    }
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString(i18n.language)
  }

  return (
    <div className="search-hits">
      {hits.map((hit) => {
        const document = hit.fieldValues
        const id = typeof document.id === 'string' ? document.id : ''
        const fileName = typeof document.fileName === 'string' ? document.fileName : id
        const fileSubClassKey =
          typeof document[FILE_SUB_CLASS_FIELD_NAME] === 'string' ? document[FILE_SUB_CLASS_FIELD_NAME] : null
        const fileSubClass = fileSubClasses?.find((candidate) => candidate.key === fileSubClassKey) ?? null
        const fileSubClassName = fileSubClass
          ? translate(fileSubClass.displayName, language) || fileSubClass.key
          : (fileSubClassKey ?? '')
        const status = typeof document[STATUS_FIELD_NAME] === 'string' ? document[STATUS_FIELD_NAME] : null
        const state = entityStates?.find((candidate) => candidate.key === status)
        const statusName = state ? translate(state.displayName, language) || state.key : status
        const description =
          typeof document.fileDescription === 'string' ? translate(document.fileDescription, language) : ''
        const version = document.version
        const fileIndex = typeof document.fileIndex === 'string' ? document.fileIndex : null
        const createdDay = day(document.documentCreatedAt)

        const facts: { key: string; label: string; value: ReactNode }[] = []
        if (fileSubClassName !== '') {
          facts.push({ key: 'fsc', label: labelOf(FILE_SUB_CLASS_FIELD_NAME, t('dms.document.fileSubClass')), value: fileSubClassName })
        }
        if (status !== null && status !== DMS_STATE_ACTIVE) {
          facts.push({ key: 'status', label: labelOf(STATUS_FIELD_NAME, t('dms.document.status')), value: statusName })
        }
        if (typeof version === 'number') {
          facts.push({ key: 'version', label: labelOf('version', t('dms.document.version')), value: version })
        }
        if (fileIndex !== null && fileIndex !== '') {
          facts.push({ key: 'index', label: labelOf('fileIndex', t('dms.document.fileIndex')), value: fileIndex })
        }
        const size = readableBytes(document.fileSizeBytes)
        if (size !== '') {
          facts.push({ key: 'size', label: labelOf('fileSizeBytes', t('dms.document.fileSize')), value: size })
        }
        if (createdDay !== null) {
          facts.push({ key: 'created', label: labelOf('documentCreatedAt', t('dms.document.createdAt')), value: createdDay })
        }
        if (typeof document[DOCUMENT_LANGUAGE_FIELD_NAME] === 'string') {
          facts.push({
            key: 'language',
            label: labelOf(DOCUMENT_LANGUAGE_FIELD_NAME, t('dms.document.documentLanguage')),
            value: <DmsDocumentLanguageFlag code={document[DOCUMENT_LANGUAGE_FIELD_NAME]} />,
          })
        }

        const hooksWithValues = (solrHooks ?? []).filter((hook) => asList(document[hook.key]).length > 0)

        return (
          <div key={id} className="search-hit">
            <Hoverlay text={fileSubClassName !== '' ? fileSubClassName : null}>
              <span className="search-hit-symbol">
                {typeof document.bildVorschauBase64Png === 'string' ? (
                  <img
                    className="search-hit-preview"
                    src={`data:image/png;base64,${document.bildVorschauBase64Png}`}
                    alt={fileName}
                  />
                ) : fileSubClass ? (
                  <FileSubClassSymbol fileSubClass={fileSubClass} />
                ) : (
                  <Icon name="dms" />
                )}
              </span>
            </Hoverlay>

            <div className="search-hit-body">
              <div className="search-hit-head">
                <Hoverlay text={description !== '' ? description : null}>
                  <button
                    type="button"
                    className="search-hit-name"
                    onClick={() => navigate(dmsSolrDocumentPath(id), { state: origin })}
                  >
                    {fileName}
                  </button>
                </Hoverlay>
                <DmsSolrDocumentIdentifiersHint document={document} />
              </div>

              {facts.length > 0 && (
                <div className="search-hit-facts">
                  {facts.map((fact) => (
                    <span key={fact.key} className="search-hit-fact">
                      <span className="text-muted">{fact.label}:</span> {fact.value}
                    </span>
                  ))}
                </div>
              )}

              {hooksWithValues.length > 0 && (
                <div className="search-hit-hooks">
                  {hooksWithValues.map((hook) => (
                    <SolrHookValues
                      key={hook.key}
                      solrHook={hook}
                      values={asList(document[hook.key])}
                      language={language}
                      origin={origin}
                      onSearchFilter={onFilter}
                      badgeClassName="search-hit-hook-value"
                    />
                  ))}
                </div>
              )}

              <SearchHitReasons reasons={hit.reasons} coreKey={DMS_CORE_KEY} language={language} />
            </div>

            <div className="search-hit-actions">
              {/* writable sagt das Backend je Treffer: Stift, neue Fassung, Freigeben nur dann */}
              <DmsSolrDocumentActionButtons document={document} origin={origin} writeAclMatchForCurrentUser={hit.writable} />
            </div>
          </div>
        )
      })}
    </div>
  )
}
