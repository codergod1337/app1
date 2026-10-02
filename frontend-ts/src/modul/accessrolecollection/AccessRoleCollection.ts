import { request } from '../../api/client.ts'
import type { AccessRoleBadgeData } from '../accessrole/AccessRole.ts'

/** Pflicht-Präfix jedes ARC-keys, gleich wie im Backend */
export const ARC_KEY_PREFIX = 'ARC_'

/**
 * Eine AccessRoleCollection (ARC), wie sie das Backend liefert (AccessRoleCollection.java): eine Position, die mehrere
 * AccessRoles bündelt. Badge wie bei der AccessRole, aber in Pillenform.
 */
export interface AccessRoleCollection extends AccessRoleBadgeData {
  /** z. B. ARC_BUCHHALTUNG */
  key: string
  /** die AR, die diese ARC verleiht. Gesetzt nur über die ARC-AR-Matrix. */
  accessRoleKeys: string[] | null
  /** die ARCs, die diese ARC überwacht und in Vertretung bearbeitet. Nicht rekursiv, nie sie selbst. */
  slaveArcKeys: string[] | null
  listingPosition: number
}

/** Daten zum Anlegen und Ändern: alles außer den AR, die setzt nur die ARC-AR-Matrix. Pflicht sind key und displayName. */
export type NewAccessRoleCollectionData = Omit<AccessRoleCollection, 'accessRoleKeys'>

/** Die vollständige neue Liste der AR einer ARC */
export interface AccessRoleCollectionAccessRoleKeys {
  key: string
  accessRoleKeys: string[]
}

// ===== Endpunkte des AccessRoleCollectionAdminController im Backend. Gelesen wird über die Stammdaten (useMasterData). =====

const ARC_ADMIN_PATH = '/api/rest/v1/admin/accessrolecollection'

/** Die neue ARC startet ohne AR. */
export function createAccessRoleCollection(newData: NewAccessRoleCollectionData) {
  return request<AccessRoleCollection>('POST', ARC_ADMIN_PATH, newData)
}

/** Alle Felder außer den AR, der key bestimmt die ARC und ändert sich nie. Steht accessRoleKeys im Body, zählt es nicht. */
export function updateAccessRoleCollection(changedData: NewAccessRoleCollectionData) {
  return request<AccessRoleCollection>('PUT', ARC_ADMIN_PATH, changedData)
}

/**
 * Die AR mehrerer ARCs auf einmal (ARC-AR-Matrix), in einer Transaktion. Wessen ARC sich ändert, der bekommt beim
 * nächsten Refresh die neuen AR. Zurück kommen alle ARCs.
 */
export function changeAccessRoleCollectionAccessRoleKeys(changedAccessRoleKeys: AccessRoleCollectionAccessRoleKeys[]) {
  return request<AccessRoleCollection[]>('PUT', `${ARC_ADMIN_PATH}/accessroles`, changedAccessRoleKeys)
}

/** Jeder key bekommt seine neue Position, z. B. { ARC_LAGER: 1 }. Zurück kommen alle ARCs in neuer Reihenfolge. */
export function changeAccessRoleCollectionPositions(newPositionByKey: Record<string, number>) {
  return request<AccessRoleCollection[]>('POST', `${ARC_ADMIN_PATH}/position`, newPositionByKey)
}

/** Nur per key. Die ARC fliegt dabei auch aus den Slaves aller anderen. */
export function deleteAccessRoleCollection(key: string) {
  return request<AccessRoleCollection>('DELETE', ARC_ADMIN_PATH, { key })
}
