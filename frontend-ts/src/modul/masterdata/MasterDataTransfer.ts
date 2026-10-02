import { request } from '../../api/client.ts'

/** Die Bereiche der Stammdaten, je eine Datei im ZIP (MasterDataSection.java) */
export type MasterDataSection =
  | 'ACCESS_ROLES'
  | 'ACCESS_ROLE_COLLECTIONS'
  | 'FILE_EXTENSION_COLLECTIONS'
  | 'FILE_EXTENSIONS'
  | 'FILE_SUB_CLASSES'
  | 'USERS'
  | 'USERS_DETAILS'
  | 'USERS_SETTINGS'
  | 'ACCESS_ROLE_USERS_ASSIGNMENTS'
  | 'ACCESS_ROLE_COLLECTION_USERS_ASSIGNMENTS'

/** Alle Bereiche in der Reihenfolge des Imports, mit Namen */
export const MASTER_DATA_SECTIONS: { section: MasterDataSection; label: string }[] = [
  { section: 'ACCESS_ROLES', label: 'AccessRoles (AR)' },
  { section: 'ACCESS_ROLE_COLLECTIONS', label: 'AccessRoleCollections (ARC)' },
  { section: 'FILE_EXTENSION_COLLECTIONS', label: 'Endungsgruppen' },
  { section: 'FILE_EXTENSIONS', label: 'Endungen' },
  { section: 'FILE_SUB_CLASSES', label: 'Dateiarten (FSC) mit Rechten und Endungen' },
  { section: 'USERS', label: 'User' },
  { section: 'USERS_DETAILS', label: 'User-Details' },
  { section: 'USERS_SETTINGS', label: 'User-Einstellungen' },
  { section: 'ACCESS_ROLE_USERS_ASSIGNMENTS', label: 'Zuordnung User ↔ AR' },
  { section: 'ACCESS_ROLE_COLLECTION_USERS_ASSIGNMENTS', label: 'Zuordnung User ↔ ARC' },
]

export const ALL_MASTER_DATA_SECTIONS = MASTER_DATA_SECTIONS.map(({ section }) => section)

/** Ein fertiger Export (MasterDataExport.java) */
export interface MasterDataExport {
  fileName: string
  /** das ZIP als Base64 */
  zip: string
  counts: Partial<Record<MasterDataSection, number>>
}

/** Was mit einer Zeile des Imports passiert (MasterDataImportCheck.RowStatus) */
export type RowStatus = 'NEW' | 'EXISTING' | 'SKIPPED' | 'INVALID' | 'ORPHAN' | 'CONFLICT'

/** Kurzname und Erklärung je Status */
export const ROW_STATUS_TEXT: Record<RowStatus, { label: string; hint: string }> = {
  NEW: { label: 'neu', hint: 'wird angelegt, wenn der Bereich angehakt ist' },
  EXISTING: { label: 'vorhanden', hint: 'gibt es schon, wird übersprungen und nicht verändert' },
  SKIPPED: { label: 'abgewählt', hint: 'beim User im Konflikt abgewählt, wird nicht geschrieben' },
  INVALID: { label: 'ungültig', hint: 'Pflichtfeld, Format oder doppelt in der Datei: wird nicht geschrieben' },
  ORPHAN: { label: 'verwaist', hint: 'das Ziel gibt es weder bei uns noch angehakt im Import: wird nicht geschrieben' },
  CONFLICT: { label: 'Konflikt', hint: 'passt nicht zu unserem Stand, unser Stand bleibt' },
}

export interface RowCheck {
  id: string
  label: string
  /** mehrsprachiges JSON, falls die Entity einen Namen hat */
  displayName: string | null
  status: RowStatus
  messages: string[]
}

export interface SectionCheck {
  section: MasterDataSection
  /** die Datei steckt im ZIP */
  present: boolean
  /** zum Schreiben angehakt */
  selected: boolean
  newCount: number
  existingCount: number
  skippedCount: number
  problemCount: number
  rows: RowCheck[]
}

/** Ein Teil eines Users im Konflikt */
export interface ConflictItem {
  key: string
  /** was passieren würde, wenn er übernommen wird */
  status: RowStatus
  /** wird laut Auswahl übernommen */
  taken: boolean
  message: string | null
}

/** Ein User aus der Datei, den es bei uns unter derselben E-Mail, aber anderer guid gibt */
export interface UsersConflict {
  fileGuid: string
  existingGuid: string
  email: string
  label: string
  details: ConflictItem | null
  settings: ConflictItem[]
  accessRoles: ConflictItem[]
  accessRoleCollection: ConflictItem | null
}

/** Was ein Import tun würde oder getan hat (MasterDataImportCheck.java) */
export interface MasterDataImportCheck {
  /** nichts blockiert, der Import darf schreiben */
  ready: boolean
  blockers: string[]
  formatVersion: number | null
  exportedAt: string | null
  exportedBy: string | null
  sections: SectionCheck[]
  usersConflicts: UsersConflict[]
  /** wer durch den Import ADMIN bekäme */
  adminGrants: string[]
}

/** Was von einem User im Konflikt an unseren User geht */
export interface UsersConflictDecision {
  details: boolean
  settingKeys: string[]
  accessRoleKeys: string[]
  accessRoleCollection: boolean
}

/** Prüfen und Importieren bekommen dasselbe */
export interface MasterDataImportRequest {
  /** das ZIP als Base64 */
  zip: string
  sections: MasterDataSection[]
  /** je guid aus der Datei. Fehlt ein User, geht alles Neue an unseren. */
  usersConflictDecisions: Record<string, UsersConflictDecision>
}

// ===== Endpunkte des MasterDataAdminController =====

const MASTER_DATA_ADMIN_PATH = '/api/rest/v1/admin/masterdata'

/** Die angehakten Bereiche als ZIP, Base64 im JSON */
export function exportMasterData(sections: MasterDataSection[]) {
  return request<MasterDataExport>('POST', `${MASTER_DATA_ADMIN_PATH}/export`, { sections })
}

/** Probelauf: was der Import mit dieser Auswahl täte. Schreibt nichts. */
export function checkMasterDataImport(importRequest: MasterDataImportRequest) {
  return request<MasterDataImportCheck>('POST', `${MASTER_DATA_ADMIN_PATH}/import/check`, importRequest)
}

/** Schreibt alles Neue der angehakten Bereiche, in einer Transaktion. Zurück kommt, was geschrieben wurde. */
export function importMasterData(importRequest: MasterDataImportRequest) {
  return request<MasterDataImportCheck>('POST', `${MASTER_DATA_ADMIN_PATH}/import`, importRequest)
}

/** Die Auswahl eines Users im Konflikt, wie das Backend sie gerade anwendet */
export function decisionOfConflict(usersConflict: UsersConflict): UsersConflictDecision {
  return {
    details: usersConflict.details?.taken ?? false,
    settingKeys: usersConflict.settings.filter((item) => item.taken).map((item) => item.key),
    accessRoleKeys: usersConflict.accessRoles.filter((item) => item.taken).map((item) => item.key),
    accessRoleCollection: usersConflict.accessRoleCollection?.taken ?? false,
  }
}
