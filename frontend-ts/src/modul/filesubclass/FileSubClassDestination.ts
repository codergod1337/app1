import { request } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { translate } from '../../components/multilingual.ts'

/**
 * Ein Anzeigeort für Dateiarten (FileSubClassDestination.java, User 2026-10-13): eine Stelle in der Oberfläche, an der
 * Dateiarten als Abschnitt stehen und zum Hochladen angeboten werden, z. B. der Dokumente-Tab der Artikelmappe. Der
 * Admin legt sie an, Dateiart und Infosystem-Aufruf verweisen über displayContextKeys auf den key, der Code eines Ortes
 * nennt seinen key selbst. Gelesen wird über die Stammdaten.
 */
export interface FileSubClassDestination {
  /** normale Key-Regel, z. B. ARTIKELMAPPE_DOKUMENTE, ändert sich nie */
  key: string
  /** mehrsprachiges JSON, z. B. {"de":"Artikelmappe, Dokumente"} */
  displayName: string
  /** mehrsprachiges JSON wie displayName, optional */
  description: string | null
  /** Reihenfolge in jeder Anzeige */
  listingPosition: number
}

/** Der Anzeigename eines Ortes in der Sprache, sonst der key */
export function fileSubClassDestinationNameOf(destination: FileSubClassDestination, language: LanguageCode): string {
  return translate(destination.displayName, language) || destination.key
}

// ===== Endpunkte des FileSubClassDestinationAdminController. Gelesen wird über die Stammdaten (useMasterData). =====

const FILE_SUB_CLASS_DESTINATION_ADMIN_PATH = '/api/rest/v1/admin/filesubclass/destination'

/** Neuer Anzeigeort */
export function createFileSubClassDestination(newFileSubClassDestinationData: FileSubClassDestination) {
  return request<FileSubClassDestination>('POST', FILE_SUB_CLASS_DESTINATION_ADMIN_PATH, newFileSubClassDestinationData)
}

/** Alle Felder, der key bestimmt den Ort und ändert sich nie. */
export function updateFileSubClassDestination(changedFileSubClassDestinationData: FileSubClassDestination) {
  return request<FileSubClassDestination>('PUT', FILE_SUB_CLASS_DESTINATION_ADMIN_PATH, changedFileSubClassDestinationData)
}

/** Jeder key bekommt seine neue Position, z. B. { ARTIKELMAPPE_DOKUMENTE: 1, KUNDENMAPPE: 2 }. Zurück kommen alle in neuer Reihenfolge. */
export function changeFileSubClassDestinationPositions(newPositionByKey: Record<string, number>) {
  return request<FileSubClassDestination[]>('POST', `${FILE_SUB_CLASS_DESTINATION_ADMIN_PATH}/position`, newPositionByKey)
}

/** Löscht den Ort; 409, solange eine Dateiart oder ein Aufruf ihn noch trägt. */
export function deleteFileSubClassDestination(key: string) {
  return request<FileSubClassDestination>('DELETE', FILE_SUB_CLASS_DESTINATION_ADMIN_PATH, { key })
}
