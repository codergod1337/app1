import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { download, request, RequestError, upload, type DownloadProgress, type UploadProgress } from '../../api/client.ts'
import { useUsersSettings } from '../../components/usersSettingsContext.ts'
import type { SearchRequest, SearchResultPage } from '../search/Search.ts'
import { clearSearchResultCache } from '../search/searchResultCache.ts'
import { SOLR_CORE_BUILT_IN_FIELD_NAMES } from '../solrmanager/SolrCore.ts'
import { isClientAllowedToSetOnCreate, solrFieldDisplayNameOf, type SolrField } from '../solrmanager/SolrField.ts'
import type { LanguageCode } from '../../branding/languages.ts'

/** Die AR, die das DMS-Modul sichtbar und den Upload erlaubt macht. Das Backend prüft dieselbe. */
export const DMS_ACCESS_ROLE_KEY = 'DMS'

/**
 * Die AR der Suche im Kern DMS (User 2026-10-09): Sie gibt in der Suche alle Felder und Hooks frei, nur readAcl und
 * Status gelten weiter. Ohne sie 403 vor Solr, außer für ADMIN.
 */
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

/** file und data als JSON; steht versionChain in data, ist es ein Versionsupdate dieser Kette */
function uploadFormData(file: File, document: Record<string, unknown>): FormData {
  const formData = new FormData()
  formData.append('file', file, file.name)
  formData.append('data', JSON.stringify(document))
  return formData
}

/**
 * Die Probe (FileDataController): Datei und Felder ans Backend, das prüft, mit Tika liest und die Vorschau rechnet,
 * aber nichts speichert. document trägt jedes Feld unter seinem Solr-Namen, multiValued als Liste, Hooks genauso.
 */
export function probeDmsUpload(file: File, document: Record<string, unknown>) {
  return upload<DmsUploadProbe>('/api/rest/v1/filedata/upload-probe', uploadFormData(file, document))
}

/**
 * Der Upload (FileDataController): wie die Probe, dann speichern (SFTP, Solr). Die Dateiart ist Pflicht, der User
 * braucht ihr Schreibrecht. Ein normaler Upload schickt nie versionChain, das Backend vergibt eine neue Kette; steht
 * versionChain im Dokument, ist es ein Versionsupdate: Das Backend nimmt den aktiven Kopf der Kette als abgelöste
 * Fassung (User 2026-10-10). onProgress meldet die gesendeten Bytes, nach 100 % arbeitet das Backend noch.
 */
export function uploadDmsFile(file: File, document: Record<string, unknown>, onProgress?: UploadProgress) {
  // ein Schreibvorgang: Die Treffer der Suche im Zwischenspeicher sind danach alt
  clearSearchResultCache()
  return upload<DmsUploadResult>('/api/rest/v1/filedata/upload', uploadFormData(file, document), onProgress)
}

/** Gibt es diese Bytes schon? sha256 in Hex, klein, wie sha256Hex.ts ihn liefert. */
export function getDmsExistence(sha256: string) {
  return request<DmsExistence>('GET', `/api/rest/v1/filedata/exists/${sha256}`)
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
  return request<CurrentUserDmsAccessForClient>('GET', '/api/rest/v1/solr/dms/access')
}

/**
 * Auflösen über den SolrDocumentDmsController: ein Wert je Feld (der erste der Liste), Seiten ab 0 mit der Größe aus
 * der YAML des Backends; Sortierung und Größe der Maske nimmt das Backend heute nicht an. Die Antwort nennt die
 * Seitengröße pageSize, die Maske kennt sie als size.
 */
export function resolveDmsDocumentsPageByExactFieldValues(resolveRequest: DmsExactFieldValueResolveRequest) {
  const exactValueByFieldName: Record<string, string> = {}
  for (const [fieldName, values] of Object.entries(resolveRequest.fieldValuesByFieldName)) {
    if (values.length > 0) {
      exactValueByFieldName[fieldName] = String(values[0])
    }
  }
  return request<{ totalFound: number; page: number; pageSize: number; documents: Record<string, unknown>[] }>(
    'POST',
    '/api/rest/v1/solr/dms/resolve',
    { exactValueByFieldName, page: resolveRequest.page },
  ).then((result) => ({
    ...result,
    data: {
      totalFound: result.data.totalFound,
      page: result.data.page,
      size: result.data.pageSize,
      documents: result.data.documents,
    } satisfies DmsResolvedDocumentsPage,
  }))
}

/**
 * Der Weg zu den Bytes einer Datei für einen angemeldeten User (FileDataController): ein normaler Link, das Token
 * kommt aus dem Cookie. Das Backend prüft readAcl oder die guid-Freigabe, bevor es die Datei vom SFTP holt. Die
 * öffentlichen Wege (isPublic, Abholschein) liegen unter /api/rest/v1/public/filedata/.
 */
export function dmsFileDownloadUrl(id: string): string {
  return `/api/rest/v1/filedata/download/${id}`
}

/**
 * Die Bytes einer Datei als Blob für den Viewer, über denselben Weg mit derselben Rechteprüfung wie der Download-Link;
 * onProgress meldet die empfangenen Bytes. Der Viewer braucht die Bytes im Speicher, weil der Download-Endpunkt
 * attachment schickt (siehe download in api/client.ts).
 */
export function downloadDmsFileBlob(id: string, onProgress?: DownloadProgress) {
  return download(dmsFileDownloadUrl(id), onProgress)
}

/** Höchstens so oft wird der Fortschritt gezeichnet: Ein Stück je Netzwerkpaket wären tausende Renderläufe bei einer großen Datei */
const PROGRESS_REDRAW_INTERVAL_MS = 100

/** Was ein Viewer über die Bytes seiner Datei weiß: die Bytes, sobald da, bis dahin der Fortschritt, oder der Fehler */
export interface DmsFileBlobState {
  blob: Blob | null
  receivedBytes: number
  /** null ohne Content-Length */
  totalBytes: number | null
  errorMessage: string | null
}

/**
 * Die Bytes einer Datei für einen Viewer (User 2026-10-14: ansehen ohne Herunterladen): über denselben Weg mit derselben
 * Leseprüfung wie der Download, aber nur in den Speicher des Browsers, nie in den Download-Ordner. Beim Wechsel der id
 * wird die alte Antwort verworfen. Alle Viewer laden damit, PDF, Text, Word und Excel.
 */
export function useDmsFileBlob(id: string): DmsFileBlobState {
  const { t } = useTranslation()
  const [state, setState] = useState<DmsFileBlobState>({ blob: null, receivedBytes: 0, totalBytes: null, errorMessage: null })
  const lastRedrawRef = useRef(0)

  useEffect(() => {
    let cancelled = false
    setState({ blob: null, receivedBytes: 0, totalBytes: null, errorMessage: null })
    lastRedrawRef.current = 0
    downloadDmsFileBlob(id, (receivedBytes, totalBytes) => {
      const now = performance.now()
      if (cancelled || now - lastRedrawRef.current < PROGRESS_REDRAW_INTERVAL_MS) {
        return
      }
      lastRedrawRef.current = now
      setState((previous) => ({ ...previous, receivedBytes, totalBytes }))
    })
      .then(({ data }) => {
        if (!cancelled) {
          setState((previous) => ({ ...previous, blob: data }))
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setState((previous) => ({ ...previous, errorMessage: error instanceof RequestError ? error.message : t('common.unknownError') }))
        }
      })
    return () => {
      cancelled = true
    }
    // neu laden, wenn eine andere id kommt
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  return state
}

// ===== Endpunkte des SolrDocumentDmsController (solr/dms): die Suche im Kern DMS =====

/**
 * Die Suche: Tür DMS_SEARCH oder ADMIN, Status ACTIVE oder ARCHIVED, alle Felder und Hooks, mit Treffergrund und
 * Vorschau. Chips sind exakte Filter je Feld, gleiches Feld heißt ODER. Nur die besten Treffer, keine Seiten.
 */
export function searchDmsDocuments(searchRequest: SearchRequest) {
  return request<SearchResultPage>('POST', '/api/rest/v1/solr/dms/search', searchRequest)
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

/** Meine Freigaben: die Dokumente, die meine guid in userGrantedAccess tragen, seitenweise ab 0, Größe aus dem Backend */
export function getDmsDocumentsGrantedToMe(page: number) {
  return request<{ totalFound: number; page: number; pageSize: number; documents: Record<string, unknown>[] }>(
    'GET',
    `/api/rest/v1/solr/dms/granted-to-me?page=${page}`,
  ).then((result) => ({
    ...result,
    data: {
      totalFound: result.data.totalFound,
      page: result.data.page,
      size: result.data.pageSize,
      documents: result.data.documents,
    } satisfies DmsResolvedDocumentsPage,
  }))
}

export function getDmsSolrDocument(id: string) {
  return request<DmsSolrDocumentForClient>('GET', `/api/rest/v1/solr/dms/document/${id}`)
}

export function patchDmsSolrDocument(patchData: DmsSolrDocumentPatchData) {
  clearSearchResultCache()
  return request<DmsSolrDocumentForClient>('PATCH', '/api/rest/v1/solr/dms/document', patchData)
}

/** Weiches Löschen: Status DELETED, die Datei bleibt. Danach sieht der User das Dokument auf keinem Weg mehr. */
export function softDeleteDmsSolrDocument(id: string) {
  clearSearchResultCache()
  return request<void>('DELETE', '/api/rest/v1/solr/dms/document', { id })
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
  /** nur die guid, den Namen löst UsersGuidName auf (User 2026-10-10) */
  changedByUsersGuid: string
  changedFieldNames: string[]
}

/** Die Revisionen eines Dokuments, neueste zuerst. 404, wenn der User das Dokument nicht sehen darf. */
export function getDmsSolrDocumentRevisions(id: string) {
  return request<DmsSolrDocumentRevision[]>('GET', `/api/rest/v1/solr/dms/document/${id}/revisions`)
}

/** Eine Revision als Dokument, wie das Backend es aus dem ZIP liest, mit der Feldgrenze des Users wie die Dokumentseite */
export function getDmsSolrDocumentRevision(id: string, revisionNo: number) {
  return request<Record<string, unknown>>('GET', `/api/rest/v1/solr/dms/document/${id}/revision/${revisionNo}`)
}

/**
 * Zurückladen: nur id und Nummer, kein Feldwert von hier, das Backend liest sein eigenes ZIP und schreibt jedes Feld
 * zurück. Vorher sichert es den heutigen Stand selbst als Revision. null, wenn der User das Dokument danach nicht mehr
 * sehen darf, denn das ZIP kann alte Zugriffsrechte zurückbringen.
 */
export function restoreDmsSolrDocumentRevision(id: string, revisionNo: number) {
  clearSearchResultCache()
  return request<DmsSolrDocumentForClient | null>('POST', '/api/rest/v1/solr/dms/document/revision/restore', {
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
  return request<Record<string, unknown>[]>('GET', `/api/rest/v1/solr/dms/version-chain/${versionChain}`)
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

// ===== Die kontextgebundene Dokumentenliste je Dateiart (DmsFileSubClassDocumentsList), nach DmsFscDokumente der Vorlage =====

/**
 * Die drei Ansichten (User 2026-10-10): ALL zeigt jede Dateiart des Orts in Katalogreihenfolge, die Vorgabe;
 * WITH_FILES nur die Dateiarten mit Dateien, in der eigenen Reihenfolge, am Griff ziehbar; FAVORITES jede Dateiart in
 * der eigenen Reihenfolge, ziehbar und je Abschnitt auf- oder zuklappbar. Der Klick auf den Umschalter geht im Kreis.
 */
export const DMS_DOCUMENT_LIST_MODES = ['ALL', 'WITH_FILES', 'FAVORITES'] as const

export type DmsDocumentListMode = (typeof DMS_DOCUMENT_LIST_MODES)[number]

/** Die Ansicht aus einer UsersSetting; alles Unbekannte ist ALL, die Vorgabe */
export function dmsDocumentListModeOf(value: string | null): DmsDocumentListMode {
  return (DMS_DOCUMENT_LIST_MODES as readonly string[]).includes(value ?? '') ? (value as DmsDocumentListMode) : 'ALL'
}

export function nextDmsDocumentListMode(mode: DmsDocumentListMode): DmsDocumentListMode {
  return DMS_DOCUMENT_LIST_MODES[(DMS_DOCUMENT_LIST_MODES.indexOf(mode) + 1) % DMS_DOCUMENT_LIST_MODES.length]
}

/** Die Ansicht eines Orts aus seiner UsersSetting (z. B. ARTIKELMAPPE_DATENTYPEN), ALL ohne eine */
export function useDmsDocumentListMode(settingKey: string): DmsDocumentListMode {
  const { settingValue } = useUsersSettings()
  return dmsDocumentListModeOf(settingValue(settingKey))
}

/**
 * Die eigene Reihenfolge der Dateiarten, eine UsersSetting mit einer JSON-Liste ihrer Keys: die Stelle in der Liste ist
 * die Position. Projektweit eine Liste; jeder Ort nimmt daraus seine Dateiarten, was er nicht kennt, bleibt hinten stehen.
 */
export const DMS_FILE_SUB_CLASS_ORDER_SETTING_KEY = 'DMS_FSC_ORDER'

/** Die zugeklappten Dateiarten der Favoriten-Ansicht, eine UsersSetting mit einer JSON-Liste ihrer Keys */
export const DMS_FILE_SUB_CLASS_COLLAPSED_SETTING_KEY = 'DMS_FSC_COLLAPSED'

/** Die Keys aus einer solchen UsersSetting; null, wenn es sie nicht gibt oder sie unlesbar ist */
export function fileSubClassKeysOfSetting(value: string | null): string[] | null {
  if (value === null) {
    return null
  }
  try {
    const parsed: unknown = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.filter((entry): entry is string => typeof entry === 'string') : null
  } catch {
    return null
  }
}
