import { useMemo } from 'react'
import { useObjectUrl } from './useObjectUrl.ts'

/** Der MIME-Typ, den der Browser braucht, um ein PDF zu zeigen statt es zum Speichern anzubieten */
const PDF_MIME_TYPE = 'application/pdf'

interface PdfFrameProps {
  /** die Bytes des PDF, egal woher: Download aus dem DMS, Base64 eines Mail-Anhangs, eine Datei aus der Dropzone */
  blob: Blob
  /** der Name für Screenreader und den Tab-Titel des eingebetteten Viewers */
  title: string
}

/**
 * Ein PDF im eingebauten Anzeigeprogramm des Browsers (nach dem PdfViewer der Vorlage): kein pdf.js, keine Bibliothek,
 * der Blob hängt als Object-URL in einem iframe. Die Komponente kennt keine Quelle (User 2026-10-13): Wer die Bytes
 * hat, gibt den Blob, so zeigt der Viewer des DMS und später die Anhangsanzeige der Mail-Archivierung dasselbe.
 *
 * Der Typ muss application/pdf sein, sonst bietet der Browser die Datei zum Speichern an statt sie zu zeigen; ein Blob
 * mit anderem oder leerem Typ (Absender setzen den Typ eines Anhangs oft falsch) wird deshalb umgetypt, die Bytes
 * bleiben. Die Fläche gibt der Aufrufer, das iframe füllt sie (.pdf-frame).
 */
export function PdfFrame({ blob, title }: PdfFrameProps) {
  const pdfBlob = useMemo(() => (blob.type === PDF_MIME_TYPE ? blob : new Blob([blob], { type: PDF_MIME_TYPE })), [blob])
  const objectUrl = useObjectUrl(pdfBlob)
  if (objectUrl === null) {
    return null
  }
  return <iframe className="pdf-frame" src={objectUrl} title={title} />
}
