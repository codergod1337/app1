import { request } from '../../api/client.ts'

/** Das Modul testfakedata (User 2026-10-14): Testdaten für die Solr-Kerne, nur ADMIN */
export const TESTFAKEDATA_PATH = '/testfakedata'

/** Bis 99999999 bleiben die Artikelnummern achtstellig (TestFakeDataService.MAX_ARTICLE_COUNT) */
export const TESTFAKEDATA_MAX_ARTICLE_COUNT = 90_000_000

/** Kunden- und Lieferantennummern bleiben siebenstellig (TestFakeDataService.MAX_PARTNER_COUNT) */
export const TESTFAKEDATA_MAX_PARTNER_COUNT = 999_999

/** Obergrenze der Maske für die Datenblätter: die größte ganze Zahl des Backends */
export const TESTFAKEDATA_MAX_DOCUMENT_COUNT = 2_147_483_647

/** So oft fragt die Maske den Stand ab, solange ein Lauf läuft */
export const TESTFAKEDATA_STATUS_POLL_MS = 2000

/** Was die Maske zum Erzeugen schickt (TestFakeDataRequest.java) */
export interface TestFakeDataRequest {
  articleCount: number
  customerCount: number
  supplierCount: number
  documentCount: number
  /** Datenblätter je Artikel im Mittel */
  documentsPerArticleMean: number
  /** Streuung der Normalverteilung um das Mittel */
  documentsPerArticleDeviation: number
}

export type TestFakeDataPhaseKey = 'CUSTOMERS' | 'SUPPLIERS' | 'ARTICLES' | 'DOCUMENTS'

/** Eine Phase des Laufs (TestFakeDataStatus.Phase) */
export interface TestFakeDataPhase {
  key: TestFakeDataPhaseKey
  /** Firmen, Artikel oder Datenblätter insgesamt */
  total: number
  done: number
  skipped: number
  failed: number
  /** bei Kunden und Lieferanten Firma plus Ansprechpartner */
  writtenSolrDocuments: number
  startedAt: string | null
  finishedAt: string | null
  solrDocumentsPerSecond: number
  lastErrorMessage: string | null
}

/** Der Stand des letzten Laufs (TestFakeDataStatus.java) */
export interface TestFakeDataStatus {
  running: boolean
  cancelRequested: boolean
  currentPhase: TestFakeDataPhaseKey | null
  startedAt: string | null
  finishedAt: string | null
  request: TestFakeDataRequest | null
  phases: TestFakeDataPhase[]
  errorMessage: string | null
}

const TESTFAKEDATA_ADMIN_PATH = '/api/rest/v1/admin/testfakedata'

/** Startet einen Lauf im Hintergrund. 400 bei unbrauchbaren Zahlen, 409, wenn schon einer läuft. */
export function startTestFakeData(testFakeDataRequest: TestFakeDataRequest) {
  return request<TestFakeDataStatus>('POST', `${TESTFAKEDATA_ADMIN_PATH}/start`, testFakeDataRequest)
}

/** Der Stand des letzten Laufs */
export function getTestFakeDataStatus() {
  return request<TestFakeDataStatus>('GET', `${TESTFAKEDATA_ADMIN_PATH}/status`)
}

/** Bricht den laufenden Lauf nach dem aktuellen Eintrag ab */
export function cancelTestFakeData() {
  return request<TestFakeDataStatus>('POST', `${TESTFAKEDATA_ADMIN_PATH}/cancel`)
}
