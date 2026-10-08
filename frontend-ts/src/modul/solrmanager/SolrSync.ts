import { request } from '../../api/client.ts'

/**
 * Was der Abgleich eines Kerns mit Solr ergab (SolrCoreSyncReport.java). Postgres ist die Wahrheit, Solr wird
 * nachgezogen: fehlende Felder angelegt, abweichende Schalter angepasst, Suggest-Zwillinge mit copyField. Nie gelöscht,
 * nie ein Typ geändert, beides wird nur gemeldet.
 */
export interface SolrCoreSyncReport {
  coreKey: string
  /** false: Solr hat nicht geantwortet, error sagt warum */
  solrReachable: boolean
  /** false: den Kern gibt es in Solr nicht, er fehlt in solr_cores der .env */
  coreExists: boolean
  /** Felder im Schema des Kerns, ohne die Systemfelder */
  fieldsInSolr: number
  added: string[]
  replaced: string[]
  /** „quelle → ziel“ */
  copyFieldsAdded: string[]
  /** in Solr, aber nicht in Postgres: nur gemeldet */
  onlyInSolr: string[]
  /** anderer Typ in Solr als in Postgres: nur gemeldet, Neuaufbau nötig */
  typeConflicts: string[]
  /** die Klauselgrenze des Kerns nach dem Abgleich (query.maxBooleanClauses), 0 wenn nicht gelesen */
  maxBooleanClauses: number
  /** der Abgleich hat sie gerade über die Config-API gesetzt */
  maxBooleanClausesChanged: boolean
  error: string | null
  syncedAt: string
  durationMs: number
}

// ===== Endpunkte des SolrSyncAdminController =====

const SOLR_SYNC_ADMIN_PATH = '/api/rest/v1/admin/solr/sync'

/** Die letzten Berichte in der Reihenfolge der Kerne. Fehlt ein Kern, lief sein Abgleich noch nicht. */
export function getSolrSyncReports() {
  return request<SolrCoreSyncReport[]>('GET', SOLR_SYNC_ADMIN_PATH)
}

/** Jetzt abgleichen, einen Kern oder ohne key alle. Zurück kommen alle Berichte. */
export function runSolrSync(coreKey?: string) {
  return request<SolrCoreSyncReport[]>('POST', SOLR_SYNC_ADMIN_PATH, coreKey === undefined ? {} : { coreKey })
}
