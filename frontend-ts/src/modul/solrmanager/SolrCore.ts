import { request } from '../../api/client.ts'
import type { SolrLook } from './SolrLook.ts'

/**
 * Ein Solr-Kern, wie ihn das Backend liefert (SolrCore.java). Der key folgt der normalen Key-Regel und ist zugleich der
 * Name des Kerns in Solr. Kein Badge, sondern ein Emblem (SolrCoreEmblem), das Aussehen steckt im look. Gelesen wird
 * über die Stammdaten.
 */
export interface SolrCore {
  /** z. B. DMS: normale Key-Regel, zugleich der Name des Kerns in Solr, ändert sich nie. HOOKS und CORES sind reserviert */
  key: string
  /** mehrsprachiges JSON, z. B. {"de":"Dokumente"}, steht am Tab und im Emblem */
  displayName: string
  look: SolrLook
  /** Reihenfolge der Tabs */
  listingPosition: number
}

/**
 * Das Feld id: der Primärschlüssel (uniqueKey) jedes Kerns, vom Backend mit dem Kern angelegt, nie löschbar
 * (SOLR_ID_FIELD_FIXED_SETTINGS). Es gibt keine Wahl einer Quelle am Kern: Was hineinkommt, steht in der Beschreibung
 * des Feldes.
 */
export const SOLR_CORE_ID_FIELD_NAME = 'id'

/** Das Feld cursorDate: der Fortschrittszeiger eines Reindex-Laufs, wie id in jedem Kern vom Backend angelegt */
export const SOLR_CORE_CURSOR_DATE_FIELD_NAME = 'cursorDate'

/** Die Felder, die jeder Kern hat: vom Backend angelegt, nicht löschbar, Typ fest (bei id auch die PK-Schalter) */
export const SOLR_CORE_BUILT_IN_FIELD_NAMES: readonly string[] = [
  SOLR_CORE_ID_FIELD_NAME,
  SOLR_CORE_CURSOR_DATE_FIELD_NAME,
]

// ===== Endpunkte des SolrCoreAdminController. Gelesen wird über die Stammdaten (useMasterData). =====

const SOLR_CORE_ADMIN_PATH = '/api/rest/v1/admin/solr/core'

/** Neuer Kern. HOOKS und CORES sind als key reserviert, die lehnt das Backend ab. */
export function createSolrCore(newSolrCoreData: SolrCore) {
  return request<SolrCore>('POST', SOLR_CORE_ADMIN_PATH, newSolrCoreData)
}

/** Alle Felder, der key bestimmt den Kern und ändert sich nie. */
export function updateSolrCore(changedSolrCoreData: SolrCore) {
  return request<SolrCore>('PUT', SOLR_CORE_ADMIN_PATH, changedSolrCoreData)
}

/** Jeder key bekommt seine neue Position, z. B. { DMS: 1, ARTIKEL: 2 }. Zurück kommen alle in neuer Reihenfolge. */
export function changeSolrCorePositions(newPositionByKey: Record<string, number>) {
  return request<SolrCore[]>('POST', `${SOLR_CORE_ADMIN_PATH}/position`, newPositionByKey)
}

/** Löscht den Kern aus unserer Tabelle. Was in Solr passiert, entscheidet der Abgleich. */
export function deleteSolrCore(key: string) {
  return request<SolrCore>('DELETE', SOLR_CORE_ADMIN_PATH, { key })
}
