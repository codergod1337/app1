import { request } from '../../api/client.ts'

/** Unser Typkatalog (SolrFieldType.java): jeder Eintrag ist ein fieldType im Basis-Schema */
export type SolrFieldType = 'STRING' | 'BOOLEAN' | 'LONG' | 'DOUBLE' | 'DATE' | 'TEXT_MINIMAL' | 'TEXT_WORD' | 'TEXT_WORDS'

/**
 * Die Typen, nur der Name: Das ist ein Adminmodul, die Erklärung steht im Hoverlay der Spalte. Ganze Zahlen sind
 * immer long, es gibt kein int. suggestable: ob ein Suggest-Zwilling möglich ist (nicht bei boolean und date).
 */
export const SOLR_FIELD_TYPES: { type: SolrFieldType; label: string; suggestable: boolean }[] = [
  { type: 'STRING', label: 'string', suggestable: true },
  { type: 'BOOLEAN', label: 'boolean', suggestable: false },
  { type: 'LONG', label: 'long', suggestable: true },
  { type: 'DOUBLE', label: 'double', suggestable: true },
  { type: 'DATE', label: 'date', suggestable: false },
  { type: 'TEXT_MINIMAL', label: 'text_minimal', suggestable: true },
  { type: 'TEXT_WORD', label: 'text_word', suggestable: true },
  { type: 'TEXT_WORDS', label: 'text_words', suggestable: true },
]

export function isSuggestable(type: SolrFieldType): boolean {
  return SOLR_FIELD_TYPES.find((candidate) => candidate.type === type)?.suggestable ?? false
}

/** Die sechs Solr-Schalter, Spalten der Tabelle im Kern-Tab */
export type SolrFieldSetting = 'indexed' | 'stored' | 'multiValued' | 'docValues' | 'required' | 'suggest'

/**
 * Was am Feld id feststeht: Es ist der Primärschlüssel (uniqueKey) jedes Kerns, also string, indexed, required und nie
 * multiValued. Das Backend lehnt jede Änderung daran ab und löscht das Feld nicht. stored, docValues, Beschreibung und
 * Position sind frei wie bei jedem Feld.
 */
export const SOLR_ID_FIELD_FIXED_SETTINGS: readonly SolrFieldSetting[] = ['indexed', 'multiValued', 'required']

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
 * Ein normales Feld eines Kerns, wie es das Backend liefert (SolrField.java). Es gibt genau zwei Arten von
 * Solr-Feldern: Hooks, in jedem Kern gleich, und diese normalen Felder je Kern, auch id. Eindeutig ist das Paar coreKey
 * und name, die id ist nur die Zeile in der Datenbank. Gelesen wird über die Stammdaten.
 */
export interface SolrField {
  id: number
  /** der Kern (SolrCore.key), ändert sich nie */
  coreKey: string
  /** der Name des Feldes in Solr, z. B. fileName, ändert sich nie */
  name: string
  type: SolrFieldType
  indexed: boolean
  stored: boolean
  multiValued: boolean
  docValues: boolean
  required: boolean
  /** ein Zwilling <name>Suggested mit copyField, nicht bei jedem Typ */
  suggest: boolean
  /** mehrsprachiges JSON: wozu das Feld da ist und welche Daten hinein gehören */
  description: string | null
  /** Reihenfolge in der Tabelle des Kerns */
  listingPosition: number
}

/** Daten zum Anlegen: alles außer id, die vergibt das Backend. */
export type SolrFieldData = Omit<SolrField, 'id'>

// ===== Endpunkte des SolrFieldAdminController. Gelesen wird über die Stammdaten (useMasterData). =====

const SOLR_FIELD_ADMIN_PATH = '/api/rest/v1/admin/solr/field'

/** Neues Feld. Hook-Namen und suggest an Typen ohne Zwilling lehnt das Backend ab. */
export function createSolrField(newSolrFieldData: SolrFieldData) {
  return request<SolrField>('POST', SOLR_FIELD_ADMIN_PATH, newSolrFieldData)
}

/** Die id bestimmt das Feld. Kern und Name ändern sich nie, bei id und cursorDate auch nicht der Typ, bei id auch nicht indexed, required, multiValued. */
export function updateSolrField(changedSolrField: SolrField) {
  return request<SolrField>('PUT', SOLR_FIELD_ADMIN_PATH, changedSolrField)
}

/** Jede id bekommt ihre neue Position, z. B. { '7': 1, '9': 2 }. Zurück kommen alle Felder in neuer Reihenfolge. */
export function changeSolrFieldPositions(newPositionById: Record<string, number>) {
  return request<SolrField[]>('POST', `${SOLR_FIELD_ADMIN_PATH}/position`, newPositionById)
}

/** Löscht das Feld aus unserer Tabelle. id und cursorDate lehnt das Backend ab (409), die hat jeder Kern. */
export function deleteSolrField(id: number) {
  return request<SolrField>('DELETE', SOLR_FIELD_ADMIN_PATH, { id })
}
