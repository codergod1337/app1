import { request } from '../../api/client.ts'

/** Welche Art Einschränkung ein Debuff ist (UsersDebuffType.java). Weitere Typen kommen dazu. */
export type UsersDebuffType = 'RESOLVE'

export const USERS_DEBUFF_TYPES: UsersDebuffType[] = ['RESOLVE']

/**
 * Eine Einschränkung eines Users (UsersDebuffs.java): je User, Typ und Solr-Feld eine Zeile. Bei RESOLVE bekommt jede
 * Auflösung des Users den Filter solrFieldName:(wert1 OR wert2 …), egal was er fragt. Setzt nur der Admin.
 */
export interface UsersDebuffs {
  id: number
  usersGuid: string
  type: UsersDebuffType
  /** Name eines Hooks oder Feldes in Solr, über alle Kerne eindeutig */
  solrFieldName: string
  /** die Pflichtwerte als Text, ein Eintrag bei einwertigen Feldern */
  values: string[]
}

export interface NewUsersDebuffsData {
  usersGuid: string
  type: UsersDebuffType
  solrFieldName: string
  values: string[]
}

// ===== Endpunkte des UsersDebuffsAdminController =====

const USERS_DEBUFFS_ADMIN_PATH = '/api/rest/v1/admin/users/debuffs'

/** Die Debuffs eines Users, ohne Zeilen eine leere Liste */
export function getUsersDebuffsByUsersGuid(usersGuid: string) {
  return request<UsersDebuffs[]>('GET', `${USERS_DEBUFFS_ADMIN_PATH}/${usersGuid}`)
}

/** Neuer Debuff. Danach enden alle Sitzungen des Users, damit sein Token den Debuff kennt. */
export function createUsersDebuffs(newUsersDebuffsData: NewUsersDebuffsData) {
  return request<UsersDebuffs>('POST', USERS_DEBUFFS_ADMIN_PATH, newUsersDebuffsData)
}

/** Neue Werte, Typ und Feld bleiben. Danach enden alle Sitzungen des Users. */
export function changeUsersDebuffsValues(id: number, values: string[]) {
  return request<UsersDebuffs>('PUT', USERS_DEBUFFS_ADMIN_PATH, { id, values })
}

/** Löscht den Debuff. Danach enden alle Sitzungen des Users. */
export function deleteUsersDebuffs(id: number) {
  return request<UsersDebuffs>('DELETE', USERS_DEBUFFS_ADMIN_PATH, { id })
}
