import { request } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { jsonRowsOf } from '../../components/jsonRows.ts'
import type { ResponseMappingExecutionResult } from '../abasmanager/AbasSternzeit.ts'
import type { AbasBelegnummerLookupResult } from '../belegnummer/Belegnummer.ts'
import type { AbasInfosys } from '../infosys/AbasInfosys.ts'
import type { SearchRequest, SearchResultPage } from '../search/Search.ts'
import { ARTICLE_NUMBER_HOOK_KEYS } from '../solrmanager/SolrHook.ts'
import { type SolrField, solrFieldDisplayNameOf } from '../solrmanager/SolrField.ts'

/** Die AR, die das Artikel-Modul öffnet und die Mappe lesen lässt. Das Backend prüft dieselbe. */
export const ARTIKEL_ACCESS_ROLE_KEY = 'ARTIKEL'

/** Die AR der Suche im Kern ARTIKEL: Sie gibt in der Suche alle Felder und Hooks frei. Ohne sie 403 vor Solr, außer für ADMIN. */
export const ARTIKEL_SEARCH_ACCESS_ROLE_KEY = 'ARTIKEL_SUCHE'

/** Der Kern der Artikel im SolrManager (SolrDocumentArtikelService.CORE_KEY). Seine Felder aus den Stammdaten sind die Mappe. */
export const ARTIKEL_CORE_KEY = 'ARTIKEL'

export const ARTIKEL_PATH = '/artikel'

/**
 * Der Anzeigeort der Artikelmappe für Infosystem-Aufrufe: ein hinterlegter Aufruf mit diesem Key ist ein Tab, der live
 * bei ABAS nachlädt (AbasArtikelmappeNachladeService.java)
 */
export const ARTIKELMAPPE_DISPLAY_CONTEXT_KEY = 'ARTIKELMAPPE'

/** Der Anzeigeort des Dokumente-Tabs der Artikelmappe (User 2026-10-13): eine Dateiart mit diesem Key ist dort ein Abschnitt */
export const ARTIKELMAPPE_DOKUMENTE_DESTINATION_KEY = 'ARTIKELMAPPE_DOKUMENTE'

// ===== Die Felder des Kerns ARTIKEL, mit denen die Mappe rechnet. Normale Felder heißen wie in der ABAS-Antwort (User 2026-10-10). =====

/** Der Name des Artikels, ABAS-Feld name */
export const ARTIKEL_NAME_FIELD_NAME = 'name'

/** Der Suchbegriff, ABAS-Feld such */
export const ARTIKEL_SEARCH_TERM_FIELD_NAME = 'such'

/** Die Masternummer als normales Feld (Kaskade: ymasterartikel, beim Master die eigene Nummer) */
export const ARTIKEL_MASTER_FIELD_NAME = 'ymasterartikel'

/** Die Hooks der hauseigenen Nummern: artikelNummer trägt bei jedem Artikel die eigene, masterArtikelNummer die des Masters */
export const ARTIKEL_NUMBER_HOOK_NAME = ARTICLE_NUMBER_HOOK_KEYS[0]
export const ARTIKEL_MASTER_NUMBER_HOOK_NAME = ARTICLE_NUMBER_HOOK_KEYS[1]

/** Das Vorschaubild, Base64 ohne Präfix, vom Backend aus dem DMS */
export const ARTIKEL_PREVIEW_FIELD_NAME = 'artikelVorschauBildBase64Png'

/** Die Sync-Felder des Backends */
export const ARTIKEL_SYNC_DATE_FIELD_NAME = 'syncDatum'
export const ARTIKEL_SYNC_STATUS_FIELD_NAME = 'syncStatus'
export const ARTIKEL_SYNC_ERROR_FIELD_NAME = 'syncFehler'

/** Die Sternzeit als Hook, in jedem Kern dasselbe Feld */
export const ARTIKEL_STERNZEIT_HOOK_NAME = 'sternzeit'

/** Das Ziel des Mappings, das den Kern ARTIKEL füllt (SolrDocumentArtikelService.TARGET_KEY): daran erkennt die Belegnummer einen Artikel */
export const ARTIKEL_TARGET_KEY = 'SOLR_ARTIKEL'

/** Das Feld mit der Sternzeit der Prüfeinstellung (71:2), die Soll-Seite des Tabs „Prüfungen“, live nachgeladen */
export const ARTIKEL_INSPECTION_PROCEDURE_FIELD_NAME = 'yqmappqmpruefv'

/** Der hinterlegte Aufruf mit den durchgeführten Prüfungen (owqmapp/QMPRUEFAUFTRAEGE), die Ist-Seite des Tabs „Prüfungen“ */
export const ARTIKEL_INSPECTION_ORDERS_ABAS_INFOSYS_KEY = 'QMPRUEFAUFTRAEGE'

/** Die festen Tabs der Mappe, in dieser Reihenfolge; das Segment in der Adresse ist der Schlüssel */
export const ARTIKEL_TABS = [
  'overview',
  'documents',
  'bom',
  'operations',
  'tools',
  'alternatives',
  'goesWith',
  'requires',
  'customer',
  'supplier',
  'inspections',
  'details',
] as const

export type ArtikelTab = (typeof ARTIKEL_TABS)[number]

export function isArtikelTab(value: string | undefined): value is ArtikelTab {
  return value !== undefined && (ARTIKEL_TABS as readonly string[]).includes(value)
}

/**
 * Das Segment eines Tabs, der ein hinterlegter Aufruf mit dem Anzeigeort ARTIKELMAPPE ist (User 2026-10-11): der Key
 * klein geschrieben, prodlist. Diese Tabs kommen zu den festen dazu, vor den Details.
 */
export function artikelInfosysTabKey(abasInfosysKey: string): string {
  return abasInfosysKey.toLowerCase()
}

/** Die Mappe eines Artikels, Übersicht */
export function artikelPath(artikelNummer: string): string {
  return `${ARTIKEL_PATH}/${encodeURIComponent(artikelNummer)}`
}

/** Ein Tab der Mappe, fest oder ein nachgeladener Aufruf */
export function artikelTabPath(artikelNummer: string, tab: string): string {
  return tab === 'overview' ? artikelPath(artikelNummer) : `${artikelPath(artikelNummer)}/${tab}`
}

/**
 * Die Beschriftung eines Feldes des Kerns ARTIKEL in der Mappe: der Anzeigename aus der Felddefinition, denn die
 * technischen Namen sind die ABAS-Kürzel (such, vbez, zmart); fehlt er, der Rückfall, sonst der Name selbst.
 */
export function artikelFieldLabel(solrFields: readonly SolrField[] | null, name: string, fallback?: string, language?: LanguageCode): string {
  return solrFieldDisplayNameOf(solrFields ?? [], ARTIKEL_CORE_KEY, name, language) ?? fallback ?? name
}

/** Eine Liste, egal ob Solr einen Wert oder mehrere liefert; leer bei nichts */
export function artikelValuesOf(value: unknown): string[] {
  if (value === null || value === undefined) {
    return []
  }
  return (Array.isArray(value) ? value : [value]).map((entry) => String(entry))
}

/** Der erste Wert eines Feldes als Text, leer bei nichts */
export function artikelValueOf(document: Record<string, unknown>, fieldName: string): string {
  const values = artikelValuesOf(document[fieldName])
  return values.length === 0 ? '' : values[0]
}

/**
 * Trägt das Dokument in diesem Feld etwas? Fehlt das Feld, darf der User es nicht lesen oder der Artikel hat nichts
 * darin, beides heißt nein. Ein Schalter auf false zählt als nichts (User 2026-10-13, Tabs nur mit Recht und Daten).
 */
export function artikelFieldHasValue(document: Record<string, unknown>, fieldName: string): boolean {
  return artikelValuesOf(document[fieldName]).some((value) => value.trim() !== '' && value.trim() !== 'false')
}

/**
 * Index-parallele Listen als Zeilen, nach paare() der Artikelmappe der Vorlage: die Felder einer Tabelle (Stückliste,
 * Arbeitsgänge, Kunden) sind je ein multiValued-Feld, Zeile i ist der i-te Wert jedes Feldes; eine kürzere Liste füllt
 * mit leerem Text auf.
 */
export function artikelPairRows(document: Record<string, unknown>, fieldNames: readonly string[]): string[][] {
  const lists = fieldNames.map((fieldName) => artikelValuesOf(document[fieldName]))
  const length = Math.max(0, ...lists.map((list) => list.length))
  const rows: string[][] = []
  for (let index = 0; index < length; index++) {
    rows.push(lists.map((list) => list[index] ?? ''))
  }
  return rows
}

/** Die Zeilen eines Tabellenfeldes (User 2026-10-10: eine Tabelle in einem Feld): jeder Wert ein JSON-Objekt als Text */
export function artikelTableRowsOf(document: Record<string, unknown>, fieldName: string): Record<string, unknown>[] {
  return jsonRowsOf(artikelValuesOf(document[fieldName]))
}

/** Die Masternummer eines Artikels aus dem Hook, leer ohne Familie */
export function artikelMasterNumberOf(document: Record<string, unknown>): string {
  return artikelValueOf(document, ARTIKEL_MASTER_NUMBER_HOOK_NAME).trim()
}

// ===== Endpunkte des SolrDocumentArtikelController (solr/artikel) =====

/**
 * Die Artikelmappe (ArtikelDocumentForClient.java): das Dokument mit den Feldern, die der User lesen darf, und seine
 * Familie, alle Artikel mit derselben Masternummer, der Master darunter. 404, wenn es den Artikel nicht gibt oder der
 * User ihn nicht sehen darf.
 */
export interface ArtikelDocumentForClient {
  document: Record<string, unknown>
  masterGroup: Record<string, unknown>[]
}

/** Was der current user im Kern ARTIKEL darf (ArtikelAccessForClient.java), nur Anzeige */
export interface CurrentUserArtikelAccessForClient {
  coreKey: string
  everyFieldAndHookOfCoreReadableByCurrentUser: boolean
  freeTextSearchAllowed: boolean
  readableFieldAndHookNamesOfCoreForCurrentUser: string[]
  exactResolveAllowedFieldAndHookNamesOfCoreForCurrentUser: string[]
}

/** Eine Trefferseite der Auflösung (ArtikelResolvedDocumentsPage.java) */
export interface ArtikelResolvedDocumentsPage {
  totalFound: number
  page: number
  pageSize: number
  documents: Record<string, unknown>[]
}

/**
 * Die vier Arten einer Verbindung zwischen Artikeln (ArtikelReferenceFlag.java, User 2026-10-13): die Werte von
 * yartreferenz je Zeile des Referenzobjekts. WERKZEUG und BENOETIGT sind gerichtet, GEHTMIT und ALTERNATIVE beidseitig,
 * in ABAS aber nur auf einer Seite eingetragen.
 */
export type ArtikelReferenceFlag = 'WERKZEUG' | 'GEHTMIT' | 'ALTERNATIVE' | 'BENOETIGT'

/** Welcher Tab der Mappe welches Flag zeigt */
export const ARTIKEL_REFERENCE_FLAG_BY_TAB: Partial<Record<ArtikelTab, ArtikelReferenceFlag>> = {
  tools: 'WERKZEUG',
  alternatives: 'ALTERNATIVE',
  goesWith: 'GEHTMIT',
  requires: 'BENOETIGT',
}

/**
 * Ein Treffer der aufaggregierten Suche (ArtikelAggregatedReference.java): distance 1 heißt direkt am Artikel
 * hinterlegt, 2 über eine Alternative; pathArtikelNummern ist der Weg vom Startartikel bis zu dem Artikel, an dem die
 * Beziehung hängt; foundByReverseLookup heißt, der Treffer führt den letzten Artikel des Weges in seiner Liste
 */
export interface ArtikelAggregatedReference {
  artikelNummer: string
  sternzeit: string
  name: string
  distance: number
  pathArtikelNummern: string[]
  foundByReverseLookup: boolean
}

/** Was der Tab eines Flags bekommt (ArtikelAggregatedReferencesForClient.java): aus der Ablage oder gerade gerechnet */
export interface ArtikelAggregatedReferencesForClient {
  artikelNummer: string
  flag: ArtikelReferenceFlag
  alternativeDepth: number
  aggregatedAt: string
  references: ArtikelAggregatedReference[]
  truncated: boolean
}

export function getArtikelDocument(artikelNummer: string) {
  return request<ArtikelDocumentForClient>('GET', `/api/rest/v1/solr/artikel/document/${encodeURIComponent(artikelNummer)}`)
}

/**
 * Die aufaggregierten Verbindungen eines Flags bis zur Tiefe über Alternativen: aus der Ablage im Dokument, wenn dort
 * ein Stand mit mindestens dieser Tiefe liegt, sonst gerechnet und abgelegt. 403, wenn das Feld des Flags nicht lesbar ist.
 */
export function getArtikelAggregatedReferences(artikelNummer: string, flag: ArtikelReferenceFlag, alternativeDepth: number) {
  return request<ArtikelAggregatedReferencesForClient>('POST', '/api/rest/v1/solr/artikel/references', { artikelNummer, flag, alternativeDepth })
}

/** Dasselbe, immer neu gerechnet und abgelegt: der Knopf „neu suchen“ */
export function recomputeArtikelAggregatedReferences(artikelNummer: string, flag: ArtikelReferenceFlag, alternativeDepth: number) {
  return request<ArtikelAggregatedReferencesForClient>('POST', '/api/rest/v1/solr/artikel/references/recompute', { artikelNummer, flag, alternativeDepth })
}

export function getCurrentUserArtikelAccess() {
  return request<CurrentUserArtikelAccessForClient>('GET', '/api/rest/v1/solr/artikel/access')
}

/** Auflösen nach exakten Werten, ein Wert je Feld, Seiten ab 0 mit der Größe aus der YAML des Backends */
export function resolveArtikelDocuments(exactValueByFieldName: Record<string, string>, page = 0) {
  return request<ArtikelResolvedDocumentsPage>('POST', '/api/rest/v1/solr/artikel/resolve', { exactValueByFieldName, page })
}

/**
 * Die Suche im Kern ARTIKEL (ArtikelSearchedDocumentsPage.java): Tür ARTIKEL_SUCHE oder ADMIN, alle Felder und Hooks,
 * mit Treffergrund. Die Treffer tragen kein Schreibflag, im Spiegelkern schreibt kein Mensch: für die eine Suchmaske
 * zählt jeder Treffer als nicht schreibbar.
 */
// ===== Endpunkte des AbasArtikelmappeController (abas): das Nachladen live bei ABAS, User 2026-10-11 =====

const ARTIKELMAPPE_NACHLADEN_PATH = '/api/rest/v1/abas/artikelmappe'

/** Die hinterlegten Aufrufe mit dem Anzeigeort ARTIKELMAPPE: je einer ein Tab der Mappe. Tür ARTIKEL. */
export function getArtikelmappeInfosysList() {
  return request<AbasInfosys[]>('GET', `${ARTIKELMAPPE_NACHLADEN_PATH}/infosys`)
}

/**
 * Einen hinterlegten Aufruf für den Artikel ausführen, die Platzhalter füllt das Backend aus dem Dokument; die Antwort
 * kommt zerlegt über das Mapping ihres Typs, Tabellen-Einträge je Zeile ein JSON-Objekt als Text
 */
export function fetchArtikelmappeInfosys(artikelNummer: string, abasInfosysKey: string) {
  return request<ResponseMappingExecutionResult>('POST', `${ARTIKELMAPPE_NACHLADEN_PATH}/infosys`, { artikelNummer, abasInfosysKey })
}

/** Das Objekt hinter der Sternzeit in einem Feld des Artikels, zerlegt über das Mapping seines Typs */
export function fetchArtikelmappeSternzeit(artikelNummer: string, fieldName: string) {
  return request<ResponseMappingExecutionResult>('POST', `${ARTIKELMAPPE_NACHLADEN_PATH}/sternzeit`, { artikelNummer, fieldName })
}

/**
 * Einen Artikel, den der Kern nicht kennt, über die Belegnummer bei ABAS suchen und in den Kern holen (User 2026-10-13);
 * die Mappe ruft das selbst bei 404. Die primaryId der Antwort ist die Nummer, wie sie jetzt im Kern steht. 404, wenn
 * ABAS keinen Artikel dazu kennt.
 */
export function fetchArtikelIntoCore(artikelNummer: string) {
  return request<AbasBelegnummerLookupResult>('POST', `${ARTIKELMAPPE_NACHLADEN_PATH}/artikel`, { artikelNummer })
}

export function searchArtikelDocuments(searchRequest: SearchRequest) {
  return request<{ totalFound: number; hits: { fieldValues: Record<string, unknown>; reasons: { fieldName: string; snippet: string | null }[] }[] }>(
    'POST',
    '/api/rest/v1/solr/artikel/search',
    searchRequest,
  ).then((result) => ({
    ...result,
    data: {
      totalFound: result.data.totalFound,
      hits: result.data.hits.map((hit) => ({ fieldValues: hit.fieldValues, writable: false, reasons: hit.reasons })),
    } satisfies SearchResultPage,
  }))
}
