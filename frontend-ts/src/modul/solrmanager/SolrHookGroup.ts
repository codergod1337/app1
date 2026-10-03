import { request } from '../../api/client.ts'

/**
 * Eine Gruppe von Hooks, nur für die Anzeige (SolrHookGroup.java): Kern-Tab, Hook-Auswahl und später die Upload-Maske
 * zeigen die Hooks gruppenweise. Ein Hook zeigt mit hookGroupKey auf seine Gruppe. Gelesen wird über die Stammdaten.
 */
export interface SolrHookGroup {
  /** normale Key-Regel, z. B. ARTIKEL, ändert sich nie */
  key: string
  /** mehrsprachiges JSON, z. B. {"de":"Artikel","en":"Articles"} */
  displayName: string
  /** Reihenfolge der Gruppen in jeder Anzeige */
  listingPosition: number
}

// ===== Endpunkte des SolrHookGroupAdminController. Gelesen wird über die Stammdaten (useMasterData). =====

const SOLR_HOOK_GROUP_ADMIN_PATH = '/api/rest/v1/admin/solr/hookgroup'

/** Neue Gruppe */
export function createSolrHookGroup(newSolrHookGroupData: SolrHookGroup) {
  return request<SolrHookGroup>('POST', SOLR_HOOK_GROUP_ADMIN_PATH, newSolrHookGroupData)
}

/** Alle Felder, der key bestimmt die Gruppe und ändert sich nie. */
export function updateSolrHookGroup(changedSolrHookGroupData: SolrHookGroup) {
  return request<SolrHookGroup>('PUT', SOLR_HOOK_GROUP_ADMIN_PATH, changedSolrHookGroupData)
}

/** Jeder key bekommt seine neue Position, z. B. { ARTIKEL: 1, PARTNER: 2 }. Zurück kommen alle in neuer Reihenfolge. */
export function changeSolrHookGroupPositions(newPositionByKey: Record<string, number>) {
  return request<SolrHookGroup[]>('POST', `${SOLR_HOOK_GROUP_ADMIN_PATH}/position`, newPositionByKey)
}

/** Löscht die Gruppe. Solange noch ein Hook darin steht, lehnt das Backend ab (409). */
export function deleteSolrHookGroup(key: string) {
  return request<SolrHookGroup>('DELETE', SOLR_HOOK_GROUP_ADMIN_PATH, { key })
}
