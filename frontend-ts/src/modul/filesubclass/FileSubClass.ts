import { request } from '../../api/client.ts'
import type { PackSymbol } from '../../components/PackSymbol.ts'

/** Eine Dateiart (FSC), wie sie das Backend liefert (FileSubClass.java). Gelesen wird über die Stammdaten. */
export interface FileSubClass {
  /** z. B. RECHNUNG: nur A–Z, 0–9 und _, beginnt und endet mit einem Großbuchstaben */
  key: string
  /** mehrsprachiges JSON, z. B. {"de":"Rechnung"} */
  displayName: string
  /** mehrsprachiges JSON wie displayName */
  description: string | null
  listingPosition: number
  /** Symbol der Dateiart. Die Form drumherum kommt später von der Endung. */
  symbol: PackSymbol | null
  /** Farbe des Symbols, immer Hex. null: Standard */
  color: string | null
  /** wer Dateien dieser Art lesen darf. Enthält immer auch alle Schreibrollen, das setzt das Backend durch. */
  readAccessRoleKeys: string[] | null
  /** wer Dateien dieser Art ändern darf */
  writeAccessRoleKeys: string[] | null
  /** freigeschaltete Endungen, die pflegt die Freischalt-Matrix */
  extensions: string[] | null
}

/** Daten zum Anlegen und Ändern: alles außer den Endungen, die fasst das Speichern nie an. */
export type FileSubClassData = Omit<FileSubClass, 'extensions'>

// ===== Endpunkte des FileSubClassAdminController. Gelesen wird über die Stammdaten (useMasterData). =====

const FILE_SUB_CLASS_ADMIN_PATH = '/api/rest/v1/admin/filesubclass'

/** Neue Dateiart, noch ohne Endungen. Unbekannte AR-Keys lässt das Backend weg. */
export function createFileSubClass(newFileSubClassData: FileSubClassData) {
  return request<FileSubClass>('POST', FILE_SUB_CLASS_ADMIN_PATH, newFileSubClassData)
}

/** Alle Felder außer den Endungen, der key bestimmt die Dateiart und ändert sich nie. */
export function updateFileSubClass(changedFileSubClassData: FileSubClassData) {
  return request<FileSubClass>('PUT', FILE_SUB_CLASS_ADMIN_PATH, changedFileSubClassData)
}

/** Die vollständigen neuen Lese- und Schreibrollen einer Dateiart */
export interface FileSubClassAccessRoleKeys {
  key: string
  readAccessRoleKeys: string[]
  writeAccessRoleKeys: string[]
}

/**
 * Lese- und Schreibrollen mehrerer Dateiarten auf einmal (FSC-ACL-Matrix), in einer Transaktion. Schreiben schließt
 * Lesen ein. Zurück kommen alle Dateiarten.
 */
export function changeFileSubClassAccessRoleKeys(changedAccessRoleKeys: FileSubClassAccessRoleKeys[]) {
  return request<FileSubClass[]>('PUT', `${FILE_SUB_CLASS_ADMIN_PATH}/accessroles`, changedAccessRoleKeys)
}

/** Die vollständige neue Liste der freigeschalteten Endungen einer Dateiart. Leer: die Art nimmt nichts an. */
export interface FileSubClassExtensions {
  key: string
  extensions: string[]
}

/** Die freigeschalteten Endungen mehrerer Dateiarten auf einmal (FileMatrix), in einer Transaktion. Zurück kommen alle. */
export function changeFileSubClassExtensions(changedExtensions: FileSubClassExtensions[]) {
  return request<FileSubClass[]>('PUT', `${FILE_SUB_CLASS_ADMIN_PATH}/extensions`, changedExtensions)
}

/** Jeder key bekommt seine neue Position, z. B. { RECHNUNG: 1, DATENBLATT: 2 }. Zurück kommen alle in neuer Reihenfolge. */
export function changeFileSubClassPositions(newPositionByKey: Record<string, number>) {
  return request<FileSubClass[]>('POST', `${FILE_SUB_CLASS_ADMIN_PATH}/position`, newPositionByKey)
}

/** Löscht endgültig. Dateien dieser Art haben danach keinen Namen und kein Symbol mehr. */
export function deleteFileSubClass(key: string) {
  return request<FileSubClass>('DELETE', FILE_SUB_CLASS_ADMIN_PATH, { key })
}
