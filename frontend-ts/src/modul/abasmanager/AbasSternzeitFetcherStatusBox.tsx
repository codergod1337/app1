import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { showToast } from '../../components/toastStore.ts'
import {
  type AbasSternzeitFetcherStatus,
  getAbasSternzeitFetcherStatus,
  switchAbasSternzeitFetcher,
} from './AbasSternzeit.ts'

/** So oft fragt die Box nach dem Zustand */
const POLL_MS = 5000

/**
 * Die große Badge der Übersicht (User 2026-10-10): ob die Sternzeiten in der Queue geholt und bearbeitet werden. Grün
 * LÄUFT, rot PAUSIERT, grau NICHT EINGERICHTET, darunter Worker, Queue und die Zähler seit dem Start, daneben der
 * Schalter. Fragt alle fünf Sekunden nach, nie mit zwei Anfragen gleichzeitig.
 */
export function AbasSternzeitFetcherStatusBox() {
  const { t, i18n } = useTranslation()
  const [status, setStatus] = useState<AbasSternzeitFetcherStatus | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [switching, setSwitching] = useState(false)
  /** eine Anfrage läuft gerade: der nächste Takt wartet, statt eine zweite loszuschicken */
  const inFlight = useRef(false)

  function load() {
    if (inFlight.current) {
      return
    }
    inFlight.current = true
    getAbasSternzeitFetcherStatus()
      .then((result) => {
        setStatus(result.data)
        setErrorMessage(null)
      })
      .catch((error: unknown) =>
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError')),
      )
      .finally(() => {
        inFlight.current = false
      })
  }

  useEffect(() => {
    load()
    const interval = setInterval(load, POLL_MS)
    return () => clearInterval(interval)
    // beim Betreten, dann im Takt
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function switchTo(running: boolean) {
    setSwitching(true)
    switchAbasSternzeitFetcher(running)
      .then((result) => setStatus(result.data))
      .catch((error: unknown) =>
        showToast({
          kind: 'error',
          title: t('abasmanager.fetcher.switchFailed'),
          text: error instanceof RequestError ? error.message : t('common.unknownError'),
        }),
      )
      .finally(() => setSwitching(false))
  }

  let badgeClass = 'badge fs-3 text-bg-secondary'
  let stateLabel = ''
  let hint = ''
  if (status?.state === 'RUNNING') {
    badgeClass = 'badge fs-3 text-bg-success'
    stateLabel = t('abasmanager.fetcher.state.running')
    hint = t('abasmanager.fetcher.hint.running')
  } else if (status?.state === 'PAUSED') {
    badgeClass = 'badge fs-3 text-bg-danger'
    stateLabel = t('abasmanager.fetcher.state.paused')
    hint = t('abasmanager.fetcher.hint.paused')
  } else if (status?.state === 'NOT_CONFIGURED') {
    stateLabel = t('abasmanager.fetcher.state.notConfigured')
    hint = t('abasmanager.fetcher.hint.notConfigured')
  }

  const actions = status === null || status.state === 'NOT_CONFIGURED' ? null : status.state === 'RUNNING' ? (
    <button type="button" className="button-other" disabled={switching} onClick={() => switchTo(false)}>
      {t('abasmanager.fetcher.pause')}
    </button>
  ) : (
    <button type="button" className="button-save" disabled={switching} onClick={() => switchTo(true)}>
      {t('abasmanager.fetcher.resume')}
    </button>
  )

  let footer
  if (errorMessage !== null) {
    footer = <span className="text-danger">{errorMessage}</span>
  } else {
    footer = <span>{t('abasmanager.fetcher.footer')}</span>
  }

  return (
    <ContentBox title={t('abasmanager.fetcher.title')} actions={actions} footer={footer}>
      {status === null && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
      )}
      {status !== null && (
        <div className="d-flex flex-wrap align-items-center gap-3">
          <span className={badgeClass}>{stateLabel}</span>
          <div className="d-flex flex-column">
            <span>{hint}</span>
            <span>
              {t('abasmanager.fetcher.workers', { busy: status.busyWorkers, parallel: status.parallel })}
              {', '}
              {t('abasmanager.fetcher.queue', { count: status.queueCount })}
              {status.queueCountEstimated && <> ({t('abasmanager.fetcher.estimated')})</>}
            </span>
            <span className="text-body-secondary">
              {t('abasmanager.fetcher.sinceStart', {
                processed: status.processedCount.toLocaleString(i18n.language),
                failed: status.failedCount.toLocaleString(i18n.language),
              })}
            </span>
          </div>
        </div>
      )}
    </ContentBox>
  )
}
