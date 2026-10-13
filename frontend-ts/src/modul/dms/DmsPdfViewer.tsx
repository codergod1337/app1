import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { LoadingProgress } from '../../components/LoadingProgress.tsx'
import { PdfFrame } from '../../components/PdfFrame.tsx'
import { downloadDmsFileBlob } from './Dms.ts'

/** Höchstens so oft wird der Fortschritt gezeichnet: Ein Stück je Netzwerkpaket wären tausende Renderläufe bei einem großen PDF */
const PROGRESS_REDRAW_INTERVAL_MS = 100

interface DmsPdfViewerProps {
  id: string
  fileName: string
}

interface DownloadedBytes {
  receivedBytes: number
  totalBytes: number | null
}

/**
 * Ein PDF aus dem DMS im Viewer: holt die Bytes über den Download-Weg mit der Leseprüfung des Users und zeigt sie im
 * quellenunabhängigen PdfFrame (User 2026-10-13: dasselbe Teil nutzt später die Anhangsanzeige der Mail-Archivierung,
 * nur mit Base64-Bytes statt des Downloads). Während des Ladens die eine Wartemeldung aller Viewer mit Fortschritt und
 * Restzeit. Beim Wechsel der id wird die alte Antwort verworfen.
 */
export function DmsPdfViewer({ id, fileName }: DmsPdfViewerProps) {
  const { t } = useTranslation()
  const [blob, setBlob] = useState<Blob | null>(null)
  const [downloaded, setDownloaded] = useState<DownloadedBytes>({ receivedBytes: 0, totalBytes: null })
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const lastRedrawRef = useRef(0)

  useEffect(() => {
    let cancelled = false
    setBlob(null)
    setErrorMessage(null)
    setDownloaded({ receivedBytes: 0, totalBytes: null })
    lastRedrawRef.current = 0
    downloadDmsFileBlob(id, (receivedBytes, totalBytes) => {
      const now = performance.now()
      if (cancelled || now - lastRedrawRef.current < PROGRESS_REDRAW_INTERVAL_MS) {
        return
      }
      lastRedrawRef.current = now
      setDownloaded({ receivedBytes, totalBytes })
    })
      .then(({ data }) => {
        if (!cancelled) {
          setBlob(data)
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        }
      })
    return () => {
      cancelled = true
    }
    // neu laden, wenn eine andere id kommt
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  if (errorMessage !== null) {
    return <p className="text-danger mb-0">{errorMessage}</p>
  }
  if (blob === null) {
    return <LoadingProgress fileName={fileName} receivedBytes={downloaded.receivedBytes} totalBytes={downloaded.totalBytes} />
  }
  return <PdfFrame blob={blob} title={fileName} />
}
