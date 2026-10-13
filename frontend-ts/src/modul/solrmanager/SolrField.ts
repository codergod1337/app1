import { request } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { translate } from '../../components/multilingual.ts'

/** Unser Typkatalog (SolrFieldType.java): jeder Eintrag ist ein fieldType im Basis-Schema. Genau zwei Texttypen. */
export type SolrFieldType = 'STRING' | 'EMAIL' | 'BOOLEAN' | 'LONG' | 'DOUBLE' | 'DATE' | 'TEXT_EDGE' | 'TEXT_SUCHE'

/**
 * Die Typen, nur der Name: Das ist ein Adminmodul, die Erklärung steht im Hoverlay der Spalte. Ganze Zahlen sind
 * immer long, es gibt kein int. suggestable: ob ein Suggest-Zwilling möglich ist, nur bei string und Zahlen; Texte
 * brauchen keinen, text_edge ist selbst per Wortanfang suchbar.
 */
export const SOLR_FIELD_TYPES: { type: SolrFieldType; label: string; suggestable: boolean }[] = [
  { type: 'STRING', label: 'string', suggestable: true },
  // string mit einem Zwilling, der jeden Anfang bis zur vollen Länge der Adresse kennt (email_suggest)
  { type: 'EMAIL', label: 'email', suggestable: true },
  { type: 'BOOLEAN', label: 'boolean', suggestable: false },
  { type: 'LONG', label: 'long', suggestable: true },
  { type: 'DOUBLE', label: 'double', suggestable: true },
  { type: 'DATE', label: 'date', suggestable: false },
  { type: 'TEXT_EDGE', label: 'text_edge', suggestable: false },
  { type: 'TEXT_SUCHE', label: 'text_suche', suggestable: false },
]

export function isSuggestable(type: SolrFieldType): boolean {
  return SOLR_FIELD_TYPES.find((candidate) => candidate.type === type)?.suggestable ?? false
}

/** Die sechs Solr-Schalter, Spalten der Tabelle im Kern-Tab */
export type SolrFieldSetting = 'indexed' | 'stored' | 'multiValued' | 'docValues' | 'required' | 'suggest'

/** Die Erklärungen dazu stehen in solrFieldHints.tsx, fürs Hoverlay am Spaltenkopf */
export const SOLR_FIELD_SETTINGS: { setting: SolrFieldSetting; label: string }[] = [
  { setting: 'indexed', label: 'indexed' },
  { setting: 'stored', label: 'stored' },
  { setting: 'multiValued', label: 'multiValued' },
  { setting: 'docValues', label: 'docValues' },
  { setting: 'required', label: 'required' },
  { setting: 'suggest', label: 'suggest' },
]

/**
 * Wer ein Feld setzt, die Schreibregel je Feld (SolrFieldClientWriteRule.java, Vorgabe 2026-10-07).
 * CLIENT_SETS_AND_CHANGES: der Client setzt es beim Anlegen und ändert es beim Bearbeiten. CLIENT_SETS_ONCE: nur beim
 * Anlegen, danach ignoriert. BACKEND_ONLY: nie vom Client, der Schreibservice setzt es selbst. Schickt der Client ein
 * Feld, das er nicht darf, wird es ignoriert, nicht abgelehnt. Nur in unserer Felddefinition, nie in Solr. Neue Felder
 * sind BACKEND_ONLY, und ein BACKEND_ONLY-Feld hat im Kern-Tab keinen Löschknopf.
 */
export type SolrFieldClientWriteRule = 'CLIENT_SETS_AND_CHANGES' | 'CLIENT_SETS_ONCE' | 'BACKEND_ONLY'

/** Die drei Stufen in der Reihenfolge, in der der Umschaltknopf im Sheet sie durchläuft */
export const SOLR_FIELD_CLIENT_WRITE_RULES: readonly SolrFieldClientWriteRule[] = [
  'BACKEND_ONLY',
  'CLIENT_SETS_ONCE',
  'CLIENT_SETS_AND_CHANGES',
]

/** Darf der Client das Feld beim Anlegen mitgeben? */
export function isClientAllowedToSetOnCreate(rule: SolrFieldClientWriteRule): boolean {
  return rule !== 'BACKEND_ONLY'
}

/** Darf der Client das Feld beim Bearbeiten ändern? */
export function isClientAllowedToChangeOnUpdate(rule: SolrFieldClientWriteRule): boolean {
  return rule === 'CLIENT_SETS_AND_CHANGES'
}

/**
 * Eine Zeile der Feldtabelle, wie das Backend sie liefert (SolrField.java, neu gefasst 2026-10-09): ein normales Feld
 * eines Kerns, oder ein Hook in einem Kern (hookKey gesetzt, name gleich dem Hook-Key). Hook-Zeilen entstehen und
 * verschwinden mit dem Hook, ihre Technik steht fest, je Kern änderbar sind nur Leseliste und Suchgewicht. coreKey
 * null heißt in jedem Kern, so auch id auf Platz 1. Gelesen wird über die Stammdaten.
 */
export interface SolrField {
  id: number
  /** der Kern (SolrCore.key), null heißt in jedem Kern, ändert sich nie */
  coreKey: string | null
  /** der Name des Feldes in Solr, z. B. fileName; bei einem normalen Feld eindeutig über alle Kerne, ändert sich nie */
  name: string
  /** der Key des Hooks, wenn die Zeile ein Hook in ihrem Kern ist, sonst null; ändert sich nie */
  hookKey: string | null
  type: SolrFieldType
  indexed: boolean
  stored: boolean
  multiValued: boolean
  docValues: boolean
  /** der Client muss es beim Anlegen mitschicken, sonst lehnt das Backend ab; nur bei Feldern, die der Client setzt */
  required: boolean
  /** ein Zwilling <name>Suggested mit copyField, nicht bei jedem Typ */
  suggest: boolean
  /** die ARs, deren Inhaber das Feld lesen dürfen; leer heißt niemand außer SOLR_USERS_VIEW_ALL */
  readAccessRoleKeys: string[]
  /** wer das Feld setzt, der Umschaltknopf im Sheet */
  clientWriteRule: SolrFieldClientWriteRule
  /** das Gewicht in der Suche: -1 nicht durchsucht, 0 durchsucht ohne Punkte, ab 1 das Gewicht; ab 0 nur mit indexed */
  searchWeight: number
  /** der lose Fremdschlüssel aufs Mapping: der Key eines Mapping-Eintrags, dessen Werte in dieses Feld gehen; null heißt keiner */
  command: string | null
  /** mehrsprachiges JSON wie beim Hook: der Name, den ein User sieht; null heißt der technische Name */
  displayName: string | null
  /** mehrsprachiges JSON: wozu das Feld da ist und welche Daten hinein gehören */
  description: string | null
  /** Reihenfolge in der Tabelle des Kerns */
  listingPosition: number
}

/** Daten zum Anlegen: alles außer id, die vergibt das Backend. */
export type SolrFieldData = Omit<SolrField, 'id'>

/** Gehört das Feld zu diesem Kern: sein eigenes oder eines, das in jedem Kern liegt */
export function isSolrFieldOfCore(solrField: Pick<SolrField, 'coreKey'>, coreKey: string): boolean {
  return solrField.coreKey === null || solrField.coreKey === coreKey
}

/** Ist die Zeile ein Hook in ihrem Kern: Technik fest, nur Lesen und Suche änderbar, kein Löschknopf */
export function isSolrHookField(solrField: Pick<SolrField, 'hookKey'>): boolean {
  return solrField.hookKey !== null
}

/**
 * Kann ein Suchgewicht wirken: nur indexed zählt, ohne Index hat Solr nichts zum Durchsuchen. Alles andere entscheidet
 * der Admin allein (User 2026-10-09; SolrField.isSearchable im Backend).
 */
export function isSolrFieldSearchable(solrField: Pick<SolrField, 'indexed'>): boolean {
  return solrField.indexed
}

/** Die Feld-ARs: nur ARs mit diesem Präfix werden als Leserecht an Feldern und Hooks angeboten (User 2026-10-09) */
export const FIELD_READ_ACCESS_ROLE_KEY_PREFIX = 'SOLR_FIELD_'

/** Die Grundmenge: Felder, die jeder User braucht, damit sich ein Dokument darstellen lässt, heißt „immer“ */
export const EVERY_USER_ACCESS_ROLE_KEY = 'USER'

/**
 * Die ARs, die der Admin an ein Feld oder einen Hook hängen kann: USER für die Grundmenge und alle Feld-ARs mit dem
 * Präfix SOLR_FIELD_, dazu die, die schon dranstehen, damit nichts stillschweigend verschwindet. SOLR_USERS_VIEW_ALL
 * ist ein User-Recht, kein Feld-Recht, und wird nicht angeboten.
 */
export function fieldReadAccessRoleCandidates<T extends { key: string }>(
  accessRoles: readonly T[],
  selectedKeys: readonly string[],
): T[] {
  return accessRoles.filter(
    (accessRole) =>
      accessRole.key === EVERY_USER_ACCESS_ROLE_KEY ||
      accessRole.key.startsWith(FIELD_READ_ACCESS_ROLE_KEY_PREFIX) ||
      selectedKeys.includes(accessRole.key),
  )
}

/** Der Name eines Feldes für einen User: der Anzeigename in der Sprache, sonst der technische Name (User 2026-10-07) */
export function solrFieldLabel(solrField: Pick<SolrField, 'name' | 'displayName'>, language?: LanguageCode): string {
  return translate(solrField.displayName, language) || solrField.name
}

/**
 * Der Anzeigename eines Feldes, gesucht über Kern und Name in den Stammdaten, null wenn es das Feld nicht gibt oder es
 * keinen Namen hat. Für Masken, die ein Feld nur beim Namen kennen und eine eigene Beschriftung als Rückfall haben.
 */
export function solrFieldDisplayNameOf(
  solrFields: readonly SolrField[],
  coreKey: string,
  name: string,
  language?: LanguageCode,
): string | null {
  const solrField = solrFields.find((candidate) => candidate.name === name && isSolrFieldOfCore(candidate, coreKey))
  const label = solrField ? translate(solrField.displayName, language) : ''
  return label === '' ? null : label
}

// ===== Endpunkte des SolrFieldAdminController. Gelesen wird über die Stammdaten (useMasterData). =====

const SOLR_FIELD_ADMIN_PATH = '/api/rest/v1/admin/solr/field'

/** Neues Feld. id, Hook-Namen, vergebene Namen und suggest an Typen ohne Zwilling lehnt das Backend ab. */
export function createSolrField(newSolrFieldData: SolrFieldData) {
  return request<SolrField>('POST', SOLR_FIELD_ADMIN_PATH, newSolrFieldData)
}

/** Die id bestimmt das Feld. Kern und Name ändern sich nie, alles andere wird ersetzt. */
export function updateSolrField(changedSolrField: SolrField) {
  return request<SolrField>('PUT', SOLR_FIELD_ADMIN_PATH, changedSolrField)
}

/** Jede id bekommt ihre neue Position, z. B. { '7': 1, '9': 2 }. Zurück kommen alle Felder in neuer Reihenfolge. */
export function changeSolrFieldPositions(newPositionById: Record<string, number>) {
  return request<SolrField[]>('POST', `${SOLR_FIELD_ADMIN_PATH}/position`, newPositionById)
}

/** Löscht das Feld aus unserer Tabelle. Der Knopf fehlt bei BACKEND_ONLY, denn das Backend braucht es. */
export function deleteSolrField(id: number) {
  return request<SolrField>('DELETE', SOLR_FIELD_ADMIN_PATH, { id })
}
