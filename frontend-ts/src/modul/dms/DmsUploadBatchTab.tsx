import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { FileDropzone } from '../../components/FileDropzone.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { MultilingualInput } from '../../components/MultilingualInput.tsx'
import { fileSha256Hex } from '../../components/sha256Hex.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { showToast, updateToast } from '../../components/toastStore.ts'
import { FileSubClassPicker } from '../filesubclass/FileSubClassPicker.tsx'
import { FileSubClassSymbol } from '../filesubclass/FileSubClassSymbol.tsx'
import { SolrHookFields, type SolrHookValuesByKey } from '../solrmanager/SolrHookFields.tsx'
import {
  DOCUMENT_LANGUAGE_FIELD_NAME,
  FILE_SUB_CLASS_FIELD_NAME,
  getDmsExistence,
  uploadDmsFile,
  type DmsExistence,
  type DmsUploadStatus,
} from './Dms.ts'
import { documentLanguageAppliesToFileExtension } from './DmsDocumentLanguage.ts'
import { DmsDocumentLanguagePicker } from './DmsDocumentLanguagePicker.tsx'
import { fileExtensionOf } from './DmsFields.ts'

/** So viele Dateien nimmt ein Stapel: eine Mappe ist kein Massenimport */
const MAX_FILES = 25
/** So lange bleibt eine fertige Zeile grün stehen, dann räumt sie sich weg */
const SUCCESS_VISIBLE_MS = 5000
/** Die Felder je Datei im Dokument */
const FILE_INDEX_FIELD_NAME = 'fileIndex'
const FILE_DESCRIPTION_FIELD_NAME = 'fileDescription'
const IS_PUBLIC_FIELD_NAME = 'isPublic'

/** Was die Vorprüfung über eine Datei weiß */
interface BatchEntry {
  /** die Nummer ist der Schlüssel, nicht die Position: Zeilen verschwinden, während andere noch prüfen */
  nr: number
  file: File
  extension: string
  fileIndex: string
  /** mehrsprachiges JSON wie das Feld, null solange leer */
  fileDescription: string | null
  /** die Sprache, nur gefragt, wenn die Endungsgruppe eine vorsieht; null: keine gewählt, bei Dokumenten erkennt sie das Backend */
  documentLanguage: string | null
  /** 'pending' solange der Browser rechnet, 'failed' wenn er es nicht kann, sonst der Hash */
  hash: 'pending' | 'failed' | string
  /** 'pending' bis das Backend geantwortet hat, 'failed' ohne Antwort, sonst die Antwort */
  existence: 'pending' | 'failed' | DmsExistence
  /** gesetzt, sobald der Upload durch ist */
  result: DmsUploadStatus | null
  uploadError: string | null
}

/**
 * Der Zustand einer Zeile, aus den Fakten der Vorprüfung und der gewählten Dateiart:
 * checking prüft noch, new geht als neues Dokument raus, merge ergänzt das vorhandene (gleiche Dateiart), exists liegt
 * unter einer anderen Dateiart, deleted ist im DMS weggeräumt, extension nimmt die Dateiart nicht an, error konnte
 * nicht geprüft werden und geht trotzdem raus, das Backend entscheidet.
 */
type EntryStatus = 'checking' | 'new' | 'merge' | 'exists' | 'deleted' | 'extension' | 'error'

/** Diese gehen nicht mit raus */
const BLOCKED: readonly EntryStatus[] = ['checking', 'exists', 'deleted', 'extension']

/** Pfad, Größe und Zeitstempel: dieselbe Datei, ohne sie zu lesen */
function fileKey(file: File): string {
  return `${file.webkitRelativePath || file.name}|${file.size}|${file.lastModified}`
}

/**
 * Die Stapelmaske des DMS, nach der UserUploadMaske der Vorlage: bis 25 Dateien, für alle zusammen die Dateiart, die
 * Hooks und die übrigen Stapelfelder, je Datei nur Index und Beschreibung. Beim Ablegen prüft jede Datei für sich:
 * Endung gegen die Dateiart, SHA-256 im Browser, dann die Frage ans Backend, ob die Bytes schon im DMS liegen.
 * Dieselbe Datei kommt nicht zweimal in den Stapel, dieselben Bytes unter anderem Namen auch nicht.
 *
 * Hochgeladen wird eine Datei nach der anderen, je ein Request. Ein Fehlschlag beendet den Stapel nicht: Die Zeile
 * bekommt ihren Grund und bleibt, derselbe Knopf schickt sie erneut. Fertige Zeilen werden grün, zeigen einen
 * ablaufenden Balken und räumen sich nach fünf Sekunden weg. Die Maske bleibt stehen, Dateiart und Hooks bleiben gesetzt:
 * Wer aus einer Mappe kommt, lädt selten nur einen Rutsch. Ein Toast läuft mit und wird am Ende zum Ergebnis.
 */
export function DmsUploadBatchTab() {
  const { t } = useTranslation()
  const { fileSubClasses, fileExtensions, fileExtensionCollections } = useMasterData()
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)
  const [fileSubClassKey, setFileSubClassKey] = useState<string | null>(null)
  const [hooks, setHooks] = useState<SolrHookValuesByKey>({})
  const [isPublic, setIsPublic] = useState(false)
  const [entries, setEntries] = useState<BatchEntry[]>([])
  const [uploading, setUploading] = useState(false)
  const [progress, setProgress] = useState<{ number: number; total: number; name: string; percent: number } | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const nextNr = useRef(0)
  /** nr → Schlüssel und Hash der Zeilen im Stapel, sofort beantwortbar, auch während andere noch rechnen */
  const occupied = useRef(new Map<number, { key: string; hash: string | null }>())
  const timers = useRef<number[]>([])
  useEffect(() => () => timers.current.forEach((timer) => window.clearTimeout(timer)), [])

  const fileSubClass = (fileSubClasses ?? []).find((candidate) => candidate.key === fileSubClassKey) ?? null

  function statusOf(entry: BatchEntry): EntryStatus {
    if (fileSubClass !== null && !(fileSubClass.extensions ?? []).includes(entry.extension)) {
      return 'extension'
    }
    if (entry.hash === 'pending' || entry.existence === 'pending') {
      return 'checking'
    }
    if (entry.hash === 'failed' || entry.existence === 'failed') {
      return 'error'
    }
    if (!entry.existence.exists) {
      return 'new'
    }
    if (entry.existence.status === 'DELETED') {
      return 'deleted'
    }
    return entry.existence.fileSubClassKey === fileSubClassKey ? 'merge' : 'exists'
  }

  /** Hat die Datei dieser Zeile eine Sprache? Sagt ihre Endungsgruppe, dieselbe Regel wie im Backend */
  function documentLanguageApplies(entry: BatchEntry): boolean {
    return documentLanguageAppliesToFileExtension(entry.extension, fileExtensions ?? [], fileExtensionCollections ?? [])
  }

  function changeEntry(nr: number, patch: Partial<BatchEntry>) {
    setEntries((current) => current.map((entry) => (entry.nr === nr ? { ...entry, ...patch } : entry)))
  }

  function removeEntry(nr: number) {
    occupied.current.delete(nr)
    setEntries((current) => current.filter((entry) => entry.nr !== nr))
  }

  /** Belegt eine andere Zeile diesen Wert schon? */
  function occupiedByOther(nr: number | null, test: (slot: { key: string; hash: string | null }) => boolean): boolean {
    for (const [holder, slot] of occupied.current) {
      if (holder !== nr && test(slot)) {
        return true
      }
    }
    return false
  }

  /** Beim Ablegen: Doppelte raus, höchstens MAX_FILES, dann je Datei die Prüfung anstoßen */
  function addFiles(files: File[]) {
    const seen = new Set<string>()
    const fresh: File[] = []
    for (const file of files) {
      const key = fileKey(file)
      if (seen.has(key) || occupiedByOther(null, (slot) => slot.key === key)) {
        continue
      }
      seen.add(key)
      fresh.push(file)
    }
    const room = Math.max(0, MAX_FILES - entries.length)
    const added = fresh.slice(0, room)
    if (added.length === 0) {
      return
    }
    setErrorMessage(null)
    const newEntries: BatchEntry[] = added.map((file) => {
      nextNr.current += 1
      occupied.current.set(nextNr.current, { key: fileKey(file), hash: null })
      return {
        nr: nextNr.current,
        file,
        extension: fileExtensionOf(file.name),
        fileIndex: '',
        fileDescription: null,
        documentLanguage: null,
        hash: 'pending',
        existence: 'pending',
        result: null,
        uploadError: null,
      }
    })
    setEntries((current) => [...current, ...newEntries])
    newEntries.forEach((entry) => void checkEntry(entry.nr, entry.file))
  }

  /** Hash im Browser, dann die Frage ans Backend. Dieselben Bytes wie eine andere Zeile: diese verschwindet still. */
  async function checkEntry(nr: number, file: File) {
    let hash: string | null
    try {
      hash = await fileSha256Hex(file)
    } catch {
      hash = null
    }
    if (!occupied.current.has(nr)) {
      return // inzwischen entfernt
    }
    if (hash === null) {
      changeEntry(nr, { hash: 'failed', existence: 'failed' })
      return
    }
    if (occupiedByOther(nr, (slot) => slot.hash === hash)) {
      removeEntry(nr)
      return
    }
    occupied.current.get(nr)!.hash = hash
    changeEntry(nr, { hash })
    try {
      const existence = (await getDmsExistence(hash)).data
      changeEntry(nr, { existence })
    } catch {
      changeEntry(nr, { existence: 'failed' })
    }
  }

  const uploadable = entries.filter((entry) => entry.result === null && !BLOCKED.includes(statusOf(entry)))

  /** Eine Datei nach der anderen. Ein Fehlschlag kennzeichnet nur seine Zeile, die übrigen gehen trotzdem raus. */
  async function uploadAll() {
    if (fileSubClass === null || uploadable.length === 0 || uploading) {
      return
    }
    const batch = uploadable
    setUploading(true)
    setErrorMessage(null)
    const toastId = showToast({
      kind: 'upload',
      title: t('dms.batch.toastRunning'),
      text: t('dms.batch.progress', { number: 1, total: batch.length, name: batch[0].file.name, percent: 0 }),
      sticky: true,
      progress: 0,
    })
    let done = 0
    let failed = 0
    try {
      for (let index = 0; index < batch.length; index++) {
        const entry = batch[index]
        const report = (percent: number) => {
          const text = t('dms.batch.progress', { number: index + 1, total: batch.length, name: entry.file.name, percent })
          setProgress({ number: index + 1, total: batch.length, name: entry.file.name, percent })
          updateToast(toastId, { text, progress: Math.round(((index + percent / 100) / batch.length) * 100) })
        }
        report(0)
        const document: Record<string, unknown> = {
          [FILE_SUB_CLASS_FIELD_NAME]: fileSubClass.key,
          [IS_PUBLIC_FIELD_NAME]: isPublic,
        }
        if (entry.fileIndex.trim() !== '') {
          document[FILE_INDEX_FIELD_NAME] = entry.fileIndex.trim()
        }
        if (entry.fileDescription !== null) {
          document[FILE_DESCRIPTION_FIELD_NAME] = entry.fileDescription
        }
        if (entry.documentLanguage !== null && documentLanguageApplies(entry)) {
          document[DOCUMENT_LANGUAGE_FIELD_NAME] = entry.documentLanguage
        }
        for (const [key, values] of Object.entries(hooks)) {
          if (values.length > 0) {
            document[key] = [...values]
          }
        }
        try {
          const result = await uploadDmsFile(entry.file, document, (sent, total) =>
            report(total > 0 ? Math.round((sent / total) * 100) : 0),
          )
          changeEntry(entry.nr, { result: result.data.status, uploadError: null })
          if (result.data.status === 'EXISTS') {
            failed += 1
          } else {
            done += 1
            timers.current.push(window.setTimeout(() => removeEntry(entry.nr), SUCCESS_VISIBLE_MS))
          }
        } catch (error: unknown) {
          failed += 1
          changeEntry(entry.nr, {
            uploadError: error instanceof RequestError ? error.message : t('common.unknownError'),
          })
        }
      }
    } finally {
      setUploading(false)
      setProgress(null)
      updateToast(toastId, {
        kind: failed > 0 ? 'error' : 'success',
        title: t('dms.batch.toastDone'),
        text: t('dms.batch.toastSummary', { done, failed }),
        sticky: false,
        progress: null,
      })
    }
  }

  function statusCell(entry: BatchEntry): ReactNode {
    if (entry.uploadError !== null) {
      return (
        <span className="text-danger">
          <Icon name="error" /> {t('dms.batch.state.failed')}
        </span>
      )
    }
    if (entry.result !== null) {
      if (entry.result === 'EXISTS') {
        return (
          <span className="text-danger">
            <Icon name="error" /> {t('dms.batch.state.exists')}
          </span>
        )
      }
      return (
        <span className="text-success">
          <Icon name="check" /> {t(`dms.batch.done.${entry.result}`)}
          <span className="dms-upload-countdown" aria-hidden="true">
            <span className="dms-upload-countdown-bar" style={{ animationDuration: `${SUCCESS_VISIBLE_MS}ms` }} />
          </span>
        </span>
      )
    }
    const status = statusOf(entry)
    switch (status) {
      case 'checking':
        return (
          <span className="text-secondary">
            <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />{' '}
            {t('dms.batch.state.checking')}
          </span>
        )
      case 'new':
        return (
          <span className="text-success">
            <Icon name="check" /> {t('dms.batch.state.new')}
          </span>
        )
      case 'merge':
        return (
          <span>
            <Icon name="info" /> {t('dms.batch.state.merge')}
          </span>
        )
      case 'error':
        return (
          <span className="text-warning">
            <Icon name="warning" /> {t('dms.batch.state.error')}
          </span>
        )
      case 'exists':
      case 'deleted':
      case 'extension':
        return (
          <span className="text-danger">
            <Icon name="error" /> {t(`dms.batch.state.${status}`)}
          </span>
        )
    }
  }

  /** Der Grund, warum eine Zeile nicht mitgeht oder scheiterte, als Satz unter dem Dateinamen */
  function reason(entry: BatchEntry): string | null {
    if (entry.uploadError !== null) {
      return entry.uploadError
    }
    if (entry.result === 'EXISTS') {
      return t('dms.batch.reason.exists', { key: existingKey(entry) })
    }
    switch (statusOf(entry)) {
      case 'exists':
        return t('dms.batch.reason.exists', { key: existingKey(entry) })
      case 'deleted':
        return t('dms.batch.reason.deleted', { id: typeof entry.hash === 'string' ? entry.hash : '' })
      case 'extension':
        return t('dms.upload.extensionNotAllowed', { extension: entry.extension || '-', key: fileSubClass?.key ?? '' })
      case 'error':
        return t('dms.batch.reason.error')
      default:
        return null
    }
  }

  function existingKey(entry: BatchEntry): string {
    return typeof entry.existence === 'object' ? (entry.existence.fileSubClassKey ?? '') : ''
  }

  const columns: TableColumn<BatchEntry>[] = [
    { header: t('dms.batch.columns.state'), width: '11rem', cell: statusCell },
    {
      header: t('dms.batch.columns.fileSubClass'),
      width: '4rem',
      unimportant: true,
      cell: () => (fileSubClass === null ? '–' : <FileSubClassSymbol fileSubClass={fileSubClass} />),
    },
    {
      header: t('dms.batch.columns.file'),
      cell: (entry) => (
        <>
          <span className="text-break">{entry.file.name}</span>{' '}
          <span className="text-secondary small">({t('dms.result.bytes', { count: entry.file.size })})</span>
          {reason(entry) !== null && <div className="small text-danger text-break">{reason(entry)}</div>}
        </>
      ),
    },
    {
      header: t('dms.batch.columns.index'),
      width: '9rem',
      cell: (entry) => (
        <input
          className="form-control form-control-sm"
          maxLength={12}
          placeholder={t('dms.batch.indexPlaceholder')}
          value={entry.fileIndex}
          disabled={uploading || entry.result !== null || statusOf(entry) === 'merge'}
          onChange={(event) => changeEntry(entry.nr, { fileIndex: event.target.value })}
        />
      ),
    },
    {
      header: t('dms.batch.columns.language'),
      width: '4.5rem',
      cell: (entry) =>
        documentLanguageApplies(entry) ? (
          <DmsDocumentLanguagePicker
            compact
            value={entry.documentLanguage}
            disabled={uploading || entry.result !== null || statusOf(entry) === 'merge'}
            onChange={(documentLanguage) => changeEntry(entry.nr, { documentLanguage })}
          />
        ) : (
          <span className="text-muted">—</span>
        ),
    },
    {
      header: t('dms.batch.columns.description'),
      width: '18rem',
      cell: (entry) => (
        <MultilingualInput
          id={`dms-batch-description-${entry.nr}`}
          language={language}
          value={entry.fileDescription}
          disabled={uploading || entry.result !== null}
          onChange={(fileDescription) => changeEntry(entry.nr, { fileDescription })}
        />
      ),
    },
  ]

  let footer: ReactNode
  if (errorMessage !== null) {
    footer = <span className="text-danger">{errorMessage}</span>
  } else if (progress !== null) {
    footer = <span>{t('dms.batch.progress', progress)}</span>
  } else {
    footer = <span>{t('dms.batch.count', { count: entries.length, max: MAX_FILES })}</span>
  }
  const uploadButton = (
    <button
      type="button"
      className="button-save"
      disabled={uploading || fileSubClass === null || uploadable.length === 0}
      onClick={() => void uploadAll()}
    >
      {uploading ? t('dms.batch.uploading') : t('dms.batch.upload', { count: uploadable.length })}
    </button>
  )

  return (
    <ContentBox
      title={t('dms.batch.title')}
      footer={
        <>
          {footer}
          <span className="ms-auto d-flex gap-2">{uploadButton}</span>
        </>
      }
      language={{ value: language, onChange: setLanguage }}
    >
      <div className="d-flex flex-column gap-3">
        <p className="mb-0 text-secondary small">{t('dms.batch.intro', { max: MAX_FILES })}</p>

        <FileSubClassPicker
          fileSubClasses={fileSubClasses ?? []}
          value={fileSubClassKey}
          onChange={setFileSubClassKey}
          language={language}
          disabled={uploading}
          required
        />

        <SolrHookFields hooks={hooks} onChange={setHooks} language={language} disabled={uploading} />

        <div className="form-check">
          <input
            id="dms-batch-public"
            type="checkbox"
            className="form-check-input"
            checked={isPublic}
            disabled={uploading}
            onChange={(event) => setIsPublic(event.target.checked)}
          />
          <label className="form-check-label" htmlFor="dms-batch-public">
            {t('dms.batch.isPublic')}
          </label>
        </div>

        <FileDropzone
          multiple
          camera
          disabled={uploading || entries.length >= MAX_FILES}
          text={t('dms.batch.dropzone', { max: MAX_FILES })}
          onFiles={addFiles}
        />

        <Table
          columns={columns}
          rows={entries}
          rowKey={(entry) => String(entry.nr)}
          detailTitle={(entry) => entry.file.name}
          emptyText={t('dms.batch.empty')}
          rowClassName={(entry) =>
            entry.uploadError !== null || entry.result === 'EXISTS'
              ? 'dms-upload-row-failed'
              : entry.result !== null
                ? 'dms-upload-row-done'
                : undefined
          }
          actions={(entry) =>
            entry.result === null || entry.result === 'EXISTS' ? (
              <DeleteButton
                label={t('dms.batch.remove', { name: entry.file.name })}
                disabled={uploading}
                onClick={() => removeEntry(entry.nr)}
              />
            ) : null
          }
        />
      </div>
    </ContentBox>
  )
}
