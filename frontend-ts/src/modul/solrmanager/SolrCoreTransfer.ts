import { request, uploadWithResponseLines, type UploadProgress } from '../../api/client.ts'

/** Was der Import mit einer id macht, die es im Kern schon gibt (SolrCoreImportMode.java) */
export type SolrCoreImportMode = 'SKIP_EXISTING' | 'REPLACE'

/** Ein Feld der Import-Datei (SolrCoreImportAnalysis.FileField) */
export interface SolrCoreImportFileField {
  name: string
  /** in so vielen Dokumenten hat es einen Wert */
  count: number
  sampleValue: string | null
  /** der Typ des gleichnamigen Kernfelds, null wenn der Kern es nicht kennt */
  coreFieldType: string | null
  essential: boolean
  /** ein Suggest-Zwilling oder _version_: entsteht beim Schreiben neu */
  skipped: boolean
  /** so viele Werte passen nicht zum Typ des Kernfelds */
  invalidValues: number
  firstInvalidValue: string | null
}

/** Ein Feld des Kerns, das in der Datei fehlt (SolrCoreImportAnalysis.MissingCoreField) */
export interface SolrCoreImportMissingCoreField {
  name: string
  type: string
  essential: boolean
}

/** Was die Prüfung über die Datei weiß (SolrCoreImportAnalysis.java) */
export interface SolrCoreImportAnalysis {
  importId: number
  coreKey: string
  fileName: string | null
  documents: number
  /** die Schlusszeile des Exports ist da und nennt dieselbe Anzahl */
  complete: boolean
  declaredDocuments: number | null
  unreadableDocuments: number
  firstUnreadable: string | null
  fileFields: SolrCoreImportFileField[]
  missingCoreFields: SolrCoreImportMissingCoreField[]
}

/** Der Stand des letzten Imports (SolrCoreImportStatus.java) */
export interface SolrCoreImportStatus {
  importId: number
  coreKey: string
  running: boolean
  cancelRequested: boolean
  mode: SolrCoreImportMode
  startedAt: string
  finishedAt: string | null
  fromLine: number
  line: number
  written: number
  skippedExisting: number
  totalDocuments: number
  documentsPerSecond: number
  /** hier hielt der Import an, Fehler oder Abbruch; ein neuer Start ab dieser Zeile macht weiter */
  stoppedAtLine: number | null
  stoppedAtId: string | null
  errorMessage: string | null
}

/** Was der Start schickt: mapping und ignored nur für Felder der Datei, die der Kern nicht kennt */
export interface SolrCoreImportStartRequest {
  importId: number
  mode: SolrCoreImportMode
  /** Feld der Datei → Feld des Kerns */
  mapping: Record<string, string>
  ignored: string[]
  fromLine: number
}

/** So oft fragt die Box den Stand ab, solange ein Import läuft */
export const SOLR_CORE_IMPORT_STATUS_POLL_MS = 2000

const SOLR_CORE_TRANSFER_ADMIN_PATH = '/api/rest/v1/admin/solr/transfer'

/** Bereitet den Export eines Kerns vor: die Nummer für den Download, fünf Minuten gültig, einmal einlösbar */
export function prepareSolrCoreExport(coreKey: string) {
  return request<{ exportId: number }>('POST', `${SOLR_CORE_TRANSFER_ADMIN_PATH}/export/prepare`, { coreKey })
}

/** Der Download des Exports: ein normaler Link, das Token kommt aus dem Cookie, die Datei geht direkt auf die Platte */
export function solrCoreExportDownloadUrl(exportId: number): string {
  return `${SOLR_CORE_TRANSFER_ADMIN_PATH}/export/download/${exportId}`
}

interface AnalyzeLine {
  kind: 'progress' | 'analysis' | 'error'
  documents?: number
  analysis?: SolrCoreImportAnalysis
  message?: string
}

/**
 * Prüfen: die Datei hochladen, die Prüfung meldet zeilenweise. onDocuments bekommt die gelesenen Dokumente, onUpload
 * die gesendeten Bytes. Zurück kommt die Analyse; eine Fehlerzeile des Servers wird ein Error mit ihrer Meldung.
 */
export async function analyzeSolrCoreImport(
  coreKey: string,
  file: File,
  onDocuments: (documents: number) => void,
  onUpload?: UploadProgress,
): Promise<SolrCoreImportAnalysis> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('coreKey', coreKey)
  // gesetzt im Callback: ohne die Angabe hielte TypeScript beide für immer null
  let final = null as SolrCoreImportAnalysis | null
  let error = null as string | null
  await uploadWithResponseLines(
    `${SOLR_CORE_TRANSFER_ADMIN_PATH}/import/analyze`,
    formData,
    (raw) => {
      const line = raw as AnalyzeLine | null
      if (line?.kind === 'progress' && typeof line.documents === 'number') {
        onDocuments(line.documents)
      } else if (line?.kind === 'analysis' && line.analysis) {
        final = line.analysis
      } else if (line?.kind === 'error') {
        error = line.message ?? null
      }
    },
    onUpload,
  )
  if (final === null) {
    throw new Error(error ?? 'keine Antwort der Prüfung')
  }
  return final
}

/** Startet den Import der geprüften Datei im Hintergrund */
export function startSolrCoreImport(startRequest: SolrCoreImportStartRequest) {
  return request<SolrCoreImportStatus>('POST', `${SOLR_CORE_TRANSFER_ADMIN_PATH}/import/start`, startRequest)
}

/** Der Stand des letzten Imports; ohne Import seit dem Start des Backends kommt nichts (204) */
export function getSolrCoreImportStatus() {
  return request<SolrCoreImportStatus | undefined>('GET', `${SOLR_CORE_TRANSFER_ADMIN_PATH}/import/status`)
}

/** Hält den laufenden Import nach dem aktuellen Dokument an */
export function cancelSolrCoreImport() {
  return request<SolrCoreImportStatus | undefined>('POST', `${SOLR_CORE_TRANSFER_ADMIN_PATH}/import/cancel`)
}
