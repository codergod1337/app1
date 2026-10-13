/**
 * Welche Dateiendung welchen Viewer bekommt, die einzige Stelle dafür (nach der viewerRegistry der Vorlage). Steht eine
 * Endung nicht hier, gibt es für sie kein Auge: lieber gar nicht anbieten als enttäuschen. Bilder kann der Browser
 * selbst, PDF zeigt sein eingebautes Anzeigeprogramm (PdfFrame), alles andere kommt später (Video, Text, Office).
 */
export type DmsViewerKind = 'image' | 'pdf'

const VIEWER_KIND_BY_FILE_EXTENSION: Readonly<Record<string, DmsViewerKind>> = {
  jpg: 'image',
  jpeg: 'image',
  png: 'image',
  gif: 'image',
  bmp: 'image',
  webp: 'image',
  pdf: 'pdf',
}

/** Der Viewer für diese Endung (klein, ohne Punkt), null wenn es keinen gibt */
export function dmsViewerKindOfFileExtension(fileExtension: unknown): DmsViewerKind | null {
  if (typeof fileExtension !== 'string') {
    return null
  }
  return VIEWER_KIND_BY_FILE_EXTENSION[fileExtension.toLowerCase()] ?? null
}

/** Gibt es für dieses Dokument einen Viewer? Entscheidet, ob das Auge erscheint. */
export function hasDmsViewer(document: Record<string, unknown>): boolean {
  return dmsViewerKindOfFileExtension(document.fileExtension) !== null
}
