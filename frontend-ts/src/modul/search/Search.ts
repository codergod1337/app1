import { useLocation } from 'react-router'

/** Die Suchmaske, ein Modul über alle Kerne wie in der Vorlage */
export const SEARCH_PATH = '/search'

/** Die UsersSetting mit den angewählten Kernen des Users, Komma-Liste ihrer Keys */
export const SEARCH_ACTIVE_CORES_SETTING_KEY = 'SEARCH_ACTIVE_CORES'

/** Mehr Wörter nimmt die Maske nicht an, dieselbe Zahl wie SolrSearchText.MAX_WORDS im Backend (User 2026-10-07) */
export const SEARCH_MAX_WORDS = 6

/** Ab so vielen Zeichen zählt ein Wort als Suchwort (SolrSearchText.MIN_CHARACTERS_OF_SEARCH_WORD) */
export const SEARCH_MIN_CHARACTERS = 3

export const SEARCH_PAGE_SIZE = 25

/** So viele Seiten sind blätterbar (DmsSearchService.MAX_PAGES), dahinter sagt die Maske, dass man enger suchen muss */
export const SEARCH_MAX_PAGES = 25

/** Beginnt die Eingabe damit, ist sie keine Suche, sondern die Wahl einer Dateiart als Filter (nach der Vorlage) */
export const FILE_SUB_CLASS_MODE_PREFIX = 'fsc.'

/** Eine Pause beim Tippen, dann die Abfrage: eine je Tipp-Pause, nicht je Taste */
export const SEARCH_DEBOUNCE_MS = 250

/**
 * Ein eingeloggter Filter, ein Chip: Feld und exakter Wert, ohne Kern, denn er geht an jeden Kern, und wer das Feld
 * nicht kennt, überliest ihn. label ist nur Anzeige, z. B. der Name der Dateiart zum key.
 */
export interface SearchFilter {
  fieldName: string
  value: string
  label?: string
}

/** Ein Zeitraum als Filter, nur für Datumsfelder: von und bis als Tag (JJJJ-MM-TT), eins darf fehlen */
export interface SearchDateRangeFilter {
  fieldName: string
  from: string | null
  to: string | null
}

/** Was an jeden Kern geht (DmsSearchRequest.java und die Gegenstücke der späteren Kerne, dieselbe Form) */
export interface SearchRequest {
  searchText: string
  exactFilterValuesByFieldName: Record<string, string[]>
  dateRangeFilterByFieldName: Record<string, { from: string | null; to: string | null }>
  page: number
  size: number
}

/** Warum ein Kern nichts gefragt hat (DmsSearchHint.java), die Maske übersetzt */
export type SearchHint = 'NO_SEARCH_WORD_AND_NO_FILTER' | 'TOO_MANY_SEARCH_WORDS'

/**
 * Warum ein Dokument ein Treffer ist (DmsSearchHitReason.java), nach dem Treffergrund der Vorlage: das Feld und sein
 * Wert mit den Fundstellen in STX und ETX; snippet null beim Volltext, der nie zum Client geht, dann steht nur das Feld.
 */
export interface SearchHitReason {
  fieldName: string
  snippet: string | null
}

/** Ein Treffer (DmsSearchHit.java): das Dokument und sein Treffergrund, Anfangstreffer zuerst, dann Filter, zuletzt der Volltext */
export interface SearchHit {
  document: Record<string, unknown>
  reasons: SearchHitReason[]
}

/** Eine Trefferseite eines Kerns (DmsSearchResultPage.java), auch leer, nie ein Fehler */
export interface SearchResultPage {
  totalFound: number
  page: number
  size: number
  hits: SearchHit[]
  hint: SearchHint | null
}

/**
 * Ein Filterangebot der Vervollständigung (DmsSearchFilterOffer.java): der ganze Wert eines filterbaren Felds aus
 * einem Treffer, dessen Anfang ein Suchwort trifft. label ist ein Zusatz zur Anzeige (beim Dateinamen der key der
 * Dateiart), imagePreviewBase64Png das Vorschaubild des Treffers, beides darf fehlen.
 */
export interface SearchFilterOffer {
  fieldName: string
  value: string
  label: string | null
  imagePreviewBase64Png: string | null
}

/** Die Antwort der Vervollständigung eines Kerns (DmsSuggestResult.java): kompakte Treffer und die Angebote */
export interface SearchSuggestResult {
  hits: SearchHit[]
  filterOffers: SearchFilterOffer[]
}

/** Ein Angebot mit dem Kern, aus dem es stammt, für Reihenfolge und später die Kernfarbe */
export interface SearchCoreFilterOffer {
  coreKey: string
  offer: SearchFilterOffer
}

/** Vorbelegung der Maske von anderswo, per Router-State, nie als URL-Parameter: z. B. ein Hook-Wert als Filter */
export interface SearchPrefill {
  searchText?: string
  filters?: SearchFilter[]
}

export interface SearchPrefillState {
  searchPrefill: SearchPrefill
}

/** Der State für navigate(SEARCH_PATH, { state }): die Suche mit diesen Filtern aufmachen */
export function searchPrefillStateOf(filters: SearchFilter[], searchText = ''): SearchPrefillState {
  return { searchPrefill: { filters, searchText } }
}

/** Die mitgegebene Vorbelegung, null ohne (direkter Aufruf, Kachel, Header) */
export function useSearchPrefill(): SearchPrefill | null {
  const state: unknown = useLocation().state
  if (typeof state !== 'object' || state === null || !('searchPrefill' in state)) {
    return null
  }
  const prefill: unknown = state.searchPrefill
  if (typeof prefill !== 'object' || prefill === null) {
    return null
  }
  const result: SearchPrefill = {}
  if ('searchText' in prefill && typeof prefill.searchText === 'string') {
    result.searchText = prefill.searchText
  }
  if ('filters' in prefill && Array.isArray(prefill.filters)) {
    result.filters = prefill.filters.filter(
      (entry): entry is SearchFilter =>
        typeof entry === 'object' &&
        entry !== null &&
        'fieldName' in entry &&
        'value' in entry &&
        typeof entry.fieldName === 'string' &&
        typeof entry.value === 'string',
    )
  }
  return result
}

/** Die Wörter der Eingabe wie das Backend sie zählt: an Leerraum getrennt, ein nacktes + oder - ist kein Wort */
export function searchWordsOf(searchText: string): string[] {
  return searchText
    .trim()
    .split(/\s+/)
    .filter((word) => word !== '' && word !== '+' && word !== '-')
}

/** Gibt es ein Suchwort: eins ohne führendes -, mit mindestens SEARCH_MIN_CHARACTERS Zeichen hinter dem Präfix */
export function hasSearchWord(searchText: string): boolean {
  return searchWordsOf(searchText).some((word) => !word.startsWith('-') && word.replace(/^\+/, '').length >= SEARCH_MIN_CHARACTERS)
}

/** Die Abfrage an einen Kern: Chips je Feld gebündelt (gleiches Feld ODER), Zeiträume je Feld */
export function searchRequestOf(
  searchText: string,
  filters: readonly SearchFilter[],
  dateRanges: readonly SearchDateRangeFilter[],
  page: number,
): SearchRequest {
  const exactFilterValuesByFieldName: Record<string, string[]> = {}
  for (const filter of filters) {
    const values = (exactFilterValuesByFieldName[filter.fieldName] ??= [])
    if (!values.includes(filter.value)) {
      values.push(filter.value)
    }
  }
  const dateRangeFilterByFieldName: Record<string, { from: string | null; to: string | null }> = {}
  for (const range of dateRanges) {
    dateRangeFilterByFieldName[range.fieldName] = { from: range.from, to: range.to }
  }
  return { searchText, exactFilterValuesByFieldName, dateRangeFilterByFieldName, page, size: SEARCH_PAGE_SIZE }
}

/** Zwei Chips sind derselbe Filter, wenn Feld und Wert gleich sind */
export function isSameSearchFilter(a: SearchFilter, b: SearchFilter): boolean {
  return a.fieldName === b.fieldName && a.value === b.value
}

// ===== Der Zustand der Suche in der Adresse (User 2026-10-07): Suchwörter und Chips, damit Zurück aus einem Treffer sie wiederherstellt =====

/** Die Suchwörter: /search?q=4711 */
export const SEARCH_TEXT_PARAM = 'q'

/** Je Chip ein f=feld:wert, mehrere in Reihenfolge; der erste Doppelpunkt trennt, ein Feldname hat keinen */
export const SEARCH_FILTER_PARAM = 'f'

/** Suchwörter und Chips als Adressparameter, leer bleibt weg */
export function searchParamsOf(searchText: string, filters: readonly SearchFilter[]): URLSearchParams {
  const params = new URLSearchParams()
  if (searchText !== '') {
    params.set(SEARCH_TEXT_PARAM, searchText)
  }
  for (const filter of filters) {
    params.append(SEARCH_FILTER_PARAM, `${filter.fieldName}:${filter.value}`)
  }
  return params
}

/** Die Chips aus den Adressparametern, Unbrauchbares und Doppeltes fällt weg. Die Beschriftung einer Dateiart holt die Anzeige aus den Stammdaten. */
export function searchFiltersOfParams(params: URLSearchParams): SearchFilter[] {
  const filters: SearchFilter[] = []
  for (const entry of params.getAll(SEARCH_FILTER_PARAM)) {
    const colon = entry.indexOf(':')
    if (colon <= 0 || colon === entry.length - 1) {
      continue
    }
    const filter = { fieldName: entry.slice(0, colon), value: entry.slice(colon + 1) }
    if (!filters.some((existing) => isSameSearchFilter(existing, filter))) {
      filters.push(filter)
    }
  }
  return filters
}
