import type { TFunction } from 'i18next'
import { request } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import type { OriginState } from '../../components/Origin.ts'
import type { SessionInfo } from '../login/Session.ts'

/** Der Kern der Jobs (SolrDocumentTodojobService.CORE_KEY) */
export const TODOJOB_CORE_KEY = 'TODOJOB'

/** Der Bereich der Status in entity_state */
export const TODOJOB_STATUS_SCOPE = 'TODOJOB'

/** Die Seite eines Jobs: /todojob/4711, die laufende Nummer ist eine Long-id */
export const TODOJOB_PATH = '/todojob'

export function todojobPath(id: string): string {
  return `${TODOJOB_PATH}/${encodeURIComponent(id)}`
}

/** Beim Öffnen eines Jobs vom Schreibtisch: der header bietet „zurück zu Profil“ */
export const TODOJOB_DESK_ORIGIN_STATE: OriginState = { origin: { path: '/profile', label: 'Profil' } }

/** Die fünf Status (TodojobStatus.java), Namen des Users 2026-10-16 */
export type TodojobStatus = 'OFFEN' | 'CLAIMED' | 'IN_BEARBEITUNG' | 'FERTIG' | 'ABGESCHLOSSEN'

/** Der Ausgang eines abgeschlossenen Jobs (TodojobAusgang.java) */
export type TodojobAusgang = 'GUT_GEMACHT' | 'VERFALLEN' | 'ZURUECKGEZOGEN'

/** Die Art eines Eintrags in der Rückmeldung (TodojobRueckmeldungEntryType.java) */
export type TodojobRueckmeldungEntryType =
  | 'LESEN'
  | 'BOOL'
  | 'TEXT'
  | 'ZAHL'
  | 'DATUM'
  | 'DMS_DATEI'
  | 'ARTIKELNUMMER'
  | 'STERNZEIT'
  | 'COMMENT'
  | 'RETRACTED'

/** Die Arten, die ein Job als Rückmeldung verlangen kann, in dieser Reihenfolge in der Auswahl */
export const TODOJOB_RUECKMELDUNGS_ARTEN: readonly TodojobRueckmeldungEntryType[] = [
  'LESEN',
  'TEXT',
  'BOOL',
  'ZAHL',
  'DATUM',
  'DMS_DATEI',
  'ARTIKELNUMMER',
  'STERNZEIT',
]

/** Die Arten, die als value eine id eines anderen Kerns tragen */
export const TODOJOB_REFERENCE_ENTRY_TYPES: readonly TodojobRueckmeldungEntryType[] = ['DMS_DATEI', 'ARTIKELNUMMER', 'STERNZEIT']

const ALL_ENTRY_TYPES: readonly TodojobRueckmeldungEntryType[] = [...TODOJOB_RUECKMELDUNGS_ARTEN, 'COMMENT', 'RETRACTED']

const ALL_AUSGAENGE: readonly TodojobAusgang[] = ['GUT_GEMACHT', 'VERFALLEN', 'ZURUECKGEZOGEN']

/** Der Name einer Eintragsart aus dem Sprachkatalog; ein unbekannter Wert aus Solr bleibt, wie er ist */
export function todojobEntryTypeLabel(t: TFunction, type: string): string {
  return (ALL_ENTRY_TYPES as readonly string[]).includes(type) ? t(`todojob.entryType.${type as TodojobRueckmeldungEntryType}`) : type
}

/** Der Name eines Ausgangs aus dem Sprachkatalog; ein unbekannter Wert bleibt, wie er ist */
export function todojobAusgangLabel(t: TFunction, ausgang: string): string {
  return (ALL_AUSGAENGE as readonly string[]).includes(ausgang) ? t(`todojob.ausgang.${ausgang as TodojobAusgang}`) : ausgang
}

/** Die drei Stapel des Schreibtischs (TodojobListKind.java) */
export type TodojobListKind = 'FOR_ME' | 'FROM_ME' | 'DONE'

/** Die Aktionen im Log (TodojobLogAction.java) */
export type TodojobLogAction =
  | 'CREATED'
  | 'READ'
  | 'CLAIMED'
  | 'CLAIM_RELEASED'
  | 'CLAIM_TAKEN'
  | 'QUESTION'
  | 'ANSWER'
  | 'RESPONDED'
  | 'RETURNED'
  | 'CONFIRMED'
  | 'EXPIRED'
  | 'WITHDRAWN'
  | 'DUE_DATE_CHANGED'
  | 'LABELS_CHANGED'
  | 'COMMENT'

/** Ein Job, wie Solr ihn liefert: die Feldnamen des Kerns, Listen als Arrays, Zeitpunkte als ISO-Text */
export type TodojobDocument = Record<string, unknown>

/** Ein Eintrag einer der Listen rueckmeldung, rueckfragen, rueckantworten, log: immer at und user, der Rest je Art */
export interface TodojobEntry {
  at: string
  user: string
  nr?: number
  type?: TodojobRueckmeldungEntryType
  text?: string
  value?: string
  action?: TodojobLogAction
  /** RETRACTED: welche nr zurückgenommen wurde */
  retracts?: number
  /** RETURNED: bis zu welcher nr die Rückmeldung abgelehnt war */
  rueckmeldungBis?: number
  /** RETURNED: der alte Stand von aufgabe und kontext, wenn der Owner sie geändert hat */
  snapshot?: { aufgabe?: string; kontext?: string[] }
  /** CLAIM_TAKEN: von wem der Claim übernommen wurde */
  from?: string
}

/** Was der User im Kern darf (TodojobAccessForClient.java) */
export interface TodojobAccessForClient {
  coreKey: string
  everyFieldAndHookOfCoreReadableByCurrentUser: boolean
  readableFieldAndHookNamesOfCoreForCurrentUser: string[]
  rueckmeldungsArten: TodojobRueckmeldungEntryType[]
}

/** Eine Seite Jobs (TodojobResolvedDocumentsPage.java) */
export interface TodojobResolvedDocumentsPage {
  totalFound: number
  page: number
  pageSize: number
  documents: TodojobDocument[]
}

/** Bei wie vielen Jobs der Ball beim User liegt (TodojobWaitingCounts.java) */
export interface TodojobWaitingCounts {
  servantCount: number
  ownerCount: number
}

/** Erteilen (TodojobCreateRequest.java) */
export interface TodojobCreateData {
  verb: string
  objekt: string
  aufgabe: string | null
  kontext: string[]
  rueckmeldungsArt: TodojobRueckmeldungEntryType
  bestaetigungNoetig: boolean
  dauerhaftRelevant: boolean
  displayContextKeys: string[]
  ownerAccessRoleKeys: string[]
  ownerArcKeys: string[]
  servantGuids: string[]
  servantAccessRoleKeys: string[]
  servantArcKeys: string[]
  /** ISO-8601 oder null */
  fristAm: string | null
  hookValuesByHookKey: Record<string, string[]>
}

// ===== Werte eines Jobs =====

/** Eine Liste, egal ob Solr einen Wert oder mehrere liefert; leer bei nichts */
export function todojobValuesOf(value: unknown): string[] {
  if (value === null || value === undefined) {
    return []
  }
  return (Array.isArray(value) ? value : [value]).map((entry) => String(entry))
}

/** Der erste Wert eines Feldes als Text, leer bei nichts */
export function todojobValueOf(job: TodojobDocument, fieldName: string): string {
  const values = todojobValuesOf(job[fieldName])
  return values.length === 0 ? '' : values[0]
}

/** Eine Zahl aus einem Feld, 0 wenn es fehlt */
export function todojobNumberOf(job: TodojobDocument, fieldName: string): number {
  const number = Number(todojobValueOf(job, fieldName))
  return Number.isFinite(number) ? number : 0
}

/** Die Einträge eines Listenfelds, in Reihenfolge; ein Eintrag, der kein JSON ist, fehlt */
export function todojobEntriesOf(job: TodojobDocument, fieldName: string): TodojobEntry[] {
  const entries: TodojobEntry[] = []
  for (const text of todojobValuesOf(job[fieldName])) {
    try {
      const parsed: unknown = JSON.parse(text)
      if (typeof parsed === 'object' && parsed !== null && 'at' in parsed && 'user' in parsed) {
        entries.push(parsed as TodojobEntry)
      }
    } catch {
      // kaputter Eintrag: bricht die Seite nicht
    }
  }
  return entries
}

/** Die nr aller zurückgenommenen Einträge der Rückmeldung */
export function todojobRetractedNumbers(entries: readonly TodojobEntry[]): Set<number> {
  const retracted = new Set<number>()
  for (const entry of entries) {
    if (entry.type === 'RETRACTED' && entry.retracts !== undefined) {
      retracted.add(entry.retracts)
    }
  }
  return retracted
}

/** Die Antwort des Owners zur Rückfrage nr, null solange keine da ist */
export function todojobAnswerOf(answers: readonly TodojobEntry[], nr: number): TodojobEntry | null {
  return answers.find((answer) => answer.nr === nr) ?? null
}

/** Ein Job an eine Gruppe: mehr als eine Person, oder eine AR oder ARC. Nur dann gibt es einen Claim. */
export function isTodojobGroupJob(job: TodojobDocument): boolean {
  return (
    todojobValuesOf(job.servantGuids).length > 1 ||
    todojobValuesOf(job.servantAccessRoleKeys).length > 0 ||
    todojobValuesOf(job.servantArcKeys).length > 0
  )
}

/** Dieselbe Parteien-Prüfung wie im Backend (SolrDocumentTodojobUtilsService.isOwnerSide), für die Knöpfe */
export function isTodojobOwnerSide(job: TodojobDocument, session: SessionInfo): boolean {
  return (
    (session.usersGuid !== null && todojobValueOf(job, 'ownerGuid') === session.usersGuid) ||
    matchesRolesOrArc(job, 'ownerAccessRoleKeys', 'ownerArcKeys', session)
  )
}

export function isTodojobServantSide(job: TodojobDocument, session: SessionInfo): boolean {
  return (
    (session.usersGuid !== null && todojobValuesOf(job.servantGuids).includes(session.usersGuid)) ||
    matchesRolesOrArc(job, 'servantAccessRoleKeys', 'servantArcKeys', session)
  )
}

function matchesRolesOrArc(job: TodojobDocument, accessRoleField: string, arcField: string, session: SessionInfo): boolean {
  if (todojobValuesOf(job[accessRoleField]).some((key) => session.accessRoleKeys.includes(key))) {
    return true
  }
  return session.accessRoleCollectionKey !== null && todojobValuesOf(job[arcField]).includes(session.accessRoleCollectionKey)
}

/** Die Farbe eines Status, theme-invariant wie die Badges: so sieht man auf jeder Karte sofort, wo der Job steht */
export function todojobStatusColor(status: string): string {
  switch (status) {
    case 'OFFEN':
      return '#0ea5e9'
    case 'CLAIMED':
      return '#8b5cf6'
    case 'IN_BEARBEITUNG':
      return '#f59e0b'
    case 'FERTIG':
      return '#ef4444'
    case 'ABGESCHLOSSEN':
      return '#22c55e'
    default:
      return '#64748b'
  }
}

const TIME_LOCALES: Record<LanguageCode, string> = { de: 'de-DE', en: 'en-US', bg: 'bg-BG' }

/** Ein Zeitpunkt kurz, in der Sprache der Oberfläche; leer bei nichts */
export function todojobFormatTime(isoTime: string | undefined | null, language: LanguageCode): string {
  if (!isoTime) {
    return ''
  }
  return new Date(isoTime).toLocaleString(TIME_LOCALES[language], { dateStyle: 'short', timeStyle: 'short' })
}

/** Ein Datum ohne Uhrzeit, für die Frist */
export function todojobFormatDate(isoTime: string | undefined | null, language: LanguageCode): string {
  if (!isoTime) {
    return ''
  }
  return new Date(isoTime).toLocaleDateString(TIME_LOCALES[language])
}

// ===== Endpunkte des SolrDocumentTodojobController =====

const TODOJOB_API_PATH = '/api/rest/v1/solr/todojob'

export function getTodojobAccess() {
  return request<TodojobAccessForClient>('GET', `${TODOJOB_API_PATH}/access`)
}

/** Die Jobseite; 404, wenn es den Job nicht gibt oder der User keine Partei ist */
export function getTodojob(id: string) {
  return request<TodojobDocument>('GET', `${TODOJOB_API_PATH}/id/${encodeURIComponent(id)}`)
}

export function listTodojobs(kind: TodojobListKind, page = 0) {
  return request<TodojobResolvedDocumentsPage>('POST', `${TODOJOB_API_PATH}/list`, { kind, page })
}

export function getTodojobWaitingCounts() {
  return request<TodojobWaitingCounts>('GET', `${TODOJOB_API_PATH}/waiting`)
}

/** Der Auszug einer Mappe: die Jobs ihres Anzeigeorts zu einem Hook-Wert */
export function resolveTodojobExcerpts(displayContextKey: string, hookKey: string, hookValue: string, page = 0) {
  return request<TodojobResolvedDocumentsPage>('POST', `${TODOJOB_API_PATH}/excerpt`, { displayContextKey, hookKey, hookValue, page })
}

export function searchTodojobs(searchText: string, exactFilterValuesByFieldName: Record<string, string[]>, page = 0) {
  return request<TodojobResolvedDocumentsPage>('POST', `${TODOJOB_API_PATH}/search`, { searchText, exactFilterValuesByFieldName, page })
}

/** Ähnliche eigene Jobs mit GUT_GEMACHT: dasselbe Verb, ein ähnliches Objekt */
export function suggestSimilarTodojobs(verb: string, objekt: string) {
  return request<TodojobDocument[]>('POST', `${TODOJOB_API_PATH}/suggest`, { verb, objekt })
}

/** Die Werte von verb oder objekt, die mit dem Getippten beginnen */
export function completeTodojobFieldValues(fieldName: 'verb' | 'objekt', prefix: string) {
  return request<string[]>('POST', `${TODOJOB_API_PATH}/complete`, { fieldName, prefix })
}

export function createTodojob(newTodojobData: TodojobCreateData) {
  return request<TodojobDocument>('POST', `${TODOJOB_API_PATH}/create`, newTodojobData)
}

export function claimTodojob(id: string) {
  return request<TodojobDocument>('POST', `${TODOJOB_API_PATH}/claim`, { id })
}

export function releaseTodojobClaim(id: string) {
  return request<TodojobDocument>('POST', `${TODOJOB_API_PATH}/claim/release`, { id })
}

export function takeOverTodojobClaim(id: string, text: string) {
  return request<TodojobDocument>('POST', `${TODOJOB_API_PATH}/claim/take`, { id, text })
}

export function addTodojobRueckmeldungEntry(id: string, type: TodojobRueckmeldungEntryType, text: string | null, value: string | null) {
  return request<TodojobDocument>('POST', `${TODOJOB_API_PATH}/rueckmeldung`, { id, type, text, value })
}

export function retractTodojobRueckmeldungEntry(id: string, nr: number) {
  return request<TodojobDocument>('POST', `${TODOJOB_API_PATH}/rueckmeldung/retract`, { id, nr })
}

export function askTodojobQuestion(id: string, text: string) {
  return request<TodojobDocument>('POST', `${TODOJOB_API_PATH}/question`, { id, text })
}

export function answerTodojobQuestion(id: string, nr: number, text: string) {
  return request<TodojobDocument>('POST', `${TODOJOB_API_PATH}/answer`, { id, nr, text })
}

export function respondTodojob(id: string) {
  return request<TodojobDocument>('POST', `${TODOJOB_API_PATH}/respond`, { id })
}

export function confirmTodojob(id: string) {
  return request<TodojobDocument>('POST', `${TODOJOB_API_PATH}/confirm`, { id })
}

export function returnTodojob(id: string, text: string, aufgabe: string | null, kontext: string[] | null) {
  return request<TodojobDocument>('POST', `${TODOJOB_API_PATH}/return`, { id, text, aufgabe, kontext })
}

export function withdrawTodojob(id: string) {
  return request<TodojobDocument>('POST', `${TODOJOB_API_PATH}/withdraw`, { id })
}

export function commentTodojob(id: string, text: string) {
  return request<TodojobDocument>('POST', `${TODOJOB_API_PATH}/comment`, { id, text })
}

export function changeTodojobDueDate(id: string, fristAm: string | null) {
  return request<TodojobDocument>('POST', `${TODOJOB_API_PATH}/due-date`, { id, fristAm })
}

export function changeTodojobLabels(id: string, displayContextKeys: string[], dauerhaftRelevant: boolean) {
  return request<TodojobDocument>('POST', `${TODOJOB_API_PATH}/labels`, { id, displayContextKeys, dauerhaftRelevant })
}
