import { request } from '../../api/client.ts'
import { sha256Hex } from '../../components/sha256Hex.ts'

/** Über welchen Login ein Token entstand (Area.java) */
export type Area = 'INTERN' | 'EXTERN'

/** Wer gerade da ist (SessionInfo.java). Unangemeldet: loggedIn = false, keine AR, keine ARC, alles andere leer. */
export interface SessionInfo {
  loggedIn: boolean
  usersGuid: string | null
  email: string | null
  /** die direkt zugewiesenen AR und die aus der ARC */
  accessRoleKeys: string[]
  /** die ARC des Users, null ohne ARC: Sie ist optional */
  accessRoleCollectionKey: string | null
  area: Area | null
  /** Sitzungsplatz 1 bis 3, 0 ohne Anmeldung */
  sessionNumber: number
  /** Ablauf des JWT nach ISO-8601. Das Frontend kann das Token nicht lesen und plant damit den Refresh. */
  tokenExpiresAt: string | null
}

/** Die Sitzung eines Unangemeldeten */
export const ANONYMOUS_SESSION: SessionInfo = {
  loggedIn: false,
  usersGuid: null,
  email: null,
  accessRoleKeys: [],
  accessRoleCollectionKey: null,
  area: null,
  sessionNumber: 0,
  tokenExpiresAt: null,
}

/**
 * Die Antwort nach Schritt 1 (Login2faPollToken.java): nur der Abholschein für dieses Fenster. Der Bestätigungs-Token
 * geht per Mail an den User, nie in der Antwort.
 */
export interface Login2faPollToken {
  pollToken: string
}

/** Antwort beim Abholen: WAITING, solange nicht bestätigt ist, danach LOGGED_IN und die Cookies sind gesetzt */
export interface LoginClaim {
  status: 'WAITING' | 'LOGGED_IN'
  sessionNumber?: number
}

// ===== Endpunkte des InternLoginPublicController: Dieses Frontend meldet sich in der area INTERN an =====

const LOGIN_PATH = '/api/rest/v1/public/intern/login'

/** Schritt 1: email und Passwort. Das Passwort geht nur als SHA-256 hinaus. 503, wenn die Mail nicht hinausging. */
export async function startLogin(email: string, password: string) {
  return request<Login2faPollToken>('POST', `${LOGIN_PATH}/start`, {
    email,
    password: await sha256Hex(password),
  })
}

/** Schritt 2: der Bestätigungs-Token, über den Link aus der Mail (neues Fenster) oder von Hand eingefügt */
export function confirmLogin(confirmationToken: string) {
  return request<void>('POST', `${LOGIN_PATH}/confirm`, { confirmationToken })
}

/** Schritt 3, im Sekundentakt bis LOGGED_IN */
export function claimLogin(pollToken: string) {
  return request<LoginClaim>('POST', `${LOGIN_PATH}/claim`, { pollToken })
}

/**
 * Eine Sitzung des angemeldeten Users (UsersSessionForClient.java): ein belegter Platz, kein Token, kein Hash.
 * lastRefreshedAt ist der Hinweis auf das Gerät: Ein offenes Fenster erneuert alle paar Minuten.
 */
export interface UsersSessionForClient {
  sessionNumber: number
  area: Area
  /** ISO-8601 */
  loggedInSince: string
  refreshCount: number
  lastRefreshedAt: string
  expiresAt: string
  /** die Sitzung dieses Fensters */
  current: boolean
}

// ===== Endpunkte des UsersSessionController: die eigenen Sitzungen, nur angemeldet =====

const USERS_SESSION_PATH = '/api/rest/v1/session'

export function getCurrentUsersSessions() {
  return request<UsersSessionForClient[]>('GET', USERS_SESSION_PATH)
}

/** Eine Sitzung beenden. War es die eigene, sind danach die Cookies weg: Sitzung neu laden. */
export function endCurrentUsersSession(sessionNumber: number) {
  return request<void>('DELETE', USERS_SESSION_PATH, { sessionNumber })
}

/** Alle Sitzungen beenden, auch diese: danach sind die Cookies weg, Sitzung neu laden. */
export function endAllCurrentUsersSessions() {
  return request<void>('DELETE', `${USERS_SESSION_PATH}/all`)
}

// ===== Endpunkte des SessionPublicController =====

const SESSION_PATH = '/api/rest/v1/public/session'

/** Immer erfolgreich: die Sitzung des Angemeldeten oder die leere Sitzung */
export function getSessionInfo() {
  return request<SessionInfo>('GET', SESSION_PATH)
}

/** Neue Cookies. Ein gescheiterter Refresh darf nie wiederholt werden, ein zweiter Versuch sähe wie Diebstahl aus. */
export function refreshSession() {
  return request<{ sessionNumber: number }>('POST', `${SESSION_PATH}/refresh`)
}

/** Kette dieses Geräts widerrufen, Cookies löschen. Scheitert nie. */
export function logoutSession() {
  return request<void>('POST', `${SESSION_PATH}/logout`)
}
