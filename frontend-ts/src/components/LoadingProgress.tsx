import type { TFunction } from 'i18next'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { readableBytes } from './readableBytes.ts'

/** So lange nach dem Start ist jede Rate Unsinn, bis dahin keine Restzeit */
const MIN_ELAPSED_MS_FOR_ESTIMATE = 500

interface LoadingProgressProps {
  /** was geladen wird, z. B. der Dateiname; null ohne Namen */
  fileName: string | null
  receivedBytes: number
  /** null ohne Content-Length, dann bleibt es beim Spinner mit Namen */
  totalBytes: number | null
}

/**
 * Was gerade lädt und wie weit, die eine Wartemeldung aller Viewer (nach ViewerLaden der Vorlage): der Name, geladene
 * von gesamten Bytes mit Prozent, dazu die geschätzte Restzeit aus der mittleren Rate seit dem Start. Ein Mittel, keine
 * Momentanrate: Die spränge bei schwankender Leitung zwischen 2 und 40 Sekunden hin und her. Ein Takt je Sekunde lässt
 * die Restzeit auch dann weiterlaufen, wenn gerade keine Bytes ankommen, sonst sähe eine Stockung aus wie eingefroren.
 * Ohne Gesamtgröße lässt sich nichts hochrechnen, eine erfundene Zahl wäre schlimmer als keine.
 */
export function LoadingProgress({ fileName, receivedBytes, totalBytes }: LoadingProgressProps) {
  const { t } = useTranslation()
  const startedAt = useRef(performance.now())
  const [, tick] = useState(0)

  useEffect(() => {
    const timer = setInterval(() => tick((count) => count + 1), 1000)
    return () => clearInterval(timer)
  }, [])

  const percent = totalBytes !== null && totalBytes > 0 ? Math.min(100, Math.round((receivedBytes / totalBytes) * 100)) : null
  const remainingSeconds = remainingSecondsOf(receivedBytes, totalBytes, performance.now() - startedAt.current)

  return (
    <span className="loading-progress">
      <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
      <span className="loading-progress-text">
        <span>
          {t('common.loading')}
          {fileName !== null && fileName !== '' ? ` ${fileName}` : ''} …
        </span>
        {percent !== null && (
          <span className="small text-muted">
            {t('common.loadingProgress.received', {
              received: readableBytes(receivedBytes),
              total: readableBytes(totalBytes),
              percent,
            })}
            {remainingSeconds !== null &&
              ` · ${t('common.loadingProgress.remaining', { duration: readableDuration(remainingSeconds, t) })}`}
          </span>
        )}
      </span>
    </span>
  )
}

/** Restzeit in Sekunden aus der mittleren Rate; null, solange sich nichts sinnvoll hochrechnen lässt */
function remainingSecondsOf(receivedBytes: number, totalBytes: number | null, elapsedMs: number): number | null {
  if (totalBytes === null || totalBytes <= 0 || receivedBytes <= 0 || elapsedMs < MIN_ELAPSED_MS_FOR_ESTIMATE) {
    return null
  }
  const bytesPerSecond = receivedBytes / (elapsedMs / 1000)
  if (bytesPerSecond <= 0) {
    return null
  }
  return Math.max(0, Math.round((totalBytes - receivedBytes) / bytesPerSecond))
}

/** Sekunden lesbar: unter einer Minute reichen Sekunden */
function readableDuration(seconds: number, t: TFunction): string {
  if (seconds < 60) {
    return t('common.loadingProgress.seconds', { count: seconds })
  }
  return t('common.loadingProgress.minutesSeconds', { minutes: Math.floor(seconds / 60), seconds: seconds % 60 })
}
