import type { CSSProperties } from 'react'
import { request } from '../../api/client.ts'
import type { SolrField } from './SolrField.ts'
import type { SolrLook } from './SolrLook.ts'

/**
 * Ein Solr-Kern, wie ihn das Backend liefert (SolrCore.java). Der key folgt der normalen Key-Regel und ist zugleich der
 * Name des Kerns in Solr. Kein Badge, sondern ein Emblem (SolrCoreEmblem), das Aussehen steckt im look. Gelesen wird
 * über die Stammdaten.
 */
export interface SolrCore {
  /** z. B. DMS: normale Key-Regel, zugleich der Name des Kerns in Solr, ändert sich nie. HOOKS, HOOKGROUPS und CORES sind reserviert */
  key: string
  /** mehrsprachiges JSON, z. B. {"de":"Dokumente"}, steht am Tab und im Emblem */
  displayName: string
  look: SolrLook
  /** Reihenfolge der Tabs */
  listingPosition: number
}

/**
 * Das Feld id: der Primärschlüssel (uniqueKey) jedes Kerns, eine Zeile ohne Kern auf Platz 1, also in jedem Kern. Was
 * hineinkommt, sagt der Code des Kerns.
 */
export const SOLR_CORE_ID_FIELD_NAME = 'id'

/** Das Feld cursorDate: der Zeiger zum Durchiterieren aller Dokumente, eine Zeile ohne Kern, also in jedem Kern */
export const SOLR_CORE_CURSOR_DATE_FIELD_NAME = 'cursorDate'

/**
 * Das Feld userGrantedAccess: die guids der User mit Dateifreigabe, der Freigabe-Service liest allein danach. Eine
 * Zeile ohne Kern, also in jedem Kern. Setzt nur das Backend.
 */
export const SOLR_CORE_USER_GRANTED_ACCESS_FIELD_NAME = 'userGrantedAccess'

/** Die Felder, die jeder Kern hat und die nie ein Client setzt */
export const SOLR_CORE_BUILT_IN_FIELD_NAMES: readonly string[] = [
  SOLR_CORE_ID_FIELD_NAME,
  SOLR_CORE_CURSOR_DATE_FIELD_NAME,
  SOLR_CORE_USER_GRANTED_ACCESS_FIELD_NAME,
]

// ===== Die Kernfarbe (User 2026-10-07, nach kernFarbe der Vorlage, nur aus den Daten statt aus dem Theme) =====

/** Die Hauptfarbe, wenn der Admin keine gesetzt hat: dieselbe wie der Standard von .solr-look in basic.css */
export const SOLR_CORE_DEFAULT_MAIN_COLOR = '#0ea5e9'
export const SOLR_CORE_DEFAULT_TEXT_COLOR = '#ffffff'

/** Die Hauptfarbe eines Kerns aus seinem look, sonst der Standard */
export function solrCoreMainColor(solrCore: SolrCore | null | undefined): string {
  return solrCore?.look.mainColor ?? SOLR_CORE_DEFAULT_MAIN_COLOR
}

/** Die Schriftfarbe auf der Hauptfarbe aus seinem look, sonst Weiß */
export function solrCoreTextColor(solrCore: SolrCore | null | undefined): string {
  return solrCore?.look.textColor ?? SOLR_CORE_DEFAULT_TEXT_COLOR
}

/**
 * Die Kernfarbe als CSS-Variablen für alles darunter (Treffer, Chips, Marken): --core-color und --core-text. Wie die
 * Klasse kern-<key> der Vorlage, nur dass die Farbe aus dem look des Kerns in den Stammdaten kommt.
 */
export function solrCoreColorStyle(solrCore: SolrCore | null | undefined): CSSProperties {
  return { '--core-color': solrCoreMainColor(solrCore), '--core-text': solrCoreTextColor(solrCore) } as CSSProperties
}

export function solrCoreOfKey(solrCores: readonly SolrCore[] | null, key: string): SolrCore | null {
  return solrCores?.find((candidate) => candidate.key === key) ?? null
}

/**
 * Zu welchem Kern ein Feld gehört, für die Farbe eines Chips: der Kern, dessen Felddefinition den Namen hat. Hooks
 * und Felder, die in jedem Kern liegen, gehören keinem Kern, für sie null. Wie kernZuFeld der Vorlage, nur aus den
 * Stammdaten statt aus einer Liste.
 */
export function solrCoreOfFieldName(
  solrCores: readonly SolrCore[] | null,
  solrFields: readonly SolrField[] | null,
  fieldName: string,
): SolrCore | null {
  const solrField = solrFields?.find((candidate) => candidate.name === fieldName)
  return solrField?.coreKey ? solrCoreOfKey(solrCores, solrField.coreKey) : null
}

// ===== Endpunkte des SolrCoreAdminController. Gelesen wird über die Stammdaten (useMasterData). =====

const SOLR_CORE_ADMIN_PATH = '/api/rest/v1/admin/solr/core'

/** Neuer Kern. HOOKS, HOOKGROUPS und CORES sind als key reserviert, die lehnt das Backend ab. */
export function createSolrCore(newSolrCoreData: SolrCore) {
  return request<SolrCore>('POST', SOLR_CORE_ADMIN_PATH, newSolrCoreData)
}

/** Alle Felder, der key bestimmt den Kern und ändert sich nie. */
export function updateSolrCore(changedSolrCoreData: SolrCore) {
  return request<SolrCore>('PUT', SOLR_CORE_ADMIN_PATH, changedSolrCoreData)
}

/** Jeder key bekommt seine neue Position, z. B. { DMS: 1, ARTIKEL: 2 }. Zurück kommen alle in neuer Reihenfolge. */
export function changeSolrCorePositions(newPositionByKey: Record<string, number>) {
  return request<SolrCore[]>('POST', `${SOLR_CORE_ADMIN_PATH}/position`, newPositionByKey)
}

/** Löscht den Kern aus unserer Tabelle. Was in Solr passiert, entscheidet der Abgleich. */
export function deleteSolrCore(key: string) {
  return request<SolrCore>('DELETE', SOLR_CORE_ADMIN_PATH, { key })
}
