import { request, upload, type UploadProgress } from '../../api/client.ts'
import type {
  SolrAdvancedFieldGrantAccessPerCore,
  SolrDocumentGrantedDocsPerCore,
  SolrHookGrantAccess,
} from '../grant/Grant.ts'
import type { SearchRequest, SearchResultPage, SearchSuggestResult } from '../search/Search.ts'
import { clearSearchResultCache } from '../search/searchResultCache.ts'
import { SOLR_CORE_BUILT_IN_FIELD_NAMES } from '../solrmanager/SolrCore.ts'
import { isClientAllowedToSetOnCreate, solrFieldDisplayNameOf, type SolrField } from '../solrmanager/SolrField.ts'
import type { LanguageCode } from '../../branding/languages.ts'

/** Die AR, die das DMS-Modul sichtbar und den Upload erlaubt macht. Das Backend prüft dieselbe. */
export const DMS_ACCESS_ROLE_KEY = 'DMS'

/** Die AR der Suche im Kern DMS, dazu immer DMS. Ohne sie 403 vor Solr, auch für den Admin. */
export const DMS_SEARCH_ACCESS_ROLE_KEY = 'DMS_SUCHE'

/**
 * Die AR der Internetfreigabe (User 2026-10-08): isPublic setzt und ändert nur, wer sie hat, beim Upload, beim
 * Bearbeiten und beim Zurückladen. Ohne sie zeigen die Masken den Haken nicht, das Backend lehnt mit 403 ab.
 */
export const DMS_PUBLIC_ACCESS_ROLE_KEY = 'DMS_PUBLIC'

/** Der Kern des DMS im SolrManager (DmsSolrDocumentFieldNames.CORE_KEY). Seine Felder aus den Stammdaten sind die Maske. */
export const DMS_CORE_KEY = 'DMS'

// ===== Die Felder des Kerns DMS, mit denen die Masken rechnen (DmsSolrDocumentFieldNames.java) =====

/** Das eine Feld des Kerns, das die Dateiart trägt: in der Maske eine Auswahl aus den Dateiarten, kein freier Text */
export const FILE_SUB_CLASS_FIELD_NAME = 'fileSubClassKey'

/**
 * versionChain: Nur im Sonderfall „neue Fassung“ schickt das Frontend die Kette der Originaldatei mit, nie der User
 * von Hand. Bei einer neuen Datei bleibt das Feld leer, das Backend vergibt beim Speichern eine zufällige UUID.
 */
export const VERSION_CHAIN_FIELD_NAME = 'versionChain'

/** fileSizeBytes liest das Frontend aus der gewählten Datei und schickt es mit, das Backend prüft es gegen die Datei */
export const FILE_SIZE_FIELD_NAME = 'fileSizeBytes'

/**
 * status: in Solr ein string, die Werte sind die EntityStates des Bereichs DMS aus den Stammdaten (DmsStateService
 * legt sie beim Start an). Später wählt ihn der User nicht mehr, die Masken je Fall setzen ihn.
 */
export const STATUS_FIELD_NAME = 'status'
export const DMS_STATE_SCOPE = 'DMS'

/** Das Feld der Sprache im Kern DMS, in Solr ein string mit dem ISO-639-1-Code */
export const DOCUMENT_LANGUAGE_FIELD_NAME = 'documentLanguage'

/**
 * Solrs _version_, der Zähler je Dokument, den Solr bei jedem Schreiben selbst setzt. Kommt mit jeder Leseantwort als
 * Text, weil die Zahl 19 Stellen hat und JavaScript sie verfälschen würde. Nur Anzeige, später die Revision; beim
 * Schreiben schickt die Maske ihn nie mit.
 */
export const SOLR_VERSION_FIELD_NAME = '_version_'

/** Wie die Upload-Maske ein Feld des Kerns DMS anbietet */
export type DmsFieldInput = 'backend' | 'chain' | 'fileSize' | 'fileSubClass' | 'status' | 'free'

/**
 * Was ein Feld in der Upload-Maske ist, aus der Schreibregel der Felddefinition (Vorgabe 2026-10-07): BACKEND_ONLY und
 * die eingebauten Felder stehen gesperrt in der Liste, die Sonderfelder haben ihre eigene Eingabe, der Rest ist frei.
 */
export function dmsFieldInput(solrField: SolrField): DmsFieldInput {
  if (!isClientAllowedToSetOnCreate(solrField.clientWriteRule) || SOLR_CORE_BUILT_IN_FIELD_NAMES.includes(solrField.name)) {
    return 'backend'
  }
  switch (solrField.name) {
    case VERSION_CHAIN_FIELD_NAME:
      return 'chain'
    case FILE_SIZE_FIELD_NAME:
      return 'fileSize'
    case FILE_SUB_CLASS_FIELD_NAME:
      return 'fileSubClass'
    case STATUS_FIELD_NAME:
      return 'status'
    default:
      return 'free'
  }
}

/**
 * Die Beschriftung eines Feldes des Kerns DMS in den Masken (User 2026-10-07): der Anzeigename aus der Felddefinition,
 * wenn der Admin einen eingetragen hat, sonst die Beschriftung aus dem Sprachkatalog. Die Stammdaten sind null, solange
 * sie laden, dann gilt der Rückfall.
 */
export function dmsFieldLabel(
  solrFields: readonly SolrField[] | null,
  name: string,
  fallback: string,
  language?: LanguageCode,
): string {
  return solrFieldDisplayNameOf(solrFields ?? [], DMS_CORE_KEY, name, language) ?? fallback
}

/** Bytes lesbar: 1,2 MB statt 1234567. Infobox, Trefferzeile der Suche. */
export function readableBytes(value: unknown): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return ''
  }
  const units = ['B', 'kB', 'MB', 'GB']
  let amount = value
  let unit = 0
  while (amount >= 1000 && unit < units.length - 1) {
    amount /= 1000
    unit += 1
  }
  return `${unit === 0 ? amount : amount.toFixed(1)} ${units[unit]}`
}

/** Die Endung nach derselben Regel wie im Backend (DmsUploadService.extensionOf): nach dem letzten Punkt, klein. Ohne Punkt leer. */
export function fileExtensionOf(fileName: string): string {
  const dot = fileName.lastIndexOf('.')
  return dot < 0 || dot === fileName.length - 1 ? '' : fileName.slice(dot + 1).toLowerCase()
}


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
 * Was der Upload tat (DmsUploadService.UploadStatus): CREATED neu angelegt (neue Datei oder neue Fassung), MERGED
 * dieselben Bytes lagen schon in dieser Dateiart und bekamen Hooks und Beschreibung dazu, EXISTS sie liegen unter einer
 * anderen Dateiart oder mit einem Status ohne Merge, nichts geändert.
 */
export type DmsUploadStatus = 'CREATED' | 'MERGED' | 'EXISTS'

/**
 * Das Ergebnis des Uploads: was passiert ist, die id, das Dokument mit der Lesegrenze des Users (null bei EXISTS und
 * wenn er es nicht sehen darf, denn Anlegen braucht kein Lesen), dazu die Probe
 */
export interface DmsUploadResult {
  status: DmsUploadStatus
  id: string
  document: Record<string, unknown> | null
  probe: DmsUploadProbe
}

/**
 * Der Existenzcheck (DmsSolrDocumentExistenceCheck.java): Gibt es diese Bytes schon im DMS? Nur id, Dateiart und
 * Status, ohne Rechteprüfung. Merge nur bei gleicher Dateiart und Status ACTIVE oder ARCHIVED.
 */
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
  // ein Schreibvorgang: Die Treffer der Suche im Zwischenspeicher sind danach alt
  clearSearchResultCache()
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

// ===== Endpunkte des DmsSearchController: die Suche im Kern DMS =====

/**
 * Die Suche: Tür DMS und DMS_SUCHE, Status ACTIVE oder ARCHIVED, alle Felder außer dem Volltext, mit Treffergrund.
 * Filter sind Chips (Feld, Wert), die das Backend gegen seine feste Liste prüft (DmsSearchService.EXACT_FILTER_FIELD_NAMES
 * plus Hooks), fremde überliest es.
 */
export function searchDmsDocuments(searchRequest: SearchRequest) {
  return request<SearchResultPage>('POST', '/api/rest/v1/dms/search', searchRequest)
}

/** Die Vervollständigung beim Tippen: dieselbe Tür, Treffer über die Suggested-Zwillinge und die Filterangebote. Seite und Größe ignoriert das Backend. */
export function suggestDmsDocuments(searchRequest: SearchRequest) {
  return request<SearchSuggestResult>('POST', '/api/rest/v1/dms/search/suggest', searchRequest)
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
 * Der Patch der Bearbeiten-Maske (DmsSolrDocumentPatchRequest.java, Vorgabe 2026-10-07): nur die Felder, die sich
 * geändert haben, je Feld der Endwert, wie er jetzt sein soll, Hooks flach als fertige Liste. null, ein leerer Text oder
 * eine leere Liste leert das Feld. Felder, die der Client nicht ändern darf, ignoriert das Backend.
 */
export interface DmsSolrDocumentPatchData {
  id: string
  fieldValuesByFieldName: Record<string, unknown>
}

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

/** Was dem current user freigegeben ist (DmsCurrentUsersGrantsForClient.java), Register mit allen Status */
export interface DmsCurrentUsersGrantsForClient {
  documentGrants: SolrDocumentGrantedDocsPerCore[]
  fieldGrants: SolrAdvancedFieldGrantAccessPerCore[]
  /** serverweit, nicht nur im DMS */
  hookGrants: SolrHookGrantAccess[]
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

export function patchDmsSolrDocument(patchData: DmsSolrDocumentPatchData) {
  clearSearchResultCache()
  return request<DmsSolrDocumentForClient>('PATCH', '/api/rest/v1/dms/solr-document', patchData)
}

/** Weiches Löschen: Status DELETED, die Datei bleibt. Danach sieht der User das Dokument auf keinem Weg mehr. */
export function softDeleteDmsSolrDocument(id: string) {
  clearSearchResultCache()
  return request<void>('DELETE', '/api/rest/v1/dms/solr-document', { id })
}

// ===== Revisionen (DmsSolrDocumentRevisionService): der Stand vor jeder Änderung durch einen Menschen =====

/**
 * Eine Zeile der Revisionsliste (DmsSolrDocumentRevisionForClient.java), allein aus dem Register in Postgres: Die
 * Zeile ist der gesicherte Stand und zugleich die Änderung, die ihn abgelöst hat, Zeitpunkt, User und Felder gehören
 * zu dieser Änderung, changedFieldNames sind die Felder, deren Wert sich wirklich geändert hat. Revision 0 heißt: so
 * wurde es hochgeladen.
 */
export interface DmsSolrDocumentRevision {
  revisionNo: number
  changedAt: string
  changedByUsersGuid: string
  changedByUsersDisplayName: string
  changedFieldNames: string[]
}

/** Die Revisionen eines Dokuments, neueste zuerst. 404, wenn der User das Dokument nicht sehen darf. */
export function getDmsSolrDocumentRevisions(id: string) {
  return request<DmsSolrDocumentRevision[]>('GET', `/api/rest/v1/dms/solr-document/${id}/revisions`)
}

/** Eine Revision als Dokument, wie das Backend es aus dem ZIP liest, mit der Feldgrenze des Users wie die Dokumentseite */
export function getDmsSolrDocumentRevision(id: string, revisionNo: number) {
  return request<Record<string, unknown>>('GET', `/api/rest/v1/dms/solr-document/${id}/revision/${revisionNo}`)
}

/**
 * Zurückladen: nur id und Nummer, kein Feldwert von hier, das Backend liest sein eigenes ZIP und schreibt jedes Feld
 * zurück. Vorher sichert es den heutigen Stand selbst als Revision. null, wenn der User das Dokument danach nicht mehr
 * sehen darf, denn das ZIP kann alte Zugriffsrechte zurückbringen.
 */
export function restoreDmsSolrDocumentRevision(id: string, revisionNo: number) {
  clearSearchResultCache()
  return request<DmsSolrDocumentForClient | null>('POST', '/api/rest/v1/dms/solr-document/revision/restore', {
    id,
    revisionNo,
  })
}

// ===== Versionskette (resolveDmsVersionChainForClient): der Fahrplan und die neue Fassung =====

/**
 * Die Versionskette: alle Fassungen, die der User sehen darf, mit seiner Feldgrenze, aufsteigend nach version, wenn er
 * das Feld lesen darf. Leer, wenn es die Kette nicht gibt oder er nichts davon sieht.
 */
export function getDmsVersionChain(versionChain: string) {
  return request<Record<string, unknown>[]>('GET', `/api/rest/v1/dms/version-chain/${versionChain}`)
}

/** Die Nummer der Fassung als Zahl, 0 wenn das Feld fehlt oder der User es nicht lesen darf */
export function dmsVersionOf(document: Record<string, unknown>): number {
  const version = document.version
  return typeof version === 'number' ? version : 0
}

/** Der Kopf einer Kette: die ACTIVE-Fassung mit der höchsten Nummer, ohne ACTIVE die höchste; null bei leerer Kette */
export function dmsVersionChainHead(members: readonly Record<string, unknown>[]): Record<string, unknown> | null {
  let head: Record<string, unknown> | null = null
  for (const member of members) {
    if (head === null) {
      head = member
      continue
    }
    const headActive = head[STATUS_FIELD_NAME] === DMS_STATE_ACTIVE
    const memberActive = member[STATUS_FIELD_NAME] === DMS_STATE_ACTIVE
    if (memberActive !== headActive) {
      if (memberActive) {
        head = member
      }
      continue
    }
    if (dmsVersionOf(member) > dmsVersionOf(head)) {
      head = member
    }
  }
  return head
}

/** Der feste Verweis auf eine Datei über ihre Kette: landet immer bei der geltenden Fassung, auch nach neuen Fassungen */
export function dmsVersionChainPath(versionChain: string): string {
  return `/dms/version-chain/${versionChain}`
}

/** Die Seiten eines Dokuments: Details, Bearbeiten, Viewer. In der URL steht nur die id. */
export function dmsSolrDocumentPath(id: string): string {
  return `/dms/solr-document/${id}`
}

export function dmsSolrDocumentEditPath(id: string): string {
  return `/dms/solr-document/${id}/edit`
}

/** Der Viewer liegt außerhalb des DMS-Moduls, ohne dessen Tabs, die Datei bekommt die ganze Fläche */
export function dmsSolrDocumentViewerPath(id: string): string {
  return `/dms/viewer/${id}`
}

/** Der Status, den das weiche Löschen setzt (DmsStateService.DELETED) */
export const DMS_STATE_DELETED = 'DELETED'
export const DMS_STATE_ACTIVE = 'ACTIVE'
