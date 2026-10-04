import { request } from '../../api/client.ts'
import type { Users } from '../users/Users.ts'

/** Der Bereich der Freigabe-Status in den Stammdaten (GrantEntityStateService.SCOPE) und der key, mit dem das Backend rechnet */
export const GRANT_ENTITY_STATE_SCOPE = 'GRANT'
export const GRANT_ENTITY_STATE_ACTIVE = 'ACTIVE'

/**
 * Eine Feldfreigabe (SolrAdvancedFieldGrantAccessPerCore.java): ein erweitertes Feld oder ein Hook eines Kerns, das
 * ein User ohne Vollsicht trotzdem lesen darf. Pro Kern, nie pro Dokument. Nichts wird gelöscht, eine Freigabe wechselt
 * nur den Status (ACTIVE, REVOKED, EXPIRED).
 */
export interface SolrAdvancedFieldGrantAccessPerCore {
  id: number
  granteeUsersGuid: string
  coreKey: string
  fieldName: string
  grantedByUsersGuid: string
  grantedAt: string
  /** null heißt unbegrenzt */
  validUntil: string | null
  status: string
  statusChangedAt: string
  statusChangedByUsersGuid: string
}

/**
 * Eine Dateifreigabe (SolrDocumentGrantedDocsPerCore.java): die Dokumente eines Kerns, die eine Auflösung mit den
 * exakten Feldwerten traf, bekamen die guid des Empfängers in userGrantedAccess. Die Zeile ist das Protokoll,
 * fieldValues die Werte je Feldname als JSON-Text.
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

/** Eine neue Feldfreigabe (NewAdvancedFieldGrantRequest.java) */
export interface NewAdvancedFieldGrantData {
  granteeUsersGuid: string
  coreKey: string
  fieldNames: string[]
  /** ISO 8601 oder null für unbegrenzt */
  validUntil: string | null
}

/** Eine neue Dateifreigabe (NewDocumentGrantRequest.java) */
export interface NewDocumentGrantData {
  granteeUsersGuid: string
  coreKey: string
  /** je Feld eine Liste im Typ des Feldes, siehe exactResolveFieldValuesForBackend */
  fieldValuesByFieldName: Record<string, unknown[]>
  validUntil: string | null
}

/** Was der Ablauf getan hat (ExpiredGrantsCount.java) */
export interface ExpiredGrantsCount {
  expiredFieldGrants: number
  expiredDocumentGrants: number
}

/**
 * Die Vorschau einer Dateifreigabe (GrantableDocumentsPreviewPage.java): die Treffer, die der Freigebende sieht und
 * ändern darf, seitenweise. grantAllowed: mindestens eine Bedingung, mindestens ein Treffer, weniger als
 * maxDocumentsPerGrant Treffer. Die lesbaren Feldnamen sind die des Freigebenden in diesem Kern, danach baut die Liste
 * ihre Spalten.
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

// ===== Endpunkte des SolrGrantAdminController =====

const GRANT_ADMIN_PATH = '/api/rest/v1/admin/solr/grant'

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

export function getAllDocumentGrants() {
  return request<SolrDocumentGrantedDocsPerCore[]>('GET', `${GRANT_ADMIN_PATH}/document`)
}

/** Die Treffer der Bedingungen, die der Freigebende sieht und ändern darf, eine Seite, dazu ob sich das freigeben lässt */
export function previewGrantableDocumentsPage(
  coreKey: string,
  fieldValuesByFieldName: Record<string, unknown[]>,
  page: number,
  size: number,
) {
  return request<GrantableDocumentsPreviewPage>('POST', `${GRANT_ADMIN_PATH}/document/preview`, {
    coreKey,
    fieldValuesByFieldName,
    page,
    size,
  })
}

export function grantDocumentsToUsers(newDocumentGrantData: NewDocumentGrantData) {
  return request<SolrDocumentGrantedDocsPerCore>('POST', `${GRANT_ADMIN_PATH}/document`, newDocumentGrantData)
}

/** Entzieht: die guid verschwindet aus den Dokumenten, die Zeile kommt mit Status REVOKED zurück. */
export function revokeDocumentGrant(id: number) {
  return request<SolrDocumentGrantedDocsPerCore>('DELETE', `${GRANT_ADMIN_PATH}/document`, { id })
}

/** Setzt alle abgelaufenen Freigaben auf EXPIRED, bei Dateifreigaben samt guid aus den Dokumenten */
export function expireGrants() {
  return request<ExpiredGrantsCount>('POST', `${GRANT_ADMIN_PATH}/expire`)
}
