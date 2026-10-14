import { request } from '../../api/client.ts'
import i18n from '../../branding/i18n.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { translate } from '../../components/multilingual.ts'
import { kontakteMappePath } from '../kontakte/Kontakte.ts'
import { SEARCH_PATH, searchPrefillStateOf, type SearchPrefillState } from '../search/Search.ts'
import type { SolrFieldSetting, SolrFieldType } from './SolrField.ts'
import type { SolrHookGroup } from './SolrHookGroup.ts'

/**
 * Ein Hook, wie ihn das Backend liefert (SolrHook.java): eine Adresse, über die ein Solr-Dokument auf ein Fachobjekt
 * zeigt, hier nur die Definition. Jeder Kern bekommt dieselben Hooks: In jedem Kern liegt der Hook als Zeile der
 * Feldtabelle (SolrField.hookKey), dort stehen je Kern Leseliste und Suchgewicht, der Kern-Tab zeigt sie unter den
 * Feldern. Kein Aussehen am Hook: Alle Chips (SolrHookChip) sehen gleich aus, der Look steht fest in basic.css.
 * Gelesen wird über die Stammdaten.
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
  /** wohin ein Badge mit einem Wert dieses Hooks führt (SolrHookValueBadgeLinkTarget.java), nur für das Frontend */
  valueBadgeLinkTarget: SolrHookValueBadgeLinkTarget
  listingPosition: number
  /**
   * die ABAS-Typen, deren Nummern in diesen Hook gehören (User 2026-10-14), z. B. obj:data:3:1: Findet das Archivieren
   * eine Nummer dieses Typs, steht sie vorbelegt in diesem Hook
   */
  abasObjectTypes: string[] | null
}

/**
 * Wohin ein Badge mit dem Wert eines Hooks führt (User 2026-10-13), gepflegt je Hook im SolrManager, gelesen über die
 * Stammdaten. Es steht nur an der Definition des Hooks, nie in den Kernen. Ein neues Ziel ist ein weiterer Wert hier,
 * im Enum des Backends und in solrHookValueLink.
 */
export const SOLR_HOOK_VALUE_BADGE_LINK_TARGETS = ['SEARCH', 'ARTIKELMAPPE', 'KUNDENMAPPE', 'LIEFERANTENMAPPE'] as const

export type SolrHookValueBadgeLinkTarget = (typeof SOLR_HOOK_VALUE_BADGE_LINK_TARGETS)[number]

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

/**
 * Die Hooks eines Dokuments, wie das Hook-Modul (SolrHookFields) sie nimmt: je Hook-Key seine Werte, nur die Hooks,
 * die im Dokument stehen und Werte haben. Alles andere im Dokument sind normale Felder und bleiben außen vor.
 */
export function solrHookValuesOf(
  document: Readonly<Record<string, unknown>>,
  solrHooks: readonly SolrHook[],
): Record<string, string[]> {
  const hooks: Record<string, string[]> = {}
  for (const solrHook of solrHooks) {
    const values = document[solrHook.key]
    if (Array.isArray(values) && values.length > 0) {
      hooks[solrHook.key] = values.map(String)
    }
  }
  return hooks
}

/** Der Anzeigename einer Gruppe in der Sprache, bei null „ohne Gruppe“ aus dem Katalog */
export function solrHookGroupLabel(solrHookGroup: SolrHookGroup | null, language?: LanguageCode): string {
  return solrHookGroup
    ? translate(solrHookGroup.displayName, language) || solrHookGroup.key
    : i18n.t('solrmanager.hooks.form.noGroup')
}

/**
 * Die Hooks der hauseigenen Artikelnummern, nach ARTIKEL_NUMMER_FELDER der Vorlage: Ihr Wert ist eine Nummer unseres
 * Artikelstamms. Die Artikelmappe liest damit ihre Nummern; wohin ein Badge führt, sagt seit 2026-10-13 das Ziel am
 * Hook (valueBadgeLinkTarget), nicht diese Liste.
 */
export const ARTICLE_NUMBER_HOOK_KEYS: readonly string[] = ['artikelNummer', 'masterArtikelNummer']

/** Wohin ein Hook-Wert führt: der Pfad und, für die Suche, die Vorbelegung als Router-State */
export interface SolrHookValueLink {
  to: string
  state?: SearchPrefillState
}

/**
 * Wohin ein Hook-Wert führt, nach hookWeg der Vorlage, entschieden vom Ziel am Hook (User 2026-10-13): eine Nummer mit
 * eigener Mappe in diese Mappe, denn zu ihr gibt es genau einen Ort; alles andere in die Suche mit sich selbst als
 * Filter. Die Vorbelegung der Suche reist als Router-State, nie in der Adresse. Der Pfad der Artikelmappe steht hier als
 * Text, denn Artikel.ts liest diese Datei.
 */
export function solrHookValueLink(solrHook: Pick<SolrHook, 'key' | 'valueBadgeLinkTarget'>, value: string): SolrHookValueLink {
  switch (solrHook.valueBadgeLinkTarget) {
    case 'ARTIKELMAPPE':
      return { to: `/artikel/${encodeURIComponent(value)}` }
    case 'KUNDENMAPPE':
      return { to: kontakteMappePath('KUNDE', value) }
    case 'LIEFERANTENMAPPE':
      return { to: kontakteMappePath('LIEFERANT', value) }
    default:
      return { to: SEARCH_PATH, state: searchPrefillStateOf([{ fieldName: solrHook.key, value }]) }
  }
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
