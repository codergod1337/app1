import { request, upload, type UploadProgress } from '../../api/client.ts'
import type { SolrAdvancedFieldGrantAccessPerCore, SolrDocumentGrantedDocsPerCore } from '../grant/Grant.ts'

/** Die AR, die das DMS-Modul sichtbar und den Upload erlaubt macht. Das Backend prüft dieselbe. */
export const DMS_ACCESS_ROLE_KEY = 'DMS'

/** Der Kern des DMS im SolrManager (DmsUploadService.CORE_KEY). Seine Felder aus den Stammdaten sind die Maske. */
export const DMS_CORE_KEY = 'DMS'

/** Das eine Feld des Kerns, das die Dateiart trägt: in der Maske eine Auswahl aus den Dateiarten, kein freier Text */
export const FILE_SUB_CLASS_FIELD_NAME = 'fileSubClassKey'

/** Was Tika fand (DmsUploadService.TikaOutcome), oder warum nicht: ok false mit error, dann ist der Rest leer */
export interface DmsTikaOutcome {
  ok: boolean
  error: string | null
  /** aus den Bytes erkannt, nicht aus der Endung */
  mimeType: string | null
  createdAt: string | null
  modifiedAt: string | null
  /** EXIF-Ausrichtung 1 bis 8, null ohne Angabe */
  orientation: number | null
  textLength: number
  text: string
  /** jeder Eintrag, den Tika kennt: Name, Werte */
  metadata: Record<string, string[]>
  durationMs: number
}

/**
 * Das Vorschaubild (DmsUploadService.PreviewOutcome), oder warum keins: attempted false, wenn die Endung im Katalog
 * kein Vorschaubild hat; ok false mit error, wenn die Bytes kein lesbares Bild sind. base64Png ohne Präfix.
 */
export interface DmsPreviewOutcome {
  attempted: boolean
  ok: boolean
  error: string | null
  base64Png: string | null
  sourceWidth: number
  sourceHeight: number
  orientation: number
  bytes: number
  durationMs: number
}

/**
 * Die Antwort der Upload-Probe (DmsUploadService.DmsUploadProbe): die Datei, wie der Browser sie schickte, ihre Endung
 * nach unserer Regel, ihr SHA-256 (später die id des Dokuments), die geprüften Felder in der Reihenfolge des Kerns,
 * alles von Tika und das Vorschaubild. stored ist in dieser Stufe immer false.
 */
export interface DmsUploadProbe {
  fileName: string
  fileExtension: string
  contentType: string | null
  sizeBytes: number
  sha256: string
  document: Record<string, unknown>
  tika: DmsTikaOutcome
  preview: DmsPreviewOutcome
  stored: boolean
}

/**
 * Was der Upload tat (DmsUploadService.UploadStatus): CREATED neu angelegt, MERGED dieselben Bytes lagen schon in dieser
 * Dateiart und bekamen nur die Hooks dazu, EXISTS sie liegen unter einer anderen Dateiart, nichts geändert.
 */
export type DmsUploadStatus = 'CREATED' | 'MERGED' | 'EXISTS'

/** Das Ergebnis des Uploads: was passiert ist, die id, das Dokument wie es jetzt in Solr steht, dazu die Probe */
export interface DmsUploadResult {
  status: DmsUploadStatus
  id: string
  document: Record<string, unknown>
  probe: DmsUploadProbe
}

/** Gibt es diese Bytes schon im DMS? Mit id, Dateiart und Status des vorhandenen Dokuments. */
export interface DmsExistence {
  exists: boolean
  id: string | null
  fileSubClassKey: string | null
  status: string | null
}

function uploadFormData(file: File, document: Record<string, unknown>): FormData {
  const formData = new FormData()
  formData.append('file', file, file.name)
  formData.append('data', JSON.stringify(document))
  return formData
}

/**
 * Die Probe: Datei und Felder ans Backend, das prüft, mit Tika liest und die Vorschau rechnet, aber nichts speichert.
 * Bleibt dauerhaft neben dem echten Upload, z. B. um die Spracherkennung zu testen. document trägt jedes Feld unter
 * seinem Solr-Namen, multiValued als Liste, Hooks genauso, denn in Solr sind sie Felder.
 */
export function probeDmsUpload(file: File, document: Record<string, unknown>) {
  return upload<DmsUploadProbe>('/api/rest/v1/dms/upload-probe', uploadFormData(file, document))
}

/**
 * Der Upload: wie die Probe, dann speichern (SFTP, Solr). Die Dateiart ist Pflicht, der User braucht ihr Schreibrecht.
 * onProgress meldet die gesendeten Bytes, nach 100 % arbeitet das Backend noch.
 */
export function uploadDmsFile(file: File, document: Record<string, unknown>, onProgress?: UploadProgress) {
  return upload<DmsUploadResult>('/api/rest/v1/dms/upload', uploadFormData(file, document), onProgress)
}

/** Gibt es diese Bytes schon? sha256 in Hex, klein, wie sha256Hex.ts ihn liefert. */
export function getDmsExistence(sha256: string) {
  return request<DmsExistence>('GET', `/api/rest/v1/dms/exists/${sha256}`)
}

// ===== Endpunkte des DmsExactFieldValueResolveController und des DmsDownloadController =====

/**
 * Was der current user im Kern DMS darf (SolrCurrentUserReadAndWriteAccessForCore.SolrCurrentUserCoreAccessForClient):
 * Vollsicht (DMS_FULL oder DMS_SUCHE) oder nur Minimum plus Freigaben, Suche oder nicht, die Felder und Hooks, die er
 * bekommt, und die, nach denen er auflösen darf.
 */
export interface CurrentUserDmsAccessForClient {
  coreKey: string
  everyFieldAndHookOfCoreReadableByCurrentUser: boolean
  freeTextSearchAllowed: boolean
  readableFieldAndHookNamesOfCoreForCurrentUser: string[]
  exactResolveAllowedFieldAndHookNamesOfCoreForCurrentUser: string[]
}

/**
 * Eine Auflösung nach exakten Feldwerten (SolrExactFieldValueResolveRequest.java): je Feld ein Wert oder eine Liste,
 * ohne Felder alles Sichtbare
 */
export interface DmsExactFieldValueResolveRequest {
  fieldValuesByFieldName: Record<string, unknown[]>
  page: number
  size: number
  sortFieldName: string | null
  sortDescending: boolean
}

/** Eine Trefferseite (SolrExactFieldValueResolveService.SolrResolvedDocumentsPage): die Dokumente nur mit den lesbaren Feldern */
export interface DmsResolvedDocumentsPage {
  totalFound: number
  page: number
  size: number
  documents: Record<string, unknown>[]
}

export function getCurrentUserDmsAccess() {
  return request<CurrentUserDmsAccessForClient>('GET', '/api/rest/v1/dms/access')
}

export function resolveDmsDocumentsPageByExactFieldValues(resolveRequest: DmsExactFieldValueResolveRequest) {
  return request<DmsResolvedDocumentsPage>('POST', '/api/rest/v1/dms/resolve', resolveRequest)
}

/**
 * Der einzige Weg zu den Bytes einer Datei (DmsDownloadController): ein normaler Link, das Token kommt aus dem Cookie.
 * Das Backend lädt das Dokument mit der Leseprüfung des Users noch einmal, bevor es die Datei vom SFTP holt.
 */
export function dmsFileDownloadUrl(id: string): string {
  return `/api/rest/v1/dms/download/${id}`
}

// ===== Endpunkte des DmsSolrDocumentController: ein einzelnes Dokument lesen und ändern =====

/**
 * Ein DMS-Dokument für Detailansicht, Bearbeiten und Viewer (DmsSolrDocumentForClient.java): die Felder, die der User
 * lesen darf, und ob writeAcl eine seiner ARs enthält. Das Flag ist Anzeige, das Backend prüft beim Schreiben selbst.
 */
export interface DmsSolrDocumentForClient {
  document: Record<string, unknown>
  writeAclMatchForCurrentUser: boolean
}

/**
 * Was die Bearbeiten-Maske schickt (DmsSolrDocumentEditRequest.java). Fehlt ein Feld, bleibt es unverändert, ein leerer
 * Text leert es. Hooks: je Hook-Key die Werteliste, eine leere Liste leert den Hook, ein fehlender Hook bleibt.
 */
export interface DmsSolrDocumentEditData {
  id: string
  fileName?: string
  fileIndex?: string
  fileDescription?: string
  /** ISO 8601 oder ein Tag JJJJ-MM-TT */
  documentCreatedAt?: string
  fileSubClassKey?: string
  hookValuesByHookKey?: Record<string, string[]>
  /** ein Status des Bereichs DMS, DELETED ist das weiche Löschen */
  status?: string
  /** ein Code aus DmsDocumentLanguage.ts, leer löscht; nur in Endungsgruppen mit Sprache, sonst 400 */
  documentLanguage?: string
}

/** Das Feld der Sprache im Kern DMS, in Solr ein string mit dem ISO-639-1-Code */
export const DOCUMENT_LANGUAGE_FIELD_NAME = 'documentLanguage'

/**
 * Die Freigaben, die ein ADMIN zu einem Dokument sieht (DmsSolrDocumentGrantsForClient.java): die guids in
 * userGrantedAccess, die Wahrheit für den Zugriff, dazu die wirksamen Dateifreigaben dieser User im Kern DMS aus dem
 * Register und Anzeigenamen zu den guids. Ein Kontrollblick, kein Schutz.
 */
export interface DmsSolrDocumentGrantsForClient {
  grantedUsersGuids: string[]
  documentGrantsInForce: SolrDocumentGrantedDocsPerCore[]
  usersDisplayNameByGuid: Record<string, string>
}

export function getDmsSolrDocumentGrants(id: string) {
  return request<DmsSolrDocumentGrantsForClient>('GET', `/api/rest/v1/dms/solr-document/${id}/grants`)
}

// ===== Endpunkte des DmsCurrentUsersGrantsController: der Tab „Meine Freigaben“ =====

/** Was dem current user im DMS freigegeben ist (DmsCurrentUsersGrantsForClient.java), Register mit allen Status */
export interface DmsCurrentUsersGrantsForClient {
  documentGrants: SolrDocumentGrantedDocsPerCore[]
  fieldGrants: SolrAdvancedFieldGrantAccessPerCore[]
  /** guid → Anzeigename, für „freigegeben von“ */
  usersDisplayNameByGuid: Record<string, string>
}

export function getCurrentUsersDmsGrants() {
  return request<DmsCurrentUsersGrantsForClient>('GET', '/api/rest/v1/dms/my-grants')
}

/** Die DMS-Dokumente, die die guid des current users in userGrantedAccess tragen, mit seiner Leseprüfung, seitenweise */
export function resolveDmsDocumentsPageGrantedToCurrentUser(page: number, size: number) {
  return request<DmsResolvedDocumentsPage>('POST', '/api/rest/v1/dms/my-grants/documents', { page, size })
}

export function getDmsSolrDocument(id: string) {
  return request<DmsSolrDocumentForClient>('GET', `/api/rest/v1/dms/solr-document/${id}`)
}

export function editDmsSolrDocument(editData: DmsSolrDocumentEditData) {
  return request<DmsSolrDocumentForClient>('PUT', '/api/rest/v1/dms/solr-document', editData)
}

/** Die Seiten eines Dokuments: Details, Bearbeiten, Viewer. In der URL steht nur die id. */
export function dmsSolrDocumentPath(id: string): string {
  return `/dms/solr-document/${id}`
}

export function dmsSolrDocumentEditPath(id: string): string {
  return `/dms/solr-document/${id}/edit`
}

export function dmsSolrDocumentViewerPath(id: string): string {
  return `/dms/solr-document/${id}/viewer`
}

/** Der Status, den das weiche Löschen setzt (DmsStateService.DELETED) */
export const DMS_STATE_DELETED = 'DELETED'
export const DMS_STATE_ACTIVE = 'ACTIVE'
