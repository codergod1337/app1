import { useLocation } from 'react-router'

/** Die Suchmaske, ein Modul über alle Kerne wie in der Vorlage */
export const SEARCH_PATH = '/search'

/** Die UsersSetting mit den angewählten Kernen des Users, Komma-Liste ihrer Keys */
export const SEARCH_ACTIVE_CORES_SETTING_KEY = 'SEARCH_ACTIVE_CORES'

/** Mehr Wörter nimmt die Maske nicht an, eine Regel der Maske (User 2026-10-09: 5), das Backend zählt nicht */
export const SEARCH_MAX_WORDS = 5

/** STX und ETX, wie das Backend den getroffenen Teil eines Werts einrahmt (DmsSearchHitReason), aus dem Zeichencode gebaut */
export const SEARCH_MARK_OPEN = String.fromCharCode(2)
export const SEARCH_MARK_CLOSE = String.fromCharCode(3)

/** Ab so vielen Zeichen zählt ein Wort als Suchwort, damit nicht nach jedem ersten Buchstaben gesucht wird */
export const SEARCH_MIN_CHARACTERS = 3

/** Beginnt die Eingabe damit, ist sie keine Suche, sondern die Wahl einer Dateiart als Filter (nach der Vorlage) */
export const FILE_SUB_CLASS_MODE_PREFIX = 'fsc.'

/** Eine Pause beim Tippen, dann die Abfrage: eine je Tipp-Pause, nicht je Taste */
export const SEARCH_DEBOUNCE_MS = 250

/**
 * Ein eingeloggter Filter, ein Chip: Feld und exakter Wert, ohne Kern, denn er geht an jeden Kern, und wer das Feld
 * nicht kennt, überliest ihn. label und previewBase64Png sind nur Anzeige, woran man den Wert erkennt (der Name der
 * Dateiart, der Firmenname zur Kundennummer, Name und Vorschaubild zur Artikelnummer); sie wandern vom Angebot mit und
 * gehen in keine Abfrage.
 */
export interface SearchFilter {
  fieldName: string
  value: string
  label?: string
  previewBase64Png?: string
}

/** Was an jeden Kern geht (der Body von POST /api/rest/v1/solr/dms/search, spätere Kerne dieselbe Form) */
export interface SearchRequest {
  searchText: string
  /** die Chips je Feld gebündelt, gleiches Feld heißt ODER */
  exactFilterValuesByFieldName: Record<string, string[]>
}

/**
 * Warum ein Dokument ein Treffer ist (DmsSearchHitReason.java im Paket solr.dms.model): das Feld mit seinem
 * ursprünglichen Namen (bei einem Hook sein Key) und der Wert mit dem getroffenen Teil in STX und ETX. Beim Volltext
 * ist snippet null, der Text geht nie zum Client, dann wird nur der Feldname markiert.
 */
export interface SearchHitReason {
  fieldName: string
  snippet: string | null
}

/**
 * Ein Angebot über dem Eingabefeld (User 2026-10-09): ein Hook-Wert aus einem Grund der Treffer, den ein Suchwort
 * getroffen hat. Klick macht ihn zum Filter (fq) und nimmt das Suchwort aus dem Suchtext.
 */
export interface SearchOffer {
  coreKey: string
  fieldName: string
  value: string
  /** das Suchwort, das den Wert traf, wie es im Wert steht */
  word: string
}

/** Das Angebot aus einem Grund: nur Hooks, der Wert ohne Marken, das Wort zwischen den Marken; sonst null */
export function searchOfferOfReason(coreKey: string, reason: SearchHitReason, isHook: (fieldName: string) => boolean): SearchOffer | null {
  if (reason.snippet === null || !isHook(reason.fieldName)) {
    return null
  }
  const start = reason.snippet.indexOf(SEARCH_MARK_OPEN)
  const end = reason.snippet.indexOf(SEARCH_MARK_CLOSE)
  if (start < 0 || end <= start) {
    return null
  }
  return {
    coreKey,
    fieldName: reason.fieldName,
    value: reason.snippet.slice(0, start) + reason.snippet.slice(start + 1, end) + reason.snippet.slice(end + 1),
    word: reason.snippet.slice(start + 1, end),
  }
}

/** Der Suchtext ohne dieses Wort, Groß/Klein egal, ein + davor zählt nicht; was bleibt, mit einem Leerzeichen gefügt */
export function withoutSearchWord(searchText: string, word: string): string {
  const wanted = word.toLowerCase()
  return searchText
    .split(/\s+/)
    .filter((entry) => entry !== '' && entry.replace(/^\+/, '').toLowerCase() !== wanted)
    .join(' ')
}

/**
 * Ein Treffer (DmsSearchHit.java): die Felder der Trefferbox samt Vorschau und den durchsuchten Feldern, ob der User
 * das Dokument schreiben darf, und die Gründe in der Reihenfolge der Felder im Kern.
 */
export interface SearchHit {
  fieldValues: Record<string, unknown>
  writable: boolean
  reasons: SearchHitReason[]
}

/** Das Ergebnis eines Kerns (DmsSearchedDocumentsPage.java): Treffer insgesamt und die besten davon, keine Seiten */
export interface SearchResultPage {
  totalFound: number
  hits: SearchHit[]
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

/** Die Abfrage an einen Kern: Chips je Feld gebündelt (gleiches Feld ODER) */
export function searchRequestOf(searchText: string, filters: readonly SearchFilter[]): SearchRequest {
  const exactFilterValuesByFieldName: Record<string, string[]> = {}
  for (const filter of filters) {
    const values = (exactFilterValuesByFieldName[filter.fieldName] ??= [])
    if (!values.includes(filter.value)) {
      values.push(filter.value)
    }
  }
  return { searchText, exactFilterValuesByFieldName }
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
