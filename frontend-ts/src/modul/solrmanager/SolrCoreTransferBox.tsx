import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { CancelButton } from '../../components/CancelButton.tsx'
import { ConfirmButton } from '../../components/ConfirmButton.tsx'
import { Icon } from '../../components/Icon.tsx'
import { readableBytes } from '../../components/readableBytes.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { showToast } from '../../components/toastStore.ts'
import {
  analyzeSolrCoreImport,
  cancelSolrCoreImport,
  getSolrCoreImportStatus,
  prepareSolrCoreExport,
  SOLR_CORE_IMPORT_STATUS_POLL_MS,
  solrCoreExportDownloadUrl,
  startSolrCoreImport,
  type SolrCoreImportAnalysis,
  type SolrCoreImportFileField,
  type SolrCoreImportMode,
  type SolrCoreImportStartRequest,
  type SolrCoreImportStatus,
} from './SolrCoreTransfer.ts'

/**
 * Die letzte Prüfung und der letzte Start, solange die Seite offen ist: Wer das Popup schließt und wieder öffnet, sieht
 * die Zuordnung noch und kann nach einem Halt fortsetzen, ohne die Datei neu hochzuladen.
 */
let rememberedAnalysis: SolrCoreImportAnalysis | null = null
let rememberedStartRequest: SolrCoreImportStartRequest | null = null

const IMPORT_MODES: readonly SolrCoreImportMode[] = ['SKIP_EXISTING', 'REPLACE']

/**
 * Export und Import eines Kerns im SolrManager (User 2026-10-14), im Popup aus dem Kern-Tab, nur ADMIN.
 *
 * Export: ein Knopf, das Backend gibt eine Nummer, der Browser lädt die gepackte Datei über einen normalen Link direkt
 * auf die Platte.
 *
 * Import in drei Schritten: Datei wählen und prüfen (hochladen mit Fortschritt, dann zählt die Prüfung die Dokumente);
 * die Analyse zeigt je Feld der Datei, was der Kern damit macht, und je fehlendem Kernfeld eine Auswahl, welches
 * unbekannte Feld der Datei einspringt; ein essenzielles Feld muss eine Quelle haben, sonst startet nichts. Unbekannte
 * Felder ohne Zuordnung werden ignoriert, das steht da. Dann Modus und Importieren; der Stand läuft mit, beim ersten
 * Fehler hält der Import an, und „ab Zeile … fortsetzen“ macht dort mit derselben Zuordnung weiter.
 */
export function SolrCoreTransferBox({ coreKey }: { coreKey: string }) {
  const { t, i18n } = useTranslation()
  const number = (value: number, fractionDigits = 0) => value.toLocaleString(i18n.language, { maximumFractionDigits: fractionDigits })

  const [exporting, setExporting] = useState(false)
  const [file, setFile] = useState<File | null>(null)
  const [analyzing, setAnalyzing] = useState(false)
  const [upload, setUpload] = useState<{ sent: number; total: number } | null>(null)
  const [readDocuments, setReadDocuments] = useState<number | null>(null)
  const [analysis, setAnalysis] = useState<SolrCoreImportAnalysis | null>(rememberedAnalysis?.coreKey === coreKey ? rememberedAnalysis : null)
  /** je fehlendem Kernfeld das Feld der Datei, das einspringt */
  const [sourceByMissing, setSourceByMissing] = useState<Record<string, string>>({})
  const [mode, setMode] = useState<SolrCoreImportMode>('SKIP_EXISTING')
  const [status, setStatus] = useState<SolrCoreImportStatus | null>(null)
  const [statusErrorMessage, setStatusErrorMessage] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  /** neu abfragen: nach dem Start und nach dem Anhalten */
  const [pollCount, setPollCount] = useState(0)

  useEffect(() => {
    let active = true
    let timer: number | undefined
    function poll() {
      getSolrCoreImportStatus()
        .then((result) => {
          if (!active) {
            return
          }
          setStatus(result.data ?? null)
          setStatusErrorMessage(null)
          if (result.data?.running) {
            timer = window.setTimeout(poll, SOLR_CORE_IMPORT_STATUS_POLL_MS)
          }
        })
        .catch((error: unknown) => {
          if (active) {
            setStatusErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
          }
        })
    }
    poll()
    return () => {
      active = false
      window.clearTimeout(timer)
    }
  }, [pollCount, t])

  const running = status?.running === true
  const own = status !== null && status.coreKey === coreKey ? status : null

  // ===== Export =====

  function startExport() {
    setExporting(true)
    prepareSolrCoreExport(coreKey)
      .then((result) => {
        // ein normaler Link: Der Browser schreibt die Datei beim Laden auf die Platte, nicht in den Speicher
        const link = document.createElement('a')
        link.href = solrCoreExportDownloadUrl(result.data.exportId)
        link.download = ''
        document.body.appendChild(link)
        link.click()
        link.remove()
      })
      .catch((error: unknown) => {
        showToast({ kind: 'error', title: t('solrmanager.transfer.export.failed'), text: error instanceof RequestError ? error.message : undefined })
      })
      .finally(() => setExporting(false))
  }

  // ===== Import: prüfen =====

  function analyze() {
    if (file === null) {
      return
    }
    setAnalyzing(true)
    setAnalysis(null)
    setReadDocuments(null)
    setUpload(null)
    analyzeSolrCoreImport(
      coreKey,
      file,
      (documents) => setReadDocuments(documents),
      (sent, total) => setUpload({ sent, total }),
    )
      .then((result) => {
        rememberedAnalysis = result
        rememberedStartRequest = null
        setAnalysis(result)
        setSourceByMissing({})
      })
      .catch((error: unknown) => {
        showToast({ kind: 'error', title: t('solrmanager.transfer.import.analyzeFailed'), text: error instanceof Error ? error.message : undefined })
      })
      .finally(() => {
        setAnalyzing(false)
        setUpload(null)
      })
  }

  /** die Felder der Datei, die der Kern nicht kennt: nur sie können für ein fehlendes Kernfeld einspringen */
  const unknownFileFields = (analysis?.fileFields ?? []).filter((field) => !field.skipped && field.coreFieldType === null)
  const usedSources = new Set(Object.values(sourceByMissing).filter((source) => source !== ''))
  const ignoredFileFields = unknownFileFields.filter((field) => !usedSources.has(field.name)).map((field) => field.name)
  const essentialWithoutSource = (analysis?.missingCoreFields ?? []).filter(
    (field) => field.essential && (sourceByMissing[field.name] ?? '') === '',
  )

  function startRequestOf(fromLine: number): SolrCoreImportStartRequest | null {
    if (analysis === null) {
      return null
    }
    const mapping: Record<string, string> = {}
    for (const [target, source] of Object.entries(sourceByMissing)) {
      if (source !== '') {
        mapping[source] = target
      }
    }
    return { importId: analysis.importId, mode, mapping, ignored: ignoredFileFields, fromLine }
  }

  function start(startRequest: SolrCoreImportStartRequest | null) {
    if (startRequest === null) {
      return
    }
    setStarting(true)
    startSolrCoreImport(startRequest)
      .then((result) => {
        rememberedStartRequest = startRequest
        setStatus(result.data)
        setPollCount((count) => count + 1)
      })
      .catch((error: unknown) => {
        showToast({ kind: 'error', title: t('solrmanager.transfer.import.startFailed'), text: error instanceof RequestError ? error.message : undefined })
      })
      .finally(() => setStarting(false))
  }

  function cancel() {
    cancelSolrCoreImport()
      .then((result) => setStatus(result.data ?? null))
      .catch((error: unknown) => {
        showToast({ kind: 'error', title: t('solrmanager.transfer.status.cancelFailed'), text: error instanceof RequestError ? error.message : undefined })
      })
  }

  const fileFieldColumns: TableColumn<SolrCoreImportFileField>[] = [
    { header: t('solrmanager.transfer.fileFields.name'), cell: (field) => <code>{field.name}</code>, width: '14rem' },
    { header: t('solrmanager.transfer.fileFields.count'), cell: (field) => number(field.count), width: '7rem' },
    {
      header: t('solrmanager.transfer.fileFields.core'),
      cell: (field) => {
        if (field.skipped) {
          return <span className="text-body-secondary">{t('solrmanager.transfer.fileFields.skipped')}</span>
        }
        if (field.coreFieldType === null) {
          return (
            <span className={usedSources.has(field.name) ? '' : 'text-warning'}>
              {usedSources.has(field.name) ? t('solrmanager.transfer.fileFields.mapped') : t('solrmanager.transfer.fileFields.unknown')}
            </span>
          )
        }
        return (
          <span>
            {field.coreFieldType.toLowerCase()}
            {field.essential && <span className="badge text-bg-warning ms-1">{t('solrmanager.transfer.essential')}</span>}
          </span>
        )
      },
      width: '11rem',
    },
    {
      header: t('solrmanager.transfer.fileFields.invalid'),
      cell: (field) =>
        field.invalidValues > 0 ? (
          <span className="text-danger" title={field.firstInvalidValue ?? undefined}>
            {number(field.invalidValues)}
          </span>
        ) : (
          '0'
        ),
      width: '7rem',
      unimportant: true,
    },
    { header: t('solrmanager.transfer.fileFields.sample'), cell: (field) => <span className="small text-break">{field.sampleValue}</span>, unimportant: true },
  ]

  let statusLine = null
  if (statusErrorMessage !== null) {
    statusLine = <span className="text-danger">{statusErrorMessage}</span>
  } else if (running && own === null) {
    statusLine = <span className="text-warning">{t('solrmanager.transfer.status.otherCoreRunning', { core: status?.coreKey })}</span>
  } else if (own === null) {
    statusLine = <span className="text-body-secondary">{t('solrmanager.transfer.status.never')}</span>
  } else if (own.running) {
    statusLine = (
      <span>
        <span className="spinner-border spinner-border-sm me-2" role="status" aria-label={t('common.loading')} />
        {own.cancelRequested ? t('solrmanager.transfer.status.cancelling') : t('solrmanager.transfer.status.running')}
      </span>
    )
  } else if (own.stoppedAtLine !== null) {
    statusLine = (
      <span className={own.errorMessage !== null ? 'text-danger' : 'text-warning'}>
        {t('solrmanager.transfer.status.stopped', { line: own.stoppedAtLine })}
        {own.stoppedAtId !== null && ` (id ${own.stoppedAtId})`}
        {own.errorMessage !== null && `: ${own.errorMessage}`}
      </span>
    )
  } else if (own.errorMessage !== null) {
    statusLine = <span className="text-danger">{t('solrmanager.transfer.status.failed', { message: own.errorMessage })}</span>
  } else {
    statusLine = <span>{t('solrmanager.transfer.status.finished')}</span>
  }

  // fortsetzen geht mit der Zuordnung des letzten Starts, solange die Datei dieselbe ist
  const resumeRequest =
    own !== null && !own.running && own.stoppedAtLine !== null && rememberedStartRequest?.importId === own.importId
      ? { ...rememberedStartRequest, fromLine: own.stoppedAtLine }
      : null

  return (
    <div className="d-flex flex-column gap-4">
      <section className="d-flex flex-column gap-2">
        <h3 className="h6 mb-0">{t('solrmanager.transfer.export.title')}</h3>
        <p className="small text-body-secondary mb-0">{t('solrmanager.transfer.export.intro')}</p>
        <div>
          <button type="button" className="button-other" disabled={exporting} onClick={startExport}>
            <Icon name="download" /> {t('solrmanager.transfer.export.start')}
          </button>
        </div>
      </section>

      <section className="d-flex flex-column gap-2">
        <h3 className="h6 mb-0">{t('solrmanager.transfer.import.title')}</h3>
        <p className="small text-body-secondary mb-0">{t('solrmanager.transfer.import.intro')}</p>
        <div className="d-flex align-items-center gap-2 flex-wrap">
          <input
            className="form-control form-control-sm"
            style={{ maxWidth: '28rem' }}
            type="file"
            accept=".gz,.ndjson,.jsonl,.json"
            aria-label={t('solrmanager.transfer.import.file')}
            disabled={analyzing || running}
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          />
          <button type="button" className="button-other" disabled={file === null || analyzing || running} onClick={analyze}>
            <Icon name="check" /> {analyzing ? t('solrmanager.transfer.import.analyzing') : t('solrmanager.transfer.import.analyze')}
          </button>
          {upload !== null && readDocuments === null && (
            <span className="small">{t('solrmanager.transfer.import.uploading', { sent: readableBytes(upload.sent), total: readableBytes(upload.total) })}</span>
          )}
          {analyzing && readDocuments !== null && <span className="small">{t('solrmanager.transfer.import.read', { value: number(readDocuments) })}</span>}
        </div>

        {analysis !== null && (
          <div className="d-flex flex-column gap-3">
            <div className="small d-flex flex-column gap-1">
              <span>
                <strong>{analysis.fileName}</strong>: {t('solrmanager.transfer.analysis.documents', { value: number(analysis.documents) })}
              </span>
              {analysis.complete ? (
                <span className="text-success">{t('solrmanager.transfer.analysis.complete')}</span>
              ) : (
                <span className="text-warning">
                  {t('solrmanager.transfer.analysis.incomplete')}
                  {analysis.declaredDocuments !== null && ` ${t('solrmanager.transfer.analysis.declared', { value: number(analysis.declaredDocuments) })}`}
                </span>
              )}
              {analysis.unreadableDocuments > 0 && (
                <span className="text-danger">
                  {t('solrmanager.transfer.analysis.unreadable', { value: number(analysis.unreadableDocuments), first: analysis.firstUnreadable ?? '' })}
                </span>
              )}
            </div>

            <div className="d-flex flex-column gap-1">
              <strong className="small">{t('solrmanager.transfer.fileFields.title')}</strong>
              <Table
                columns={fileFieldColumns}
                rows={analysis.fileFields}
                rowKey={(field) => field.name}
                detailTitle={(field) => field.name}
                emptyText={t('solrmanager.transfer.fileFields.none')}
              />
            </div>

            <div className="d-flex flex-column gap-2">
              <strong className="small">{t('solrmanager.transfer.missing.title')}</strong>
              {analysis.missingCoreFields.length === 0 && <span className="small text-body-secondary">{t('solrmanager.transfer.missing.none')}</span>}
              {analysis.missingCoreFields.map((missing) => {
                const chosen = sourceByMissing[missing.name] ?? ''
                return (
                  <div key={missing.name} className="d-flex align-items-center gap-2 flex-wrap">
                    <code style={{ minWidth: '14rem' }}>{missing.name}</code>
                    <span className="small text-body-secondary">{missing.type.toLowerCase()}</span>
                    {missing.essential && <span className="badge text-bg-warning">{t('solrmanager.transfer.essential')}</span>}
                    <select
                      className={`form-select form-select-sm${missing.essential && chosen === '' ? ' is-invalid' : ''}`}
                      style={{ maxWidth: '18rem' }}
                      aria-label={t('solrmanager.transfer.missing.from', { name: missing.name })}
                      value={chosen}
                      disabled={running}
                      onChange={(event) => setSourceByMissing({ ...sourceByMissing, [missing.name]: event.target.value })}
                    >
                      <option value="">{missing.essential ? t('solrmanager.transfer.missing.choose') : t('solrmanager.transfer.missing.leaveEmpty')}</option>
                      {unknownFileFields
                        .filter((field) => field.name === chosen || !usedSources.has(field.name))
                        .map((field) => (
                          <option key={field.name} value={field.name}>
                            {field.name}
                          </option>
                        ))}
                    </select>
                  </div>
                )
              })}
              {ignoredFileFields.length > 0 && (
                <span className="small text-warning">{t('solrmanager.transfer.ignored', { names: ignoredFileFields.join(', ') })}</span>
              )}
            </div>

            <div className="d-flex align-items-center gap-3 flex-wrap">
              <span className="small fw-semibold">{t('solrmanager.transfer.mode.title')}</span>
              {IMPORT_MODES.map((candidate) => (
                <label key={candidate} className="form-check mb-0">
                  <input
                    className="form-check-input"
                    type="radio"
                    name={`solr-core-import-mode-${coreKey}`}
                    checked={mode === candidate}
                    disabled={running}
                    onChange={() => setMode(candidate)}
                  />
                  <span className="form-check-label">{t(`solrmanager.transfer.mode.${candidate}`)}</span>
                </label>
              ))}
            </div>

            <div className="d-flex align-items-center gap-2 flex-wrap">
              <ConfirmButton
                label={t('solrmanager.transfer.import.start')}
                onClick={() => start(startRequestOf(1))}
                disabled={running || essentialWithoutSource.length > 0}
                busy={starting}
              />
              {essentialWithoutSource.length > 0 && (
                <span className="small text-danger">
                  {t('solrmanager.transfer.import.essentialMissing', { names: essentialWithoutSource.map((field) => field.name).join(', ') })}
                </span>
              )}
            </div>
          </div>
        )}
      </section>

      <section className="d-flex flex-column gap-2">
        <h3 className="h6 mb-0">{t('solrmanager.transfer.status.title')}</h3>
        <div className="d-flex align-items-center gap-2 flex-wrap">
          {statusLine}
          {own !== null && own.running && (
            <CancelButton label={t('solrmanager.transfer.status.cancel')} disabled={own.cancelRequested} onClick={cancel} />
          )}
          {resumeRequest !== null && (
            <ConfirmButton
              label={t('solrmanager.transfer.status.resume', { line: resumeRequest.fromLine })}
              onClick={() => start(resumeRequest)}
              busy={starting}
            />
          )}
        </div>
        {own !== null && (
          <div className="small">
            {t('solrmanager.transfer.status.progress', {
              line: number(own.line),
              total: number(own.totalDocuments),
              written: number(own.written),
              skipped: number(own.skippedExisting),
              rate: number(own.documentsPerSecond, 1),
            })}
          </div>
        )}
      </section>
    </div>
  )
}
