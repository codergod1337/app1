/**
 * Welche Dateiendung welchen Viewer bekommt, die einzige Stelle dafür (nach der viewerRegistry der Vorlage). Steht eine
 * Endung nicht hier, gibt es für sie kein Auge: lieber gar nicht anbieten als enttäuschen. Bilder kann der Browser
 * selbst, PDF zeigt sein eingebautes Anzeigeprogramm (PdfFrame), Text roh, Word über docx-preview, Excel über SheetJS
 * (User 2026-10-14, die Formate der Vorlage). Die alten .doc und .xls und die OpenOffice-Formate kann keine dieser
 * Bibliotheken, sie bekommen nur den Download.
 */
import { fileExtensionOf } from './Dms.ts'

export type DmsViewerKind = 'image' | 'pdf' | 'text' | 'word' | 'spreadsheet'

const VIEWER_KIND_BY_FILE_EXTENSION: Readonly<Record<string, DmsViewerKind>> = {
  jpg: 'image',
  jpeg: 'image',
  png: 'image',
  gif: 'image',
  bmp: 'image',
  webp: 'image',
  pdf: 'pdf',
  txt: 'text',
  md: 'text',
  csv: 'text',
  log: 'text',
  docx: 'word',
  xlsx: 'spreadsheet',
}

/**
 * TEMP wird nie angezeigt (Vorlage, User 2026-10-14 übernommen): Das Auffangbecken nimmt jede Endung, also auch
 * Dateien, deren Inhalt niemand geprüft hat. Hoch- und Herunterladen ja, im Browser darstellen nein, egal welche Endung.
 * Erst mit der richtigen Dateiart gibt es den Viewer.
 */
const FILE_SUB_CLASS_KEY_WITHOUT_VIEWER = 'TEMP'

/** Der Viewer für diese Endung (klein, ohne Punkt), null wenn es keinen gibt */
export function dmsViewerKindOfFileExtension(fileExtension: unknown): DmsViewerKind | null {
  if (typeof fileExtension !== 'string') {
    return null
  }
  return VIEWER_KIND_BY_FILE_EXTENSION[fileExtension.toLowerCase()] ?? null
}

/**
 * Der Viewer für dieses Dokument: nach seiner Endung, fehlt das Feld (eine Liste ohne fileExtension), nach dem
 * Dateinamen; nie für TEMP. Jede Stelle fragt hier, Auge in Treffern und Listen wie die Viewer-Seite.
 */
export function dmsViewerKindOf(document: Record<string, unknown>): DmsViewerKind | null {
  if (document.fileSubClassKey === FILE_SUB_CLASS_KEY_WITHOUT_VIEWER) {
    return null
  }
  if (typeof document.fileExtension === 'string' && document.fileExtension !== '') {
    return dmsViewerKindOfFileExtension(document.fileExtension)
  }
  return typeof document.fileName === 'string' ? dmsViewerKindOfFileExtension(fileExtensionOf(document.fileName)) : null
}

/** Gibt es für dieses Dokument einen Viewer? Entscheidet, ob das Auge erscheint. */
export function hasDmsViewer(document: Record<string, unknown>): boolean {
  return dmsViewerKindOf(document) !== null
}
