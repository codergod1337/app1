import { request } from '../../api/client.ts'
import i18n from '../../branding/i18n.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { translate } from '../../components/multilingual.ts'
import type { SolrFieldSetting, SolrFieldType } from './SolrField.ts'
import type { SolrHookGroup } from './SolrHookGroup.ts'

/**
 * Ein Hook, wie ihn das Backend liefert (SolrHook.java): eine Adresse, über die ein Solr-Dokument auf ein Fachobjekt
 * zeigt. Jeder Kern bekommt dieselben Hooks. Kein Aussehen am Hook: Alle Chips (SolrHookChip) sehen gleich aus, der
 * Look steht fest in basic.css. Der Hook trägt keine Rechte: Wer HOOK_FULL hat, darf jeden benutzen, alle anderen nur,
 * was ihnen je Hook und Wert freigegeben ist. Gelesen wird über die Stammdaten.
 */
export interface SolrHook {
  /** der Name des Feldes in Solr, z. B. artikelNummer, ändert sich nie */
  key: string
  /** mehrsprachiges JSON, z. B. {"de":"Artikelnummer"} */
  displayName: string
  /** mehrsprachiges JSON wie displayName */
  description: string | null
  /** der key der Gruppe (SolrHookGroup), nur für die Anzeige. null: ohne Gruppe */
  hookGroupKey: string | null
  listingPosition: number
}

/** Die Hooks einer Gruppe, in Listenreihenfolge. solrHookGroup null: die Hooks ohne Gruppe, immer zuletzt. */
export interface SolrHookGroupWithHooks {
  solrHookGroup: SolrHookGroup | null
  solrHooks: SolrHook[]
}

/**
 * Teilt die Hooks nach Gruppe auf: die Gruppen in ihrer Listenreihenfolge, darin die Hooks in ihrer. Leere Gruppen
 * fallen weg. Hooks ohne Gruppe oder mit einem key, den es nicht mehr gibt, stehen zuletzt unter null.
 */
export function groupSolrHooks(solrHooks: SolrHook[], solrHookGroups: SolrHookGroup[]): SolrHookGroupWithHooks[] {
  const groups: SolrHookGroupWithHooks[] = solrHookGroups.map((solrHookGroup) => ({
    solrHookGroup,
    solrHooks: solrHooks.filter((solrHook) => solrHook.hookGroupKey === solrHookGroup.key),
  }))
  const knownKeys = new Set(solrHookGroups.map((solrHookGroup) => solrHookGroup.key))
  const ungrouped = solrHooks.filter(
    (solrHook) => solrHook.hookGroupKey === null || !knownKeys.has(solrHook.hookGroupKey),
  )
  if (ungrouped.length > 0) {
    groups.push({ solrHookGroup: null, solrHooks: ungrouped })
  }
  return groups.filter((group) => group.solrHooks.length > 0)
}

/** Der Anzeigename einer Gruppe in der Sprache, bei null „ohne Gruppe“ aus dem Katalog */
export function solrHookGroupLabel(solrHookGroup: SolrHookGroup | null, language?: LanguageCode): string {
  return solrHookGroup
    ? translate(solrHookGroup.displayName, language) || solrHookGroup.key
    : i18n.t('solrmanager.hooks.form.noGroup')
}

/**
 * Die hauseigenen Artikelnummern, nach ARTIKEL_NUMMER_FELDER der Vorlage: Ihr Wert ist eine Nummer unseres
 * Artikelstamms und hat eine eigene Artikelmappe. Fremde Nummern (Kunde, Hersteller, Lieferant, EAN) haben keine.
 */
export const ARTICLE_NUMBER_HOOK_KEYS: readonly string[] = ['artikelNummer', 'masterArtikelNummer']

/**
 * Wohin ein Hook-Wert führt, nach hookWeg der Vorlage: eine hauseigene Artikelnummer in ihre Mappe, nicht in die Suche,
 * denn zu ihr gibt es genau einen Ort. Alles andere geht später in die Suche mit sich selbst als Filter, bis es die
 * Suche gibt null, also kein Link.
 */
export function solrHookValuePath(hookKey: string, value: string): string | null {
  return ARTICLE_NUMBER_HOOK_KEYS.includes(hookKey) ? `/artikel/${encodeURIComponent(value)}` : null
}

/** In Solr ist jeder Hook in jedem Kern dasselbe Feld: ein string, der exakte Wert (SolrHook.java, FIELD_TYPE) */
export const SOLR_HOOK_FIELD_TYPE: SolrFieldType = 'STRING'

/**
 * Die Schalter, die bei jedem Hook an sind (SolrHook.java, FIELD_*): indexed zum Filtern, stored für die Chips,
 * multiValued weil ein Dokument auf mehrere Fachobjekte zeigen darf, docValues für Facetten mit Zählern, suggest für
 * die Vorschläge beim Tippen. required fehlt: Jeder Hook ist je Dokument optional. Deshalb hat ein Hook keine Schalter
 * in der Tabelle, der Kern-Tab beschreibt sie nur (SolrHookFieldNote).
 */
export const SOLR_HOOK_FIELD_SETTINGS: readonly SolrFieldSetting[] = [
  'indexed',
  'stored',
  'multiValued',
  'docValues',
  'suggest',
]

// ===== Endpunkte des SolrHookAdminController. Gelesen wird über die Stammdaten (useMasterData). =====

const SOLR_HOOK_ADMIN_PATH = '/api/rest/v1/admin/solr/hook'

/** Neuer Hook */
export function createSolrHook(newSolrHookData: SolrHook) {
  return request<SolrHook>('POST', SOLR_HOOK_ADMIN_PATH, newSolrHookData)
}

/** Alle Felder, der key bestimmt den Hook und ändert sich nie. */
export function updateSolrHook(changedSolrHookData: SolrHook) {
  return request<SolrHook>('PUT', SOLR_HOOK_ADMIN_PATH, changedSolrHookData)
}

/** Jeder key bekommt seine neue Position, z. B. { artikelNummer: 1, kundenNummer: 2 }. Zurück kommen alle in neuer Reihenfolge. */
export function changeSolrHookPositions(newPositionByKey: Record<string, number>) {
  return request<SolrHook[]>('POST', `${SOLR_HOOK_ADMIN_PATH}/position`, newPositionByKey)
}

/** Löscht endgültig. Was in den Kernen mit dem Feld passiert, entscheidet der Abgleich mit Solr. */
export function deleteSolrHook(key: string) {
  return request<SolrHook>('DELETE', SOLR_HOOK_ADMIN_PATH, { key })
}
