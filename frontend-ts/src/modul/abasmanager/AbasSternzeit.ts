import { request, uploadWithResponseLines, type UploadProgress } from '../../api/client.ts'

/** Das Modul AbasManager, Eintritt mit AR ADMIN */
export const ABAS_MANAGER_PATH = '/abasmanager'

/** So lang darf eine Sternzeit höchstens sein, das Spaltenmaß im Backend */
export const STERNZEIT_MAX_LENGTH = 30

/** Die volle Klammerform (Nummer,Datenbank,Gruppe), und nur sie: dieselbe Regel wie im Backend in AbasSternzeit.isValidSternzeit */
const STERNZEIT_PATTERN = /^\(\d+,\d+,\d+\)$/

export function isValidSternzeit(text: string): boolean {
  return text.length <= STERNZEIT_MAX_LENGTH && STERNZEIT_PATTERN.test(text)
}

/**
 * Ein Stammsatz, wie das Backend ihn für die Übersicht liefert (AbasSternzeitForClient.java): eine ABAS-Objektkennung,
 * hausintern Sternzeit, z. B. (87448,19,0).
 */
export interface AbasSternzeit {
  id: number
  sternzeit: string
  /** der Typ-String von ABAS, z. B. obj:data:2:1; null heißt noch nie geholt */
  objectType: string | null
  /** „im Kern seit“: wann ABAS die Kennung zum ersten Mal gepusht hat, geschrieben nur beim Anlegen */
  firstPushedAt: string
  /** wann zuletzt erfolgreich geholt; null heißt noch nie */
  lastSyncAt: string | null
  fetchCount: number
}

/** Eine Seite der Liste, geblättert per Keyset über die Kennung (AbasSternzeitPage.java) */
export interface AbasSternzeitPage {
  sternzeiten: AbasSternzeit[]
  /** der Anker für die nächste Seite; null bei leerer Seite */
  lastSternzeit: string | null
  hasMore: boolean
}

/** Die Kopfzahlen der Übersicht (AbasSternzeitOverviewCounts.java); die großen Zahlen ab einer halben Million geschätzt */
export interface AbasSternzeitOverviewCounts {
  sternzeitCount: number
  sternzeitCountEstimated: boolean
  sternzeitWithoutObjectTypeCount: number
  queueCount: number
  queueCountEstimated: boolean
  queueClaimedCount: number
  queueFailedCount: number
  ignoredCount: number
}

/** Was mit einer Kennung in der Queue geschehen soll: holen und in den Kern schreiben, oder im Kern auflösen */
export type AbasSternzeitQueueAction = 'FETCH' | 'DELETE'

/** Die Antwort auf ein Einreihen (AbasSternzeitQueueEnqueueResult.java): alreadyQueued heißt, sie stand schon so drin */
export interface AbasSternzeitQueueEnqueueResult {
  sternzeit: string
  action: AbasSternzeitQueueAction
  alreadyQueued: boolean
}

/** Der Zustand des Worker-Pools, die große Badge der Übersicht (AbasSternzeitFetcherState.java) */
export type AbasSternzeitFetcherState = 'RUNNING' | 'PAUSED' | 'NOT_CONFIGURED'

/** Was die Übersicht über den Worker-Pool zeigt (AbasSternzeitFetcherStatus.java); die Zähler laufen seit dem Start */
export interface AbasSternzeitFetcherStatus {
  state: AbasSternzeitFetcherState
  parallel: number
  busyWorkers: number
  queueCount: number
  queueCountEstimated: boolean
  processedCount: number
  failedCount: number
}

/** Wer einen Eintrag in die Queue gelegt hat: der Push des ERP, der Admin von Hand, der Import einer Datei */
export type AbasSternzeitQueueSource = 'ABAS' | 'ADMIN' | 'IMPORT'

/** Ein Eintrag der Warteschlange (AbasSternzeitQueueEntry.java) */
export interface AbasSternzeitQueueEntry {
  sternzeit: string
  action: AbasSternzeitQueueAction
  source: AbasSternzeitQueueSource
  queuedAt: string
  /** null heißt frei, gesetzt heißt ein Worker arbeitet daran */
  claimedAt: string | null
  attempts: number
  lastErrorMessage: string | null
}

/** Eine gesperrte Kennung (AbasIgnoredSternzeit.java) */
export interface AbasIgnoredSternzeit {
  id: number
  sternzeit: string
  reason: string | null
  ignoredAt: string
}

// ===== Endpunkte des AbasSternzeitQueueAdminController und des AbasIgnoredSternzeitAdminController =====

/** Die vordersten 50 in FIFO-Ordnung, was als Nächstes drankommt */
export function getAbasSternzeitQueueTop() {
  return request<AbasSternzeitQueueEntry[]>('GET', '/api/rest/v1/admin/abas/queue/top')
}

/** Eine Kennung sperren: 409, wenn sie schon gesperrt ist; ein wartender Queue-Eintrag wird entfernt */
export function ignoreAbasSternzeit(sternzeit: string, reason: string | null) {
  return request<AbasIgnoredSternzeit>('POST', '/api/rest/v1/admin/abas/ignored', { sternzeit, reason })
}

// ===== Endpunkte des AbasSternzeitFetcherAdminController =====

const ABAS_FETCHER_ADMIN_PATH = '/api/rest/v1/admin/abas/fetcher'

export function getAbasSternzeitFetcherStatus() {
  return request<AbasSternzeitFetcherStatus>('GET', ABAS_FETCHER_ADMIN_PATH)
}

/** Der Schalter: true schaltet das Holen ein, false pausiert. Zurück kommt der neue Zustand. */
export function switchAbasSternzeitFetcher(running: boolean) {
  return request<AbasSternzeitFetcherStatus>('POST', ABAS_FETCHER_ADMIN_PATH, { running })
}

// ===== Endpunkte des AbasSternzeitAdminController =====

const ABAS_STERNZEIT_ADMIN_PATH = '/api/rest/v1/admin/abas/sternzeit'

export function getAbasSternzeitOverviewCounts() {
  return request<AbasSternzeitOverviewCounts>('GET', `${ABAS_STERNZEIT_ADMIN_PATH}/counts`)
}

/** Die nächste Seite hinter dem Anker; null heißt von vorn */
export function getAbasSternzeitPage(afterSternzeit: string | null) {
  return request<AbasSternzeitPage>('POST', `${ABAS_STERNZEIT_ADMIN_PATH}/page`, { afterSternzeit })
}

/** Der Treffer per Kennung, 404 ohne Stammsatz */
export function findAbasSternzeit(sternzeit: string) {
  return request<AbasSternzeit>('POST', `${ABAS_STERNZEIT_ADMIN_PATH}/search`, { sternzeit })
}

/** Von Hand einreihen; 403, wenn die Kennung auf der Sperrliste steht */
export function enqueueAbasSternzeit(sternzeit: string, action: AbasSternzeitQueueAction) {
  return request<AbasSternzeitQueueEnqueueResult>('POST', `${ABAS_STERNZEIT_ADMIN_PATH}/queue`, { sternzeit, action })
}

/**
 * Die rohe Antwort von ABAS, live geholt (AbasSternzeitRawRequest.java): leere Feldlisten heißen alle Kopffelder
 * (headFields=*) und ohne tableFields; 404, wenn ABAS die Kennung nicht kennt, 503, wenn ABAS nicht antwortet
 */
export function fetchAbasSternzeitRaw(sternzeit: string, headFieldNames: string[], tableFieldNames: string[]) {
  return request<unknown>('POST', `${ABAS_STERNZEIT_ADMIN_PATH}/raw`, { sternzeit, headFieldNames, tableFieldNames })
}

// ===== Export und Import der Sternzeiten (User 2026-10-13), beide als Strom =====

/**
 * Eine Gruppe in einer Import-Datei (AbasSternzeitImportGroup.java): je Objekttyp eine, wenn die Datei ihn nennt, sonst
 * je Datenbank, der mittleren Zahl der Sternzeit, eine
 */
export interface AbasSternzeitImportGroup {
  objectType: string | null
  abasDatabaseNumber: number | null
  sternzeitCount: number
  exampleSternzeit: string
}

/** Was in einer Import-Datei steckt (AbasSternzeitImportCheck.java), auch als Zwischenstand während des Lesens */
export interface AbasSternzeitImportCheck {
  sternzeitCount: number
  invalidCount: number
  groups: AbasSternzeitImportGroup[]
}

/** Was der Import getan hat (AbasSternzeitImportResult.java), auch als Zwischenstand während des Laufs */
export interface AbasSternzeitImportResult {
  found: number
  filtered: number
  queued: number
  skipped: number
  ignored: number
}

/** Die Auswahl des Imports: Gruppen mit Typ über den Typ, Gruppen ohne Typ über die Datenbank */
export interface AbasSternzeitImportSelection {
  objectTypes: string[]
  abasDatabaseNumbers: number[]
}

/** Eine Zeile des Antwortstroms: Zwischenstand, Ergebnis oder Fehler */
type AbasSternzeitImportLine =
  | { kind: 'progress'; check?: AbasSternzeitImportCheck; result?: AbasSternzeitImportResult }
  | { kind: 'check'; check: AbasSternzeitImportCheck }
  | { kind: 'result'; result: AbasSternzeitImportResult }
  | { kind: 'error'; message: string }

/** Der Schlüssel einer Gruppe für die Auswahl: T und der Typ, D und die Datenbank */
export function abasSternzeitImportGroupKey(group: AbasSternzeitImportGroup): string {
  return group.objectType !== null ? `T${group.objectType}` : `D${group.abasDatabaseNumber ?? ''}`
}

/** Der Link des Exports: ein normaler Download, das Token kommt aus dem Cookie */
export function abasSternzeitExportUrl(): string {
  return `${ABAS_STERNZEIT_ADMIN_PATH}/export`
}

function asImportLine(line: unknown): AbasSternzeitImportLine | null {
  return typeof line === 'object' && line !== null && 'kind' in line ? (line as AbasSternzeitImportLine) : null
}

function fileFormData(file: File, selection?: AbasSternzeitImportSelection): FormData {
  const formData = new FormData()
  formData.append('file', file, file.name)
  if (selection !== undefined) {
    formData.append('data', JSON.stringify(selection))
  }
  return formData
}

/**
 * Prüfen: die Datei hochladen und lesen lassen, gespeichert wird nichts. onCheck bekommt die Zwischenstände, onUpload
 * die gesendeten Bytes. Zurück kommt der Endstand; eine Fehlerzeile des Servers wird ein Error mit ihrer Meldung.
 */
export async function checkAbasSternzeitImportFile(
  file: File,
  onCheck: (check: AbasSternzeitImportCheck) => void,
  onUpload?: UploadProgress,
): Promise<AbasSternzeitImportCheck> {
  // gesetzt im Callback: ohne die Angabe hielte TypeScript beide für immer null
  let final = null as AbasSternzeitImportCheck | null
  let error = null as string | null
  await uploadWithResponseLines(
    `${ABAS_STERNZEIT_ADMIN_PATH}/import/check`,
    fileFormData(file),
    (raw) => {
      const line = asImportLine(raw)
      if (line?.kind === 'progress' && line.check) {
        onCheck(line.check)
      } else if (line?.kind === 'check') {
        final = line.check
        onCheck(line.check)
      } else if (line?.kind === 'error') {
        error = line.message
      }
    },
    onUpload,
  )
  if (error !== null) {
    throw new Error(error)
  }
  if (final === null) {
    throw new Error('Die Antwort endete ohne Ergebnis.')
  }
  return final
}

/** Importieren: dieselbe Datei noch einmal, mit der Auswahl. Sonst wie das Prüfen. */
export async function importAbasSternzeitenFromFile(
  file: File,
  selection: AbasSternzeitImportSelection,
  onResult: (result: AbasSternzeitImportResult) => void,
  onUpload?: UploadProgress,
): Promise<AbasSternzeitImportResult> {
  // gesetzt im Callback: ohne die Angabe hielte TypeScript beide für immer null
  let final = null as AbasSternzeitImportResult | null
  let error = null as string | null
  await uploadWithResponseLines(
    `${ABAS_STERNZEIT_ADMIN_PATH}/import`,
    fileFormData(file, selection),
    (raw) => {
      const line = asImportLine(raw)
      if (line?.kind === 'progress' && line.result) {
        onResult(line.result)
      } else if (line?.kind === 'result') {
        final = line.result
        onResult(line.result)
      } else if (line?.kind === 'error') {
        error = line.message
      }
    },
    onUpload,
  )
  if (error !== null) {
    throw new Error(error)
  }
  if (final === null) {
    throw new Error('Die Antwort endete ohne Ergebnis.')
  }
  return final
}

// ===== Endpunkte des AbasSternzeitMappingAdminController =====

/**
 * Die Werte je Key aus dem Mapping samt Subfetches (ResponseMappingExecutionResult.java); jeder Wert eine Liste,
 * Lücken null, eine Tabelle je Zeile ein JSON-Objekt als Text. warnings: was beim Lauf nicht ging, ohne ihn zu
 * stoppen, je Subfetch Key und Grund (syncStatus WARN im Dokument).
 */
export interface ResponseMappingExecutionResult {
  responseMappingKey: string
  responseObjectType: string
  targetKey: string | null
  primaryIdKey: string | null
  valuesByKey: Record<string, unknown[]>
  warnings: string[]
}

/** Was der Admin sieht, wenn er eine Sternzeit mappt (AbasSternzeitMappingPreview.java): document null ohne Schreibweg */
export interface AbasSternzeitMappingPreview {
  result: ResponseMappingExecutionResult
  document: Record<string, unknown> | null
  written: boolean
}

/** Holen, mappen, das Dokument zeigen, nichts schreiben; 404 ohne passendes Mapping */
export function previewAbasSternzeitMapping(sternzeit: string) {
  return request<AbasSternzeitMappingPreview>('POST', `${ABAS_STERNZEIT_ADMIN_PATH}/mapping/preview`, { sternzeit })
}

/** Dasselbe, und das Dokument wird in den Kern geschrieben */
export function writeAbasSternzeitMapping(sternzeit: string) {
  return request<AbasSternzeitMappingPreview>('POST', `${ABAS_STERNZEIT_ADMIN_PATH}/mapping/write`, { sternzeit })
}

// ===== Endpunkt des AbasErpAdminController =====

/** Die Basis jeder Anfrage an ABAS, https://host:port/mw/r/mandant; null, wenn ABAS nicht eingerichtet ist */
export interface AbasErpBaseUrl {
  baseUrl: string | null
}

export function getAbasErpBaseUrl() {
  return request<AbasErpBaseUrl>('GET', '/api/rest/v1/admin/abas/erp/base-url')
}
