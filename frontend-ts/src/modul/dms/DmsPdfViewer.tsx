import { LoadingProgress } from '../../components/LoadingProgress.tsx'
import { PdfFrame } from '../../components/PdfFrame.tsx'
import { useDmsFileBlob } from './Dms.ts'

interface DmsPdfViewerProps {
  id: string
  fileName: string
}

/**
 * Ein PDF aus dem DMS im Viewer: holt die Bytes über den Download-Weg mit der Leseprüfung des Users (useDmsFileBlob,
 * nur in den Speicher) und zeigt sie im quellenunabhängigen PdfFrame (User 2026-10-13: dasselbe Teil nutzt später die
 * Anhangsanzeige der Mail-Archivierung, nur mit Base64-Bytes statt des Downloads). Während des Ladens die eine
 * Wartemeldung aller Viewer mit Fortschritt und Restzeit.
 */
export function DmsPdfViewer({ id, fileName }: DmsPdfViewerProps) {
  const { blob, receivedBytes, totalBytes, errorMessage } = useDmsFileBlob(id)
  if (errorMessage !== null) {
    return <p className="text-danger mb-0">{errorMessage}</p>
  }
  if (blob === null) {
    return <LoadingProgress fileName={fileName} receivedBytes={receivedBytes} totalBytes={totalBytes} />
  }
  return <PdfFrame blob={blob} title={fileName} />
}
