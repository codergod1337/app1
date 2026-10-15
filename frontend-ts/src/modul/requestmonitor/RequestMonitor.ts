import { request } from '../../api/client.ts'

/** Das Modul Anfragenmonitor (User 2026-10-15): die Map der Anfragen an den Login-Endpunkt und die Sperrliste, live, nur ADMIN */
export const REQUESTMONITOR_PATH = '/requestmonitor'

/** So oft holt das Modul den Stand beider Maps */
export const REQUESTMONITOR_POLL_MS = 2000

/** Vorgabe der Sperrdauer im Popup: 12 Stunden */
export const REQUESTMONITOR_DEFAULT_BLOCK_MINUTES = 720

/** Eine Anfrage in der Map (LoggedClientRequest.java) */
export interface LoggedClientRequest {
  /** laufende Nummer, der Schlüssel in der Map */
  id: number
  clientIp: string
  /** ISO-8601 */
  receivedAt: string
  /** Methode und Pfad, z. B. "POST /api/rest/v1/public/intern/login/start" */
  endpoint: string
  /** true: die IP stand auf der Sperrliste, die Anfrage bekam 429 */
  blocked: boolean
}

/** Eine gesperrte IP (BlockedClientIp.java) */
export interface BlockedClientIp {
  clientIp: string
  /** ISO-8601 */
  blockedAt: string
  /** ISO-8601, danach ist die IP wieder frei */
  expiresAt: string
  reason: string
  /** wie viele Anfragen der Filter seit der Sperre abgewiesen hat, an allen Endpunkten zusammen */
  rejectedRequests: number
}

/** Was das Popup zum Sperren schickt (BlockClientIpRequest.java) */
export interface BlockClientIpRequest {
  clientIp: string
  durationMinutes: number
  reason: string
}

const REQUESTMONITOR_ADMIN_PATH = '/api/rest/v1/admin/requestmonitor'

/** Alle Anfragen in der Map, die neueste zuerst */
export function getLoggedClientRequests() {
  return request<LoggedClientRequest[]>('GET', REQUESTMONITOR_ADMIN_PATH)
}

/** Die Sperrliste, die am frühesten ablaufende Sperre zuerst */
export function getBlockedClientIps() {
  return request<BlockedClientIp[]>('GET', `${REQUESTMONITOR_ADMIN_PATH}/blocked`)
}

/** Sperre aussprechen. 400 bei leerer IP oder Dauer unter 1. Eine bestehende Sperre derselben IP wird ersetzt. */
export function blockClientIp(blockClientIpRequest: BlockClientIpRequest) {
  return request<BlockedClientIp>('POST', `${REQUESTMONITOR_ADMIN_PATH}/blocked`, blockClientIpRequest)
}

/** Sperre aufheben. 404, wenn die IP nicht gesperrt ist. */
export function liftClientIpBlock(clientIp: string) {
  return request<void>('DELETE', `${REQUESTMONITOR_ADMIN_PATH}/blocked`, { clientIp })
}
