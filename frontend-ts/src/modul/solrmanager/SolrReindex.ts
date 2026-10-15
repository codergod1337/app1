import { request } from '../../api/client.ts'

/**
 * Die Prüfungen beim Nachziehen (DmsReindexOption.java, NachrichtenReindexOption.java) und der Neuaufbau der Suche,
 * den es in jedem Kern gibt (SolrReindexService.SEARCH_SUGGESTION_REBUILD)
 */
export type SolrReindexOption =
  | 'ACLS_FROM_FILE_SUB_CLASS'
  | 'SEARCHABLE_WORDS_FROM_BINARY'
  | 'ACLS_FROM_MESSAGE_SUB_CLASS'
  | 'SEARCH_SUGGESTION_REBUILD'

/**
 * Suche neu aufbauen (SolrReindexSearchSuggestionRebuilder, User 2026-10-14): jedes Dokument einmal neu schreiben,
 * nach einer Änderung am Regelwerk der Suche. Läuft nur allein.
 */
export const SOLR_REINDEX_SEARCH_SUGGESTION_REBUILD: SolrReindexOption = 'SEARCH_SUGGESTION_REBUILD'

/** Die Prüfungen eigener Art je Kern, die erste ist vorgewählt (User 2026-10-14) */
const SOLR_REINDEX_CHECKS_BY_CORE_KEY: Readonly<Record<string, readonly SolrReindexOption[]>> = {
  DMS: ['ACLS_FROM_FILE_SUB_CLASS', 'SEARCHABLE_WORDS_FROM_BINARY'],
  NACHRICHTEN: ['ACLS_FROM_MESSAGE_SUB_CLASS'],
}

/** Was ein Kern im Nachziehen anbietet: seine eigenen Prüfungen, dann in jedem Kern der Neuaufbau der Suche */
export function solrReindexOptionsOf(coreKey: string): SolrReindexOption[] {
  return [...(SOLR_REINDEX_CHECKS_BY_CORE_KEY[coreKey] ?? []), SOLR_REINDEX_SEARCH_SUGGESTION_REBUILD]
}

/** So oft fragt die Box den Stand ab, solange ein Lauf läuft */
export const SOLR_REINDEX_STATUS_POLL_MS = 2000

/** Der Stand des letzten Laufs (SolrReindexStatus.java), über alle Kerne: coreKey sagt, welcher dran ist */
export interface SolrReindexStatus {
  coreKey: string
  running: boolean
  cancelRequested: boolean
  options: SolrReindexOption[]
  startedAt: string
  finishedAt: string | null
  /** Dokumente im Kern beim Start, -1 bis zur ersten Seite */
  total: number
  /** fertig geprüft, geändert oder nicht */
  checked: number
  /** davon geschrieben */
  changed: number
  /** mit Fehler, der Lauf ging weiter */
  failed: number
  documentsPerSecond: number
  /** ob der Lauf die geänderten ids aufschreibt; der Neuaufbau der Suche zählt nur */
  changedIdsRecorded: boolean
  /** die ersten hundert geänderten ids; alle stehen in der Datei */
  firstChangedIds: string[]
  lastErrorMessage: string | null
  /** warum der ganze Lauf abbrach */
  errorMessage: string | null
}

const SOLR_REINDEX_ADMIN_PATH = '/api/rest/v1/admin/solr/reindex'

/** Startet das Nachziehen eines Kerns im Hintergrund. 400 bei einer unbekannten Option, 409, wenn schon einer läuft. */
export function startSolrReindex(coreKey: string, options: readonly SolrReindexOption[]) {
  return request<SolrReindexStatus>('POST', `${SOLR_REINDEX_ADMIN_PATH}/start`, { coreKey, options })
}

/** Der Stand des letzten Laufs; ohne Lauf seit dem Start des Backends kommt nichts (204) */
export function getSolrReindexStatus() {
  return request<SolrReindexStatus | undefined>('GET', `${SOLR_REINDEX_ADMIN_PATH}/status`)
}

/** Bricht den laufenden Lauf nach dem aktuellen Dokument ab */
export function cancelSolrReindex() {
  return request<SolrReindexStatus | undefined>('POST', `${SOLR_REINDEX_ADMIN_PATH}/cancel`)
}

/** Alle geänderten ids des letzten Laufs als Textdatei: ein normaler Download, das Token kommt aus dem Cookie */
export function solrReindexChangedIdsUrl(): string {
  return `${SOLR_REINDEX_ADMIN_PATH}/changed-ids`
}
