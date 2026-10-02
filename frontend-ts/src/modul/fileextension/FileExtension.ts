import { request } from '../../api/client.ts'

/** Eine Endung im Katalog, wie sie das Backend liefert (FileExtension.java). Gelesen wird über die Stammdaten. */
export interface FileExtension {
  /** klein, ohne Punkt, z. B. pdf. Ändert sich nie. */
  extension: string
  /** mehrsprachiges JSON: wofür die Endung da ist */
  description: string | null
  /** key der Gruppe, z. B. BILDER. null: ohne Gruppe */
  fileExtensionCollectionKey: string | null
  /** heikel, z. B. html oder exe: beim Freischalten wird gewarnt */
  dangerous: boolean
  /** Reihenfolge innerhalb der Gruppe */
  listingPosition: number
  /** Text aus Bildern und Scans erkennen */
  ocr: boolean
  /** für die maschinelle Auswertung */
  ki: boolean
  /** ein Vorschaubild rechnen */
  imagePreview: boolean
}

// ===== Endpunkte des FileExtensionAdminController. Gelesen wird über die Stammdaten (useMasterData). =====

const FILE_EXTENSION_ADMIN_PATH = '/api/rest/v1/admin/fileextension'

export function createFileExtension(newFileExtensionData: FileExtension) {
  return request<FileExtension>('POST', FILE_EXTENSION_ADMIN_PATH, newFileExtensionData)
}

/** Alle Felder, die Endung bestimmt den Eintrag und ändert sich nie. */
export function updateFileExtension(changedFileExtensionData: FileExtension) {
  return request<FileExtension>('PUT', FILE_EXTENSION_ADMIN_PATH, changedFileExtensionData)
}

/** Jede Endung bekommt ihre neue Position, z. B. { pdf: 1, docx: 2 }. Zurück kommen alle in neuer Reihenfolge. */
export function changeFileExtensionPositions(newPositionByExtension: Record<string, number>) {
  return request<FileExtension[]>('POST', `${FILE_EXTENSION_ADMIN_PATH}/position`, newPositionByExtension)
}

/** Löscht die Endung und nimmt sie allen Dateiarten weg. */
export function deleteFileExtension(extension: string) {
  return request<FileExtension>('DELETE', FILE_EXTENSION_ADMIN_PATH, { extension })
}
