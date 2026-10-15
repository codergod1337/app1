import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { CancelButton } from '../../components/CancelButton.tsx'
import { ConfirmButton } from '../../components/ConfirmButton.tsx'
import { DownloadButton } from '../../components/DownloadButton.tsx'
import { showToast } from '../../components/toastStore.ts'
import {
  cancelSolrReindex,
  getSolrReindexStatus,
  SOLR_REINDEX_SEARCH_SUGGESTION_REBUILD,
  SOLR_REINDEX_STATUS_POLL_MS,
  solrReindexChangedIdsUrl,
  solrReindexOptionsOf,
  startSolrReindex,
  type SolrReindexOption,
  type SolrReindexStatus,
} from './SolrReindex.ts'

/** 75 s, 12 min 5 s, 2 h 3 min */
function durationText(seconds: number): string {
  const whole = Math.round(seconds)
  if (whole < 60) {
    return `${whole} s`
  }
  if (whole < 3600) {
    return `${Math.floor(whole / 60)} min ${whole % 60} s`
  }
  return `${Math.floor(whole / 3600)} h ${Math.floor((whole % 3600) / 60)} min`
}

/**
 * Das Nachziehen eines Kerns im SolrManager (User 2026-10-14), im Popup aus dem Kern-Tab: die Prüfungen als Haken,
 * Starten, der Fortschritt, Abbrechen und am Ende die geänderten ids. Der Lauf selbst läuft im Backend über alle
 * Dokumente, schreibt nur abweichende und das einzeln; das Popup darf man schließen und wieder öffnen. Ein Lauf zur
 * Zeit über alle Kerne: Läuft gerade einer in einem anderen Kern, steht das hier und Starten ist gesperrt.
 */
export function SolrReindexBox({ coreKey }: { coreKey: string }) {
  const { t, i18n } = useTranslation()
  const availableOptions = solrReindexOptionsOf(coreKey)
  const [options, setOptions] = useState<SolrReindexOption[]>(availableOptions.slice(0, 1))
  const [status, setStatus] = useState<SolrReindexStatus | null>(null)
  const [statusErrorMessage, setStatusErrorMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  /** neu abfragen: nach dem Start und nach dem Abbrechen */
  const [pollCount, setPollCount] = useState(0)

  useEffect(() => {
    let active = true
    let timer: number | undefined
    function poll() {
      getSolrReindexStatus()
        .then((result) => {
          if (!active) {
            return
          }
          setStatus(result.data ?? null)
          setStatusErrorMessage(null)
          if (result.data?.running) {
            timer = window.setTimeout(poll, SOLR_REINDEX_STATUS_POLL_MS)
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

  const number = (value: number, fractionDigits = 0) => value.toLocaleString(i18n.language, { maximumFractionDigits: fractionDigits })
  const running = status?.running === true
  const otherCoreRunning = running && status?.coreKey !== coreKey
  /** der Stand dieses Kerns; der eines anderen Kerns steht nur als Hinweis da */
  const own = status !== null && status.coreKey === coreKey ? status : null

  /** Der Neuaufbau der Suche läuft nur allein: Ihn anhaken nimmt die Prüfungen weg und umgekehrt */
  function toggleOption(option: SolrReindexOption, checked: boolean) {
    setOptions((previous) => {
      if (!checked) {
        return previous.filter((entry) => entry !== option)
      }
      if (option === SOLR_REINDEX_SEARCH_SUGGESTION_REBUILD) {
        return [option]
      }
      return [...previous.filter((entry) => entry !== SOLR_REINDEX_SEARCH_SUGGESTION_REBUILD), option]
    })
  }

  function start() {
    setBusy(true)
    startSolrReindex(coreKey, options)
      .then((result) => {
        setStatus(result.data)
        setPollCount((count) => count + 1)
      })
      .catch((error: unknown) => {
        showToast({ kind: 'error', title: t('solrmanager.reindex.startFailed'), text: error instanceof RequestError ? error.message : undefined })
      })
      .finally(() => setBusy(false))
  }

  function cancel() {
    cancelSolrReindex()
      .then((result) => setStatus(result.data ?? null))
      .catch((error: unknown) => {
        showToast({ kind: 'error', title: t('solrmanager.reindex.cancelFailed'), text: error instanceof RequestError ? error.message : undefined })
      })
  }

  let statusLine = null
  if (statusErrorMessage !== null) {
    statusLine = <span className="text-danger">{statusErrorMessage}</span>
  } else if (otherCoreRunning) {
    statusLine = <span className="text-warning">{t('solrmanager.reindex.otherCoreRunning', { core: status?.coreKey })}</span>
  } else if (own === null) {
    statusLine = <span className="text-body-secondary">{t('solrmanager.reindex.status.never')}</span>
  } else if (own.running) {
    statusLine = (
      <span>
        <span className="spinner-border spinner-border-sm me-2" role="status" aria-label={t('common.loading')} />
        {own.cancelRequested ? t('solrmanager.reindex.status.cancelling') : t('solrmanager.reindex.status.running')}
      </span>
    )
  } else if (own.errorMessage !== null) {
    statusLine = <span className="text-danger">{t('solrmanager.reindex.status.failed', { message: own.errorMessage })}</span>
  } else {
    statusLine = <span>{own.cancelRequested ? t('solrmanager.reindex.status.cancelled') : t('solrmanager.reindex.status.finished')}</span>
  }

  const processed = own !== null ? own.checked + own.failed : 0
  const percent = own !== null && own.total > 0 ? Math.min(100, (processed / own.total) * 100) : own?.finishedAt ? 100 : 0
  const seconds =
    own !== null ? Math.max(0, ((own.finishedAt !== null ? new Date(own.finishedAt).getTime() : Date.now()) - new Date(own.startedAt).getTime()) / 1000) : 0

  return (
    <div className="d-flex flex-column gap-3">
      <p className="small text-body-secondary mb-0">{t('solrmanager.reindex.intro')}</p>

      <div className="d-flex flex-column gap-1">
        {availableOptions.map((option) => (
          <label key={option} className="form-check mb-0">
            <input
              className="form-check-input"
              type="checkbox"
              checked={options.includes(option)}
              disabled={running}
              onChange={(event) => toggleOption(option, event.target.checked)}
            />
            <span className="form-check-label">{t(`solrmanager.reindex.options.${option}`)}</span>
          </label>
        ))}
      </div>

      <div className="d-flex align-items-center gap-2 flex-wrap">
        <ConfirmButton
          label={busy ? t('solrmanager.reindex.starting') : t('solrmanager.reindex.start')}
          onClick={start}
          disabled={running || options.length === 0}
          busy={busy}
        />
        {own !== null && own.running && (
          <CancelButton label={t('solrmanager.reindex.cancel')} disabled={own.cancelRequested} onClick={cancel} />
        )}
        <span className="ms-auto">{statusLine}</span>
      </div>

      {own !== null && (
        <div className="d-flex flex-column gap-2">
          <div className="small">
            {t('solrmanager.reindex.progress', { done: number(processed), total: own.total < 0 ? '…' : number(own.total) })}
            {' · '}
            {t('solrmanager.reindex.changed', { count: own.changed, value: number(own.changed) })}
            {own.failed > 0 && (
              <>
                {' · '}
                <span className="text-danger">{t('solrmanager.reindex.failed', { value: number(own.failed) })}</span>
              </>
            )}
            {' · '}
            {t('solrmanager.reindex.rate', { value: number(own.documentsPerSecond, 1) })}
            {' · '}
            {durationText(seconds)}
          </div>
          <div className="progress" role="progressbar" aria-valuenow={Math.round(percent)} aria-valuemin={0} aria-valuemax={100} style={{ height: '0.5rem' }}>
            <div className="progress-bar" style={{ width: `${percent}%` }} />
          </div>
          {own.lastErrorMessage !== null && (
            <div className="small text-danger">{t('solrmanager.reindex.lastError', { message: own.lastErrorMessage })}</div>
          )}
          {!own.changedIdsRecorded ? (
            <span className="small text-body-secondary">{t('solrmanager.reindex.changedIdsNotRecorded')}</span>
          ) : own.firstChangedIds.length > 0 ? (
            <div className="d-flex flex-column gap-1">
              <div className="d-flex align-items-center gap-2">
                <span className="small fw-semibold">{t('solrmanager.reindex.firstChangedIds', { count: own.firstChangedIds.length })}</span>
                {!own.running && <DownloadButton label={t('solrmanager.reindex.download')} href={solrReindexChangedIdsUrl()} />}
              </div>
              <pre className="small mb-0" style={{ maxHeight: '12rem', overflow: 'auto' }}>
                {own.firstChangedIds.join('\n')}
              </pre>
            </div>
          ) : (
            !own.running && <span className="small text-body-secondary">{t('solrmanager.reindex.changedNone')}</span>
          )}
        </div>
      )}
    </div>
  )
}
