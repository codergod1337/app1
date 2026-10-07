import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { MultilingualInput } from '../../components/MultilingualInput.tsx'
import { FileSubClassPicker } from '../filesubclass/FileSubClassPicker.tsx'
import { FileSubClassSymbol } from '../filesubclass/FileSubClassSymbol.tsx'
import { useSession } from '../login/sessionContext.ts'
import { solrHookValuesOf } from '../solrmanager/SolrHook.ts'
import { SolrHookFields, type SolrHookValuesByKey } from '../solrmanager/SolrHookFields.tsx'
import {
  DOCUMENT_LANGUAGE_FIELD_NAME,
  FILE_SUB_CLASS_FIELD_NAME,
  patchDmsSolrDocument,
  type DmsSolrDocumentForClient,
} from './Dms.ts'
import { documentLanguageAppliesToFileExtension } from './DmsDocumentLanguage.ts'
import { DmsDocumentLanguagePicker } from './DmsDocumentLanguagePicker.tsx'
import { DmsSolrDocumentIdentifiersHint, DmsSolrDocumentInfobox } from './DmsSolrDocumentFacts.tsx'

interface DmsSolrDocumentEditBoxProps {
  loaded: DmsSolrDocumentForClient
  /** nach dem Speichern, mit dem Dokument, wie es jetzt steht */
  onSaved: (saved: DmsSolrDocumentForClient) => void
  onCancel: () => void
}

/** Der Tag eines Zeitpunkts fürs Datumsfeld, leer ohne Wert */
function dayOf(value: unknown): string {
  return typeof value === 'string' && value.length >= 10 ? value.slice(0, 10) : ''
}

/**
 * Ein DMS-Dokument bearbeiten, nur eine ContentBox wie die Details (nach DmsEdit der Vorlage). Änderbar: Dateiname,
 * Index, Erstelldatum als Tag, Beschreibung mehrsprachig, Dateiart (nur Klassen mit eigener Schreibrolle und
 * freigeschalteter Endung, der Wechsel schreibt die Rechte des Dokuments neu) und Hooks. Status und das Feststehende
 * sind Anzeige. Geschickt wird ein Patch: nur, was sich geändert hat, je Feld der Endwert, ein Hook als fertige Liste.
 * Das Backend prüft writeAcl und alle Regeln selbst.
 */
export function DmsSolrDocumentEditBox({ loaded, onSaved, onCancel }: DmsSolrDocumentEditBoxProps) {
  const { t } = useTranslation()
  const { sessionInfo } = useSession()
  const { fileSubClasses, solrHooks, fileExtensions, fileExtensionCollections } = useMasterData()
  const uiLanguage = useLanguage().language
  // die Sprache der Maske, startet mit der Oberfläche und lässt sich im Kopf der Box für die Beschreibung umschalten
  const [language, setLanguage] = useState<LanguageCode>(uiLanguage)

  const document = loaded.document
  const id = String(document.id ?? '')
  const originalFileName = typeof document.fileName === 'string' ? document.fileName : ''
  const originalFileIndex = typeof document.fileIndex === 'string' ? document.fileIndex : ''
  const originalDescription = typeof document.fileDescription === 'string' ? document.fileDescription : null
  const originalCreatedDay = dayOf(document.documentCreatedAt)
  const originalFileSubClassKey = typeof document[FILE_SUB_CLASS_FIELD_NAME] === 'string' ? document[FILE_SUB_CLASS_FIELD_NAME] : null
  const originalHooks = solrHookValuesOf(document, solrHooks ?? [])
  const fileExtension = typeof document.fileExtension === 'string' ? document.fileExtension : null
  const originalDocumentLanguage =
    typeof document[DOCUMENT_LANGUAGE_FIELD_NAME] === 'string' ? document[DOCUMENT_LANGUAGE_FIELD_NAME] : null
  // nur Dateien aus Endungsgruppen mit Sprache bekommen die Auswahl, dieselbe Regel wie im Backend
  const documentLanguageApplies = documentLanguageAppliesToFileExtension(
    fileExtension,
    fileExtensions ?? [],
    fileExtensionCollections ?? [],
  )

  const [fileName, setFileName] = useState(originalFileName)
  const [fileIndex, setFileIndex] = useState(originalFileIndex)
  const [description, setDescription] = useState<string | null>(originalDescription)
  const [createdDay, setCreatedDay] = useState(originalCreatedDay)
  const [documentLanguage, setDocumentLanguage] = useState<string | null>(originalDocumentLanguage)
  const [fileSubClassKey, setFileSubClassKey] = useState<string | null>(originalFileSubClassKey)
  const [hooks, setHooks] = useState<SolrHookValuesByKey>(originalHooks)
  const [saving, setSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const ownRoles = sessionInfo?.accessRoleKeys ?? []
  /**
   * Die wählbaren Dateiarten: eigene Schreibrolle und die Endung freigeschaltet, die aktuelle immer dabei, damit man
   * sieht, wo das Dokument steht. Das ist nur der Vorschlag, das Backend prüft dasselbe hart.
   */
  const selectableFileSubClasses = (fileSubClasses ?? []).filter((candidate) => {
    if (candidate.key === originalFileSubClassKey) {
      return true
    }
    const writable = (candidate.writeAccessRoleKeys ?? []).some((key) => ownRoles.includes(key))
    const extensionAllowed = fileExtension === null || (candidate.extensions ?? []).includes(fileExtension)
    return writable && extensionAllowed
  })
  const currentFileSubClass = fileSubClasses?.find((candidate) => candidate.key === originalFileSubClassKey) ?? null

  function save(event: FormEvent) {
    event.preventDefault()
    if (!loaded.writeAclMatchForCurrentUser) {
      return
    }
    setSaving(true)
    setErrorMessage(null)
    // nur die geänderten Felder, je Feld der Endwert; leer heißt leeren
    const fieldValuesByFieldName: Record<string, unknown> = {}
    if (fileName !== originalFileName) {
      fieldValuesByFieldName.fileName = fileName
    }
    if (fileIndex !== originalFileIndex) {
      fieldValuesByFieldName.fileIndex = fileIndex
    }
    if (description !== originalDescription) {
      fieldValuesByFieldName.fileDescription = description ?? ''
    }
    if (createdDay !== originalCreatedDay && createdDay !== '') {
      fieldValuesByFieldName.documentCreatedAt = createdDay
    }
    if (documentLanguageApplies && documentLanguage !== originalDocumentLanguage) {
      fieldValuesByFieldName[DOCUMENT_LANGUAGE_FIELD_NAME] = documentLanguage ?? ''
    }
    if (fileSubClassKey !== null && fileSubClassKey !== originalFileSubClassKey) {
      fieldValuesByFieldName[FILE_SUB_CLASS_FIELD_NAME] = fileSubClassKey
    }
    // je Hook die fertige Liste, nur wenn sie sich geändert hat; ein Hook, der aus der Maske verschwand, wird geleert
    for (const key of new Set([...Object.keys(originalHooks), ...Object.keys(hooks)])) {
      const before = originalHooks[key] ?? []
      const after = hooks[key] ?? []
      if (before.length !== after.length || before.some((value, index) => value !== after[index])) {
        fieldValuesByFieldName[key] = [...after]
      }
    }
    patchDmsSolrDocument({ id, fieldValuesByFieldName })
      .then(({ data }) => onSaved(data))
      .catch((error: unknown) => {
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
      })
      .finally(() => setSaving(false))
  }

  const title = (
    <span className="d-inline-flex align-items-center gap-2 text-break">
      {currentFileSubClass && <FileSubClassSymbol fileSubClass={currentFileSubClass} />}
      {originalFileName || id}
      <span className="fw-normal text-muted">({t('dms.document.editTitle')})</span>
      <DmsSolrDocumentIdentifiersHint document={document} />
    </span>
  )

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {!loaded.writeAclMatchForCurrentUser && <span className="text-warning">{t('dms.document.noWriteAcl')}</span>}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={onCancel}>
          {t('common.cancel')}
        </button>
        <button type="submit" form="dms-solr-document-edit" className="button-save" disabled={saving || !loaded.writeAclMatchForCurrentUser}>
          {t('common.save')}
        </button>
      </span>
    </>
  )

  return (
    <ContentBox title={title} footer={footer} language={{ value: language, onChange: setLanguage }}>
      <div className="d-flex flex-column gap-3">
        <DmsSolrDocumentInfobox document={document} />

        <form id="dms-solr-document-edit" className="d-flex flex-column gap-3" onSubmit={save}>
          <div className="row g-3">
            <div className="col-12 col-md-6">
              <label className="form-label" htmlFor="dms-edit-file-name">
                {t('dms.document.fileName')}
              </label>
              <input
                id="dms-edit-file-name"
                className="form-control"
                value={fileName}
                required
                disabled={saving}
                onChange={(event) => setFileName(event.target.value)}
              />
              <div className="form-text">{t('dms.document.fileNameHint')}</div>
            </div>
            <div className="col-6 col-md-3">
              <label className="form-label" htmlFor="dms-edit-file-index">
                {t('dms.document.fileIndex')}
              </label>
              <input
                id="dms-edit-file-index"
                className="form-control"
                value={fileIndex}
                maxLength={12}
                disabled={saving}
                onChange={(event) => setFileIndex(event.target.value)}
              />
            </div>
            <div className="col-6 col-md-3">
              <label className="form-label" htmlFor="dms-edit-created-day">
                {t('dms.document.createdAt')}
              </label>
              <input
                id="dms-edit-created-day"
                type="date"
                className="form-control"
                value={createdDay}
                disabled={saving}
                onChange={(event) => setCreatedDay(event.target.value)}
              />
            </div>
            {documentLanguageApplies && (
              <div className="col-12">
                <span className="form-label d-block">{t('dms.document.documentLanguage')}</span>
                <DmsDocumentLanguagePicker value={documentLanguage} onChange={setDocumentLanguage} disabled={saving} />
              </div>
            )}
          </div>
          <div>
            <label className="form-label" htmlFor="dms-edit-description">
              {t('dms.document.description')}
            </label>
            <MultilingualInput
              id="dms-edit-description"
              value={description}
              language={language}
              onChange={setDescription}
              disabled={saving}
              multiline
            />
          </div>
        </form>

        <div>
          <p className="small text-warning mb-2">{t('dms.document.fileSubClassWarning')}</p>
          <FileSubClassPicker
            fileSubClasses={selectableFileSubClasses}
            value={fileSubClassKey}
            onChange={setFileSubClassKey}
            language={language}
            disabled={saving}
            title={t('dms.document.fileSubClass')}
          />
          {fileSubClassKey !== null && fileSubClassKey !== originalFileSubClassKey && (
            <p className="small text-warning mt-2 mb-0">
              {t('dms.document.fileSubClassChange', { key: fileSubClassKey })}
            </p>
          )}
        </div>

        <SolrHookFields hooks={hooks} onChange={setHooks} language={language} disabled={saving} />
      </div>
    </ContentBox>
  )
}
