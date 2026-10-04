import { request } from '../../api/client.ts'

/**
 * Ein Status für alle Entities, die einen brauchen (EntityState.java), z. B. die Dokumente des DMS. scope sagt, wo er
 * gilt, null heißt überall: Eine Maske bietet nur die Status ihres Bereichs an. Gelesen wird über die Stammdaten.
 */
export interface EntityState {
  /** normale Key-Regel, z. B. ACTIVE, ändert sich nie. In den Dokumenten steht dieser key. */
  key: string
  /** mehrsprachiges JSON, z. B. {"de":"aktiv","en":"active"} */
  displayName: string
  /** wo der Status gilt, normale Key-Regel, z. B. DMS. null: überall */
  scope: string | null
  /** Reihenfolge in jeder Anzeige */
  listingPosition: number
}

/** Die Status eines Bereichs in Listenreihenfolge: die mit diesem scope und die ohne, denn die gelten überall */
export function entityStatesOfScope(entityStates: readonly EntityState[], scope: string): EntityState[] {
  return entityStates.filter((entityState) => entityState.scope === null || entityState.scope === scope)
}

// ===== Endpunkte des EntityStateAdminController. Gelesen wird über die Stammdaten (useMasterData). =====

const ENTITY_STATE_ADMIN_PATH = '/api/rest/v1/admin/entitystate'

/** Neuer Status */
export function createEntityState(newEntityStateData: EntityState) {
  return request<EntityState>('POST', ENTITY_STATE_ADMIN_PATH, newEntityStateData)
}

/** Alle Felder, der key bestimmt den Status und ändert sich nie. */
export function updateEntityState(changedEntityStateData: EntityState) {
  return request<EntityState>('PUT', ENTITY_STATE_ADMIN_PATH, changedEntityStateData)
}

/** Jeder key bekommt seine neue Position, z. B. { ACTIVE: 1, ARCHIVED: 2 }. Zurück kommen alle in neuer Reihenfolge. */
export function changeEntityStatePositions(newPositionByKey: Record<string, number>) {
  return request<EntityState[]>('POST', `${ENTITY_STATE_ADMIN_PATH}/position`, newPositionByKey)
}

/** Löscht den Status. Dokumente, die ihn tragen, behalten den key ohne Anzeigenamen. */
export function deleteEntityState(key: string) {
  return request<EntityState>('DELETE', ENTITY_STATE_ADMIN_PATH, { key })
}
