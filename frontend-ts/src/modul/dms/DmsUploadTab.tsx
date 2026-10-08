import { useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { FileDropzone } from '../../components/FileDropzone.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { FileSubClassPicker } from '../filesubclass/FileSubClassPicker.tsx'
import { SOLR_FIELD_TYPES, solrFieldLabel, type SolrField } from '../solrmanager/SolrField.ts'
import { solrHookValuesOf } from '../solrmanager/SolrHook.ts'
import { solrFieldDocument, type SolrFieldRawValue } from '../solrmanager/SolrFieldValue.ts'
import { SolrFieldValueInput } from '../solrmanager/SolrFieldValueInput.tsx'
import { SolrHookFields, type SolrHookValuesByKey } from '../solrmanager/SolrHookFields.tsx'
import {
  DMS_CORE_KEY,
  DMS_STATE_SCOPE,
  dmsFieldInput,
  FILE_SIZE_FIELD_NAME,
  FILE_SUB_CLASS_FIELD_NAME,
  fileExtensionOf,
  probeDmsUpload,
  uploadDmsFile,
  type DmsUploadProbe,
  type DmsUploadResult,
} from './Dms.ts'
import { entityStatesOfScope } from '../entitystate/EntityState.ts'
import { DmsUploadProbeResult } from './DmsUploadProbeResult.tsx'

const FORM_ID = 'dms-upload-form'

/**
 * Stufe 1 des Uploads: die Probe. Eine Datei, dazu alle Felder des Kerns DMS aus den Stammdaten, jedes mit einer
 * Eingabe nach seinem Typ, und die Hooks. Alles ist freiwillig, der User schickt, was er will, das Backend prüft jedes
 * Feld gegen die Felddefinition und nennt jede Beanstandung. Gespeichert wird nichts, die Antwort zeigt, was ankam und
 * was Tika in der Datei fand. Masken je Fall, die Felder ausblenden und vorbelegen, kommen später darüber.
 *
 * Was ein Feld in der Maske ist, sagt die Schreibregel der Felddefinition (dmsFieldInput): Die Felder des Backends
 * stehen gesperrt in der Liste, versionChain bleibt leer (nur eine neue Fassung schickt die Kette mit), fileSizeBytes liest die Maske aus der Datei, status ist
 * eine feste Liste. Die Dateiart wird über den Kacheln der Dateiart-Auswahl gewählt (FileSubClassPicker, alle
 * Dateiarten), ihre Zeile in der Liste zeigt nur die Wahl. Die Datei kommt über die Dropzone (ziehen, klicken, am
 * Telefon die Kamera), alles andere ist freie Eingabe.
 */
export function DmsUploadTab() {
  const { t } = useTranslation()
  const { solrFields, solrHooks, fileSubClasses, fileExtensions, entityStates, errorMessage: loadErrorMessage } =
    useMasterData()
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)
  const [file, setFile] = useState<File | null>(null)
  const [values, setValues] = useState<Record<string, SolrFieldRawValue>>({})
  const [hooks, setHooks] = useState<SolrHookValuesByKey>({})
  const [uploading, setUploading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [probe, setProbe] = useState<DmsUploadProbe | null>(null)
  /** das Ergebnis des echten Uploads, null nach einer Probe */
  const [uploadResult, setUploadResult] = useState<DmsUploadResult | null>(null)

  const fields = (solrFields ?? []).filter((solrField) => solrField.coreKey === DMS_CORE_KEY)
  const fileSubClassField = fields.find((solrField) => solrField.name === FILE_SUB_CLASS_FIELD_NAME) ?? null
  const chosenFileSubClassKey = values[FILE_SUB_CLASS_FIELD_NAME]
  const chosenFileSubClass =
    (fileSubClasses ?? []).find((fileSubClass) => fileSubClass.key === chosenFileSubClassKey) ?? null

  // Die Endung der Datei gegen den Katalog und die Dateiart: Das Backend prüft dasselbe, hier nur, damit es vorher auffällt
  const extension = file === null ? null : fileExtensionOf(file.name)
  const extensionKnown =
    extension === null || (fileExtensions ?? []).some((fileExtension) => fileExtension.extension === extension)
  const extensionAllowed =
    extension === null || chosenFileSubClass === null || (chosenFileSubClass.extensions ?? []).includes(extension)

  function setValue(name: string, value: SolrFieldRawValue | undefined) {
    setValues((current) => {
      const next = { ...current }
      if (value === undefined) {
        delete next[name]
      } else {
        next[name] = value
      }
      return next
    })
  }

  /** Die Datei und mit ihr die Größe: fileSizeBytes liest das Frontend, das Backend prüft es gegen die Datei */
  function chooseFile(chosen: File | null) {
    setFile(chosen)
    setValue(FILE_SIZE_FIELD_NAME, chosen === null ? undefined : String(chosen.size))
  }

  /** Das Dokument fürs Backend: Hooks sind in Solr Felder wie alle anderen, flach dazu, nur die gefüllten */
  function buildDocument(): Record<string, unknown> {
    const document = solrFieldDocument(fields, values)
    for (const [key, hookValues] of Object.entries(hooks)) {
      if (hookValues.length > 0) {
        document[key] = [...hookValues]
      }
    }
    return document
  }

  /** Die Probe: nichts wird gespeichert */
  function submit(event: FormEvent) {
    event.preventDefault()
    if (file === null || !extensionAllowed) {
      return
    }
    setUploading(true)
    setErrorMessage(null)
    setUploadResult(null)
    probeDmsUpload(file, buildDocument())
      .then((result) => setProbe(result.data))
      .catch((error: unknown) => {
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
      })
      .finally(() => setUploading(false))
  }

  /** Der echte Upload: dieselbe Vorarbeit, dann SFTP und Solr. Bis die Stapelmaske steht, der Weg zum Ausprobieren. */
  function store() {
    if (file === null || !extensionAllowed) {
      return
    }
    setUploading(true)
    setErrorMessage(null)
    uploadDmsFile(file, buildDocument())
      .then((result) => {
        setUploadResult(result.data)
        setProbe(result.data.probe)
      })
      .catch((error: unknown) => {
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
      })
      .finally(() => setUploading(false))
  }

  function renderInput(solrField: SolrField): ReactNode {
    const id = `dms-upload-${solrField.name}`
    const current = values[solrField.name]
    const currentText = typeof current === 'string' ? current : ''
    switch (dmsFieldInput(solrField)) {
      case 'backend':
        return <input id={id} className="form-control form-control-sm" disabled placeholder={t('dms.upload.backendSets')} />
      case 'chain':
        return <input id={id} className="form-control form-control-sm" disabled placeholder={t('dms.upload.chainLater')} />
      case 'fileSize':
        // aus der gewählten Datei, nicht von Hand
        return (
          <input
            id={id}
            className="form-control form-control-sm"
            readOnly
            disabled
            value={currentText}
            placeholder={t('dms.upload.fromFile')}
          />
        )
      case 'fileSubClass':
        // gewählt wird über den Kacheln, hier steht nur die Wahl
        return (
          <input
            id={id}
            className="form-control form-control-sm"
            readOnly
            disabled
            value={currentText}
            placeholder={t('dms.upload.chosenAbove')}
          />
        )
      case 'status':
        return (
          <select
            id={id}
            className="form-select form-select-sm"
            value={currentText}
            disabled={uploading}
            onChange={(event) => setValue(solrField.name, event.target.value === '' ? undefined : event.target.value)}
          >
            <option value="">{t('solrmanager.valueInput.unset')}</option>
            {entityStatesOfScope(entityStates ?? [], DMS_STATE_SCOPE).map((entityState) => (
              <option key={entityState.key} value={entityState.key}>
                {entityState.key}: {translate(entityState.displayName, language)}
              </option>
            ))}
          </select>
        )
      case 'free':
        return (
          <SolrFieldValueInput
            id={id}
            solrField={solrField}
            value={current}
            disabled={uploading}
            onChange={(value) => setValue(solrField.name, value)}
          />
        )
    }
  }

  const columns: TableColumn<SolrField>[] = [
    {
      header: t('dms.upload.field'),
      width: '15rem',
      cell: (solrField) => (
        <Hoverlay text={translate(solrField.description, language)}>
          <span className="table-header-hinted">
            {solrFieldLabel(solrField, language)}
            {solrFieldLabel(solrField, language) !== solrField.name && (
              <span className="text-muted ms-1">
                <code>{solrField.name}</code>
              </span>
            )}
          </span>
        </Hoverlay>
      ),
    },
    {
      header: t('dms.upload.type'),
      width: '9rem',
      unimportant: true,
      cell: (solrField) => (
        <code>
          {SOLR_FIELD_TYPES.find((candidate) => candidate.type === solrField.type)?.label ?? solrField.type}
          {solrField.multiValued ? ' []' : ''}
        </code>
      ),
    },
    { header: t('dms.upload.value'), cell: renderInput },
  ]

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      <span className="ms-auto d-flex gap-2">
        <button
          type="submit"
          form={FORM_ID}
          className="button-other"
          disabled={uploading || file === null || !extensionAllowed}
        >
          {uploading ? t('dms.upload.submitting') : t('dms.upload.submit')}
        </button>
        <button
          type="button"
          className="button-save"
          disabled={uploading || file === null || !extensionAllowed || chosenFileSubClass === null}
          onClick={store}
        >
          {t('dms.upload.store')}
        </button>
      </span>
    </>
  )

  return (
    <>
      <ContentBox title={t('dms.upload.title')} footer={footer} language={{ value: language, onChange: setLanguage }}>
        <form id={FORM_ID} className="d-flex flex-column gap-3" onSubmit={submit}>
          <p className="mb-0 text-secondary small">{t('dms.upload.intro')}</p>
          <div>
            <span className="form-label">{t('dms.upload.file')}</span>
            <FileDropzone camera disabled={uploading} onFiles={(files) => chooseFile(files[0] ?? null)} />
            {file !== null && (
              <div className="small text-secondary mt-1">
                {file.name}, {t('dms.result.bytes', { count: file.size })}
              </div>
            )}
            {extension !== null && !extensionKnown && (
              <div className="small text-warning mt-1">{t('dms.upload.extensionUnknown', { extension })}</div>
            )}
            {extension !== null && !extensionAllowed && chosenFileSubClass !== null && (
              <div className="small text-danger mt-1">
                {t('dms.upload.extensionNotAllowed', { extension, key: chosenFileSubClass.key })}
              </div>
            )}
          </div>
          <FileSubClassPicker
            fileSubClasses={fileSubClasses ?? []}
            value={typeof chosenFileSubClassKey === 'string' ? chosenFileSubClassKey : null}
            onChange={(key) => setValue(FILE_SUB_CLASS_FIELD_NAME, key)}
            language={language}
            disabled={uploading}
            title={
              <Hoverlay text={translate(fileSubClassField?.description ?? null, language)}>
                <span className="table-header-hinted">{FILE_SUB_CLASS_FIELD_NAME}</span>
              </Hoverlay>
            }
          />
          <div>
            <span className="form-label">{t('dms.upload.fields')}</span>
            <Table
              columns={columns}
              rows={fields}
              rowKey={(solrField) => solrField.name}
              detailTitle={(solrField) => solrField.name}
              emptyText={loadErrorMessage ?? t('dms.upload.noFields')}
            />
          </div>
          <SolrHookFields hooks={hooks} onChange={setHooks} language={language} disabled={uploading} />
        </form>
      </ContentBox>
      {uploadResult !== null && (
        <ContentBox title={t('dms.result.uploadTitle')}>
          <dl className="details-list">
            <div>
              <dt>{t('dms.result.uploadStatus')}</dt>
              <dd>
                <strong>{uploadResult.status}</strong>: {t(`dms.result.uploadStatusText.${uploadResult.status}`)}
              </dd>
            </div>
            <div>
              <dt>id</dt>
              <dd>
                <code className="text-break">{uploadResult.id}</code>
              </dd>
            </div>
          </dl>
          {/* die Hooks, wie sie jetzt in Solr stehen: bei MERGED die vereinigten, über das Hook-Modul. Ohne Dokument darf der User es nicht lesen. */}
          {uploadResult.document !== null && (
            <SolrHookFields
              hooks={solrHookValuesOf(uploadResult.document, solrHooks ?? [])}
              language={language}
              title={t('dms.result.storedHooks')}
            />
          )}
        </ContentBox>
      )}
      {probe !== null && <DmsUploadProbeResult probe={probe} language={language} />}
    </>
  )
}
