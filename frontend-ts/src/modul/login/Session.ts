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

/** Die zwei Tokens nach Schritt 1: Abholschein und, bis es Mailversand gibt, der Bestätigungs-Token */
export interface Login2faTokens {
  pollToken: string
  confirmationToken: string
}

/** Antwort beim Abholen: WAITING, solange nicht bestätigt ist, danach LOGGED_IN und die Cookies sind gesetzt */
export interface LoginClaim {
  status: 'WAITING' | 'LOGGED_IN'
  sessionNumber?: number
}

// ===== Endpunkte des InternLoginPublicController: Dieses Frontend meldet sich in der area INTERN an =====

const LOGIN_PATH = '/api/rest/v1/public/intern/login'

/** Schritt 1: email und Passwort. Das Passwort geht nur als SHA-256 hinaus. */
export async function startLogin(email: string, password: string) {
  return request<Login2faTokens>('POST', `${LOGIN_PATH}/start`, { email, password: await sha256Hex(password) })
}

/** Schritt 2: der Aktivierungslink, im neuen Fenster */
export function confirmLogin(confirmationToken: string) {
  return request<void>('POST', `${LOGIN_PATH}/confirm`, { confirmationToken })
}

/** Schritt 3, im Sekundentakt bis LOGGED_IN */
export function claimLogin(pollToken: string) {
  return request<LoginClaim>('POST', `${LOGIN_PATH}/claim`, { pollToken })
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
