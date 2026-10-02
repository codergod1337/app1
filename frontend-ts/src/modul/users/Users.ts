import { request } from '../../api/client.ts'
import { sha256Hex } from '../../components/sha256Hex.ts'

/** Ein User, wie ihn das Backend liefert (Users.java). */
export interface Users {
  guid: string
  email: string
  username: string | null
  vorname: string | null
  nachname: string | null
  /** Zeitpunkt nach ISO-8601 */
  createdAt: string
  /** kein Mensch, sondern ein Backend-Dienst, ändert nur der Admin */
  serviceAccount: boolean
  /** freier String, z. B. ACTIVE oder DELETED, Anzeige über entityStatusName. Anmelden geht nur mit ACTIVE. */
  status: string
}

/** Die Felder, die ein Admin beim Anlegen und Bearbeiten setzt. Pflicht ist nur email. */
export interface UsersData {
  email: string
  username?: string
  vorname?: string
  nachname?: string
  serviceAccount: boolean
  status: string
}

/** Was der User selbst an sich ändert. Was fehlt, wird geleert. */
export interface UsersNamesData {
  username?: string
  vorname?: string
  nachname?: string
}

// ===== Endpunkte des UsersController (jeder bei sich selbst) =====

const USERS_PATH = '/api/rest/v1/users'

export function getUsersByGuid(guid: string) {
  return request<Users>('GET', `${USERS_PATH}/${guid}`)
}

/** Der User ändert username, vorname und nachname. email, serviceAccount und status bleiben. */
export function updateUsersNames(guid: string, changedUsersNamesData: UsersNamesData) {
  return request<Users>('PUT', USERS_PATH, { guid, ...changedUsersNamesData })
}

/**
 * Der User löscht sich selbst, aber nur weich: Status DELETED, anmelden geht danach nicht mehr. Zur Bestätigung das
 * eigene Passwort, es geht nur als SHA-256 hinaus.
 */
export async function softDeleteUsers(guid: string, password: string) {
  return request<Users>('DELETE', USERS_PATH, { guid, password: await sha256Hex(password) })
}

// ===== Endpunkte des UsersAdminController =====

const USERS_ADMIN_PATH = '/api/rest/v1/admin/users'

export function getAllUsers() {
  return request<Users[]>('GET', USERS_ADMIN_PATH)
}

/** guid und createdAt vergibt das Backend */
export function createUsers(newUsersData: UsersData) {
  return request<Users>('POST', USERS_ADMIN_PATH, newUsersData)
}

/**
 * Ersetzt alle Felder, was fehlt, wird geleert. guid und createdAt bleiben. Wechselt der Status weg von ACTIVE,
 * enden alle Sitzungen des Users.
 */
export function updateUsers(guid: string, changedUsersData: UsersData) {
  return request<Users>('PUT', USERS_ADMIN_PATH, { guid, ...changedUsersData })
}

/** Ändert die guid, Zugangsdaten und Einstellungen ziehen mit um. Zurück kommt der User unter der neuen guid. */
export function changeUsersGuid(guidCurrent: string, guidNew: string) {
  return request<Users>('PATCH', USERS_ADMIN_PATH, { guidCurrent, guidNew })
}

/** Löscht den User endgültig samt Einstellungen, Details und Zugangsdaten. Zurück kommt der User, wie er vorher war. */
export function deleteUsers(guid: string) {
  return request<Users>('DELETE', USERS_ADMIN_PATH, { guid })
}
