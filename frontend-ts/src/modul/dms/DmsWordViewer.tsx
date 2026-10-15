import { renderAsync } from 'docx-preview'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { LoadingProgress } from '../../components/LoadingProgress.tsx'
import { useDmsFileBlob } from './Dms.ts'

interface DmsWordViewerProps {
  id: string
  fileName: string
}

/**
 * Word im Viewer (nach dem WordViewer der Vorlage), nur docx: docx-preview rendert das Dokument seitenweise als HTML in
 * den Container, Seiten weiß auf grauem Grund wie in Word. Reine Anzeige, kein Bearbeiten; wer die Datei braucht, nimmt
 * den Download. Das alte .doc kann keine Bibliothek im Browser. Die Bytes kommen nur in den Speicher (useDmsFileBlob).
 */
export function DmsWordViewer({ id, fileName }: DmsWordViewerProps) {
  const { t } = useTranslation()
  const { blob, receivedBytes, totalBytes, errorMessage } = useDmsFileBlob(id)
  const containerRef = useRef<HTMLDivElement>(null)
  /** zu welchen Bytes das Rendern fertig ist, mit Fehler oder ohne */
  const [rendered, setRendered] = useState<{ blob: Blob; errorMessage: string | null } | null>(null)

  useEffect(() => {
    const container = containerRef.current
    if (blob === null || container === null) {
      return
    }
    let active = true
    container.innerHTML = ''
    renderAsync(blob, container)
      .then(() => {
        if (active) {
          setRendered({ blob, errorMessage: null })
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setRendered({ blob, errorMessage: error instanceof Error && error.message ? error.message : t('dms.viewer.renderFailed') })
        }
      })
    return () => {
      active = false
      container.innerHTML = ''
    }
    // t ändert sich nicht während des Renderns
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [blob])

  const current = rendered !== null && rendered.blob === blob ? rendered : null
  return (
    <div className="viewer-document" aria-label={fileName}>
      {errorMessage !== null && <p className="text-danger mb-0">{errorMessage}</p>}
      {current !== null && current.errorMessage !== null && <p className="text-danger mb-0">{current.errorMessage}</p>}
      {errorMessage === null && current === null && <LoadingProgress fileName={fileName} receivedBytes={receivedBytes} totalBytes={totalBytes} />}
      <div ref={containerRef} />
    </div>
  )
}
