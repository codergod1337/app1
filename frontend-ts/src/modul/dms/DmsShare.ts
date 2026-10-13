import { request } from '../../api/client.ts'

/** BINARY: nur der Download; DOCUMENT: die Felder der Liste, der Download dazu wählbar (ShareTokenKind.java) */
export type DmsShareTokenKind = 'BINARY' | 'DOCUMENT'

export type DmsShareTokenStatus = 'ACTIVE' | 'EXPIRED'

/**
 * Ein Abholschein (ShareToken.java): die Freigabe eines DMS-Dokuments an jemanden ohne Konto über einen Link. Der
 * Klartext des Tokens steht nie hier, nur beim Anlegen einmal in CreatedDmsShareToken.
 */
export interface DmsShareToken {
  id: number
  coreKey: string
  documentId: string
  kind: DmsShareTokenKind
  includedFieldNames: string[]
  binaryIncluded: boolean
  grantedByUsersGuid: string
  recipient: string | null
  maxUses: number
  uses: number
  /** Zeitpunkt nach ISO 8601 */
  expiresAt: string
  status: DmsShareTokenStatus
  /** je Einlösung "Zeitpunkt IP VIEW|DOWNLOAD" */
  redemptions: string[]
  createdAt: string
}

/** Die Antwort auf das Anlegen: die Zeile und der Klartext, den es nur dieses eine Mal gibt */
export interface CreatedDmsShareToken {
  shareToken: DmsShareToken
  token: string
}

export interface NewDmsShareTokenData {
  id: string
  kind: DmsShareTokenKind
  /** nur bei DOCUMENT, mindestens ein Feld */
  includedFieldNames: string[]
  /** nur bei DOCUMENT; BINARY hat den Download immer */
  binaryIncluded: boolean
  recipient: string | null
  maxUses: number
  /** Zeitpunkt nach ISO 8601 mit Zone */
  expiresAt: string
}

// ===== Endpunkte des DmsShareController, angemeldet: ADMIN oder DMS_PUBLIC =====

const DMS_SHARE_PATH = '/api/rest/v1/solr/dms/share'

/** Alle Scheine des Kerns DMS, jüngste zuerst, auch abgelaufene: die Übersicht „Freigaben“ */
export function getAllDmsShareTokens() {
  return request<DmsShareToken[]>('GET', DMS_SHARE_PATH)
}

/** Alle Scheine eines Dokuments, jüngste zuerst, auch abgelaufene */
export function getDmsShareTokens(id: string) {
  return request<DmsShareToken[]>('GET', `${DMS_SHARE_PATH}/${id}`)
}

export function createDmsShareToken(newShareTokenData: NewDmsShareTokenData) {
  return request<CreatedDmsShareToken>('POST', DMS_SHARE_PATH, newShareTokenData)
}

/** Zurückziehen: Die Zeile wird EXPIRED und bleibt, der Link gilt nicht mehr */
export function expireDmsShareToken(shareTokenId: number) {
  return request<DmsShareToken>('DELETE', DMS_SHARE_PATH, { shareTokenId })
}

/** Endgültig löschen samt History, nur ADMIN (Admin-Endpunkt) */
export function deleteDmsShareToken(shareTokenId: number) {
  return request<void>('DELETE', '/api/rest/v1/admin/solr/dms/share', { shareTokenId })
}

// ===== Freigabe an einen User: seine guid in userGrantedAccess des Dokuments (User 2026-10-10) =====

export type DmsUserGrantStatus = 'ACTIVE' | 'REVOKED'

/**
 * Eine User-Freigabe aus dem Register (UserGrant.java): wer hat wann wem welches Dokument freigegeben, und wann wer sie
 * zurückgenommen hat. Der Zugriff selbst ist die guid in userGrantedAccess des Dokuments.
 */
export interface DmsUserGrant {
  id: number
  coreKey: string
  documentId: string
  granteeUsersGuid: string
  grantedByUsersGuid: string
  createdAt: string
  status: DmsUserGrantStatus
  revokedAt: string | null
  revokedByUsersGuid: string | null
}

/** Alle User-Freigaben im DMS, jüngste zuerst, auch zurückgenommene: die Übersicht „Freigaben“ */
export function getAllDmsUserGrants() {
  return request<DmsUserGrant[]>('GET', `${DMS_SHARE_PATH}/user`)
}

/** Die User-Freigaben eines Dokuments, jüngste zuerst, auch zurückgenommene */
export function getDmsUserGrants(id: string) {
  return request<DmsUserGrant[]>('GET', `${DMS_SHARE_PATH}/user/${id}`)
}

/** Freigeben: Zeile im Register und die guid ins Dokument. Der User liest, sucht, löst und lädt es danach wie über die readAcl */
export function grantDmsDocumentToUser(id: string, usersGuid: string) {
  return request<DmsUserGrant>('POST', `${DMS_SHARE_PATH}/user`, { id, usersGuid })
}

/** Zurücknehmen: die Zeilen werden REVOKED und bleiben, die guid geht aus dem Dokument */
export function revokeDmsDocumentFromUser(id: string, usersGuid: string) {
  return request<void>('DELETE', `${DMS_SHARE_PATH}/user`, { id, usersGuid })
}

/** Endgültig löschen samt History, nur ADMIN (Admin-Endpunkt) */
export function deleteDmsUserGrant(userGrantId: number) {
  return request<void>('DELETE', '/api/rest/v1/admin/solr/dms/share/user', { userGrantId })
}

// ===== Station Detailansicht (DmsShareController), ohne Anmeldung =====

/**
 * Was die Station „Detailansicht“ einem Empfänger ohne Konto gibt (DmsSharedDocument.java): die id, die Art des
 * Scheins, ob der Download dazu darf, bis wann und wie oft noch, und die Werte der freigegebenen Felder, nur diese.
 */
export interface DmsSharedDocument {
  id: string
  kind: DmsShareTokenKind
  binaryIncluded: boolean
  expiresAt: string
  usesLeft: number
  fieldValues: Record<string, unknown>
}

/** token → die Antwort; jeder Aufruf zählt als Einlösung, deshalb je Token nur einmal, auch im StrictMode */
const sharedDocumentsByToken = new Map<string, Promise<DmsSharedDocument>>()

/** Den Schein einlösen und die Felder holen. Jeder Aufruf zählt gegen das Limit, deshalb je Token nur einmal je Sitzung. */
export function getDmsSharedDocumentByToken(token: string): Promise<DmsSharedDocument> {
  let pending = sharedDocumentsByToken.get(token)
  if (pending === undefined) {
    pending = request<DmsSharedDocument>('GET', `/api/rest/v1/public/solr/dms/document/token/${token}`).then(
      ({ data }) => data,
    )
    pending.catch(() => sharedDocumentsByToken.delete(token))
    sharedDocumentsByToken.set(token, pending)
  }
  return pending
}

/** datetime-local (Ortszeit ohne Zone) nach ISO 8601 in UTC, leer oder unlesbar heißt null */
export function localDateTimeInputToIso(local: string): string | null {
  if (local === '') {
    return null
  }
  const date = new Date(local)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}

// ===== Die Links der Stationen: je Station ein eigener öffentlicher Endpunkt (User 2026-10-09) =====

/** Station nur Download (FileDataController, ohne Anmeldung): die Datei, bei BINARY und bei DOCUMENT mit Download */
export function dmsShareDownloadUrl(token: string): string {
  return `${window.location.origin}/api/rest/v1/public/filedata/token/${token}`
}

/** Station Detailansicht: die öffentliche Seite des Frontends, die den Schein am DMS-Endpunkt einlöst */
export function dmsShareDocumentUrl(token: string): string {
  return `${window.location.origin}/share/dms/document/${token}`
}

/** Der Link, der zum Schein passt: BINARY lädt direkt, DOCUMENT öffnet die Detailansicht */
export function dmsShareUrlOf(kind: DmsShareTokenKind, token: string): string {
  return kind === 'BINARY' ? dmsShareDownloadUrl(token) : dmsShareDocumentUrl(token)
}
