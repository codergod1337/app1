import { request } from '../../api/client.ts'
import type { Users } from '../users/Users.ts'

/** Der Bereich der Freigabe-Status in den Stammdaten (GrantEntityStateService.SCOPE) und der key, mit dem das Backend rechnet */
export const GRANT_ENTITY_STATE_SCOPE = 'GRANT'
export const GRANT_ENTITY_STATE_ACTIVE = 'ACTIVE'

/**
 * Eine Hook-Freigabe (SolrHookGrantAccess.java), serverweit: ein Hook, nach dem der Empfänger auflösen darf, obwohl er
 * HOOK_FULL nicht hat, in jedem Kern, in dem er die Tür <KERN> hat. Kein Kern, denn jeder Kern trägt sämtliche Hooks.
 * Nichts wird gelöscht, eine Freigabe wechselt nur den Status (ACTIVE, REVOKED, EXPIRED).
 */
export interface SolrHookGrantAccess {
  id: number
  granteeUsersGuid: string
  hookKey: string
  grantedByUsersGuid: string
  grantedAt: string
  /** null heißt unbegrenzt */
  validUntil: string | null
  status: string
  statusChangedAt: string
  statusChangedByUsersGuid: string
}

/**
 * Eine Feldfreigabe (SolrAdvancedFieldGrantAccessPerCore.java): ein normales Feld eines Kerns, nach dem der Empfänger
 * auflösen darf, obwohl er <KERN>_SUCHE nicht hat; ein erweitertes Feld wird damit auch lesbar. Pro Kern, nie pro
 * Dokument. Hooks laufen seit 2026-10-07 über die Hook-Freigabe.
 */
export interface SolrAdvancedFieldGrantAccessPerCore {
  id: number
  granteeUsersGuid: string
  coreKey: string
  fieldName: string
  grantedByUsersGuid: string
  grantedAt: string
  validUntil: string | null
  status: string
  statusChangedAt: string
  statusChangedByUsersGuid: string
}

/**
 * Eine Dateifreigabe (SolrDocumentGrantedDocsPerCore.java): die Dokumente eines Kerns, die eine Auflösung mit den
 * exakten Feldwerten traf, bekamen die guid des Empfängers in userGrantedAccess. Die Zeile ist das Protokoll,
 * fieldValues die Werte je Feldname als JSON-Text. Gesetzt, entzogen und abgelaufen wird je Kern über den Service des
 * Kerns, das Register aller Kerne liest der SolrGrantAdminController.
 */
export interface SolrDocumentGrantedDocsPerCore {
  id: number
  granteeUsersGuid: string
  coreKey: string
  fieldValues: string
  grantedByUsersGuid: string
  grantedAt: string
  validUntil: string | null
  status: string
  statusChangedAt: string
  statusChangedByUsersGuid: string
  /** wie viele Dokumente beim Freigeben die guid bekamen */
  documentCount: number
}

/** Eine neue Hook-Freigabe (NewHookGrantRequest.java) */
export interface NewHookGrantData {
  granteeUsersGuid: string
  hookKeys: string[]
  /** ISO 8601 oder null für unbegrenzt */
  validUntil: string | null
}

/** Eine neue Feldfreigabe (NewAdvancedFieldGrantRequest.java) */
export interface NewAdvancedFieldGrantData {
  granteeUsersGuid: string
  coreKey: string
  fieldNames: string[]
  validUntil: string | null
}

/** Eine neue Dateifreigabe, wie der Kern-Endpunkt sie nimmt (im DMS DmsNewDocumentGrantRequest.java) */
export interface NewDocumentGrantData {
  granteeUsersGuid: string
  /** je Feld eine Liste im Typ des Feldes, siehe exactResolveFieldValuesForBackend */
  fieldValuesByFieldName: Record<string, unknown[]>
  validUntil: string | null
}

/** Was der Ablauf in Postgres getan hat (ExpiredGrantsCount.java): Hook- und Feldfreigaben */
export interface ExpiredGrantsCount {
  expiredHookGrants: number
  expiredFieldGrants: number
}

/** Was der Ablauf der Dateifreigaben eines Kerns getan hat (im DMS DmsExpiredDocumentGrantsCount.java) */
export interface ExpiredDocumentGrantsCount {
  expiredDocumentGrants: number
}

/**
 * Die Vorschau einer Dateifreigabe (GrantableDocumentsPreviewPage.java): die Treffer, die der Freigebende schreiben
 * darf, seitenweise. grantAllowed: mindestens eine Bedingung, mindestens ein Treffer, weniger als maxDocumentsPerGrant
 * Treffer. Die lesbaren Feldnamen sind die des Freigebenden in diesem Kern, danach baut die Liste ihre Spalten.
 */
export interface GrantableDocumentsPreviewPage {
  totalFound: number
  maxDocumentsPerGrant: number
  grantAllowed: boolean
  page: number
  size: number
  documents: Record<string, unknown>[]
  readableFieldAndHookNamesOfCoreForCurrentUser: string[]
}

/** Wie ein User in den Listen heißt: username, sonst E-Mail, sonst die guid. */
export function usersDisplayLabel(users: readonly Users[] | null, guid: string): string {
  const user = users?.find((candidate) => candidate.guid === guid)
  return user?.username ?? user?.email ?? guid
}

// ===== Endpunkte des SolrGrantAdminController: Hook- und Feldfreigaben, das Register der Dateifreigaben =====

const GRANT_ADMIN_PATH = '/api/rest/v1/admin/solr/grant'

export function getAllHookGrants() {
  return request<SolrHookGrantAccess[]>('GET', `${GRANT_ADMIN_PATH}/hook`)
}

/** Eine Zeile je Hook. Zurück kommen die angelegten Zeilen. */
export function grantHooksToUsers(newHookGrantData: NewHookGrantData) {
  return request<SolrHookGrantAccess[]>('POST', `${GRANT_ADMIN_PATH}/hook`, newHookGrantData)
}

/** Entzieht, löscht nicht: Die Zeile kommt mit Status REVOKED zurück. */
export function revokeHookGrant(id: number) {
  return request<SolrHookGrantAccess>('DELETE', `${GRANT_ADMIN_PATH}/hook`, { id })
}

export function getAllFieldGrants() {
  return request<SolrAdvancedFieldGrantAccessPerCore[]>('GET', `${GRANT_ADMIN_PATH}/field`)
}

/** Eine Zeile je Feld. Zurück kommen die angelegten Zeilen. */
export function grantAdvancedFieldsToUsers(newAdvancedFieldGrantData: NewAdvancedFieldGrantData) {
  return request<SolrAdvancedFieldGrantAccessPerCore[]>('POST', `${GRANT_ADMIN_PATH}/field`, newAdvancedFieldGrantData)
}

/** Entzieht, löscht nicht: Die Zeile kommt mit Status REVOKED zurück. */
export function revokeFieldGrant(id: number) {
  return request<SolrAdvancedFieldGrantAccessPerCore>('DELETE', `${GRANT_ADMIN_PATH}/field`, { id })
}

/** Das Register aller Dateifreigaben aller Kerne, nur lesen */
export function getAllDocumentGrants() {
  return request<SolrDocumentGrantedDocsPerCore[]>('GET', `${GRANT_ADMIN_PATH}/document`)
}

/** Setzt alle abgelaufenen Hook- und Feldfreigaben auf EXPIRED. Dateifreigaben laufen je Kern ab. */
export function expireGrants() {
  return request<ExpiredGrantsCount>('POST', `${GRANT_ADMIN_PATH}/expire`)
}

// ===== Dateifreigaben je Kern: jeder Kern hat seinen eigenen Endpunkt (Vorgabe 2026-10-07) =====

/** Der Admin-Endpunkt der Dateifreigabe je Kern. null, wenn der Kern noch keinen Freigabe-Service hat. */
export function documentGrantAdminPathOf(coreKey: string): string | null {
  return coreKey === 'DMS' ? '/api/rest/v1/admin/dms/grant/document' : null
}

function requireDocumentGrantAdminPath(coreKey: string): string {
  const path = documentGrantAdminPathOf(coreKey)
  if (path === null) {
    throw new Error(`kein Freigabe-Service für den Kern ${coreKey}`)
  }
  return path
}

/** Die Treffer der Bedingungen, die der Freigebende schreiben darf, eine Seite, dazu ob sich das freigeben lässt */
export function previewGrantableDocumentsPage(
  coreKey: string,
  fieldValuesByFieldName: Record<string, unknown[]>,
  page: number,
  size: number,
) {
  return request<GrantableDocumentsPreviewPage>('POST', `${requireDocumentGrantAdminPath(coreKey)}/preview`, {
    fieldValuesByFieldName,
    page,
    size,
  })
}

export function grantDocumentsToUsers(coreKey: string, newDocumentGrantData: NewDocumentGrantData) {
  return request<SolrDocumentGrantedDocsPerCore>('POST', requireDocumentGrantAdminPath(coreKey), newDocumentGrantData)
}

/** Entzieht: die guid verschwindet aus den Dokumenten, die Zeile kommt mit Status REVOKED zurück. */
export function revokeDocumentGrant(coreKey: string, id: number) {
  return request<SolrDocumentGrantedDocsPerCore>('DELETE', requireDocumentGrantAdminPath(coreKey), { id })
}

/** Setzt alle abgelaufenen Dateifreigaben dieses Kerns auf EXPIRED, samt guid aus den Dokumenten */
export function expireDocumentGrants(coreKey: string) {
  return request<ExpiredDocumentGrantsCount>('POST', `${requireDocumentGrantAdminPath(coreKey)}/expire`)
}
