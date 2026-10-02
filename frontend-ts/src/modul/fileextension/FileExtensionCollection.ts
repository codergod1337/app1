import { request } from '../../api/client.ts'

/** Die Form, in der eine Datei gezeichnet wird (SymbolShape.java). Darin sitzt das Symbol der Dateiart. */
export type SymbolShape = 'CIRCLE' | 'SQUARE' | 'TRIANGLE' | 'DIAMOND' | 'PENTAGON' | 'HEXAGON' | 'HEPTAGON' | 'OCTAGON'

export const SYMBOL_SHAPES: { shape: SymbolShape; label: string }[] = [
  { shape: 'CIRCLE', label: 'Kreis' },
  { shape: 'SQUARE', label: 'Quadrat' },
  { shape: 'TRIANGLE', label: 'Dreieck' },
  { shape: 'DIAMOND', label: 'Raute' },
  { shape: 'PENTAGON', label: 'Fünfeck' },
  { shape: 'HEXAGON', label: 'Sechseck' },
  { shape: 'HEPTAGON', label: 'Siebeneck' },
  { shape: 'OCTAGON', label: 'Achteck' },
]

/** Eine Gruppe von Endungen, wie sie das Backend liefert (FileExtensionCollection.java). */
export interface FileExtensionCollection {
  /** z. B. BILDER: nur A–Z, 0–9 und _, von Hand vergeben, ändert sich nie. Die Endungen verweisen darüber hierher. */
  key: string
  /** mehrsprachiges JSON, z. B. {"de":"Bilder"} */
  displayName: string
  /** null: Standardform */
  symbolShape: SymbolShape | null
  listingPosition: number
}

// ===== Endpunkte des FileExtensionCollectionAdminController. Gelesen wird über die Stammdaten (useMasterData). =====

const FILE_EXTENSION_COLLECTION_ADMIN_PATH = '/api/rest/v1/admin/fileextensioncollection'

/** Den key prüft das Backend: 400 bei ungültigem, 409 bei vergebenem. */
export function createFileExtensionCollection(newFileExtensionCollectionData: FileExtensionCollection) {
  return request<FileExtensionCollection>('POST', FILE_EXTENSION_COLLECTION_ADMIN_PATH, newFileExtensionCollectionData)
}

/** Alle Felder, der key bestimmt die Gruppe und ändert sich nie. */
export function updateFileExtensionCollection(changedFileExtensionCollectionData: FileExtensionCollection) {
  return request<FileExtensionCollection>('PUT', FILE_EXTENSION_COLLECTION_ADMIN_PATH, changedFileExtensionCollectionData)
}

/** Jeder key bekommt seine neue Position, z. B. { BILDER: 1, CAD: 2 }. Zurück kommen alle in neuer Reihenfolge. */
export function changeFileExtensionCollectionPositions(newPositionByKey: Record<string, number>) {
  return request<FileExtensionCollection[]>('POST', `${FILE_EXTENSION_COLLECTION_ADMIN_PATH}/position`, newPositionByKey)
}

/** Löscht die Gruppe, ihre Endungen stehen danach ohne Gruppe. */
export function deleteFileExtensionCollection(key: string) {
  return request<FileExtensionCollection>('DELETE', FILE_EXTENSION_COLLECTION_ADMIN_PATH, { key })
}
