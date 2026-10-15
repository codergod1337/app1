import { useLocation } from 'react-router'
import type { SolrHook } from '../solrmanager/SolrHook.ts'

/** Die Suchmaske, ein Modul über alle Kerne wie in der Vorlage */
export const SEARCH_PATH = '/search'

/** Die UsersSetting mit den angewählten Kernen des Users, Komma-Liste ihrer Keys */
export const SEARCH_ACTIVE_CORES_SETTING_KEY = 'SEARCH_ACTIVE_CORES'

/**
 * Die UsersSetting des Schalters über dem Suchfeld (User 2026-10-14): "true" jedes Wort muss vorhanden sein, "false"
 * Hauptsache ein Treffer. Ohne Eintrag an.
 */
export const SEARCH_ALL_WORDS_SETTING_KEY = 'SEARCH_ALL_WORDS_REQUIRED'

/** Mehr Wörter nimmt die Maske nicht an, eine Regel der Maske (User 2026-10-09: 5), das Backend zählt nicht */
export const SEARCH_MAX_WORDS = 5

/** STX und ETX, wie das Backend den getroffenen Teil eines Werts einrahmt (DmsSearchHitReason), aus dem Zeichencode gebaut */
export const SEARCH_MARK_OPEN = String.fromCharCode(2)
export const SEARCH_MARK_CLOSE = String.fromCharCode(3)

/** Ab so vielen Zeichen zählt ein Wort als Suchwort, damit nicht nach jedem ersten Buchstaben gesucht wird */
export const SEARCH_MIN_CHARACTERS = 3

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
  /** die Seite ab 0; „weitere laden“ fragt die nächste (User 2026-10-14) */
  page: number
  /** jedes Wort muss im Dokument vorkommen, ein - schließt aus; false: ein Wort reicht (User 2026-10-14) */
  allWordsRequired: boolean
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
 * Ein Filter-Angebot über dem Eingabefeld (User 2026-10-09, mit Zusatz und Bild seit 2026-10-13 nach der Vorlage):
 * Feld und Wert aus einem Treffer, dazu woran man den Wert erkennt. Klick oder Tab macht ihn zum Filter (fq). Jeder Kern
 * baut seine Angebote selbst (SearchCore.offersOf).
 */
export interface SearchOffer {
  /** der Kern, aus dessen Treffer das Angebot stammt */
  coreKey: string
  fieldName: string
  value: string
  /** das Suchwort, das den Wert traf: Es geht beim Übernehmen aus dem Suchtext. null: Der Wert kam ohne Suchwort, die Eingabe wird geleert wie in der Vorlage. */
  word: string | null
  /** woran man den Wert erkennt, z. B. der Firmenname zur Kundennummer, der Artikelname zur Artikelnummer; null ohne */
  additionText: string | null
  /** ein Vorschaubild als Base64-PNG ohne Präfix, z. B. das des Artikels; null ohne */
  previewBase64Png: string | null
}

/**
 * Eine Vervollständigung des Suchtexts (User 2026-10-13): Das gerade getippte Wort ist der Anfang eines Werts, z. B.
 * einer E-Mail-Adresse. Klick oder Tab ersetzt das Wort durch den ganzen Wert, die Suche läuft damit. Kein Filter.
 */
export interface SearchTextCompletion {
  coreKey: string
  /** das getippte Wort, das ersetzt wird */
  typedWord: string
  /** der ganze Wert, der es ersetzt */
  completedText: string
  /** woran man den Wert erkennt, z. B. Name und Firma zur Adresse; null ohne */
  additionText: string | null
}

/** Höchstens so viele Angebote je Kern, wie in der Vorlage */
export const SEARCH_MAX_OFFERS_PER_CORE = 10

/** Höchstens so viele Vervollständigungen insgesamt */
export const SEARCH_MAX_TEXT_COMPLETIONS = 5

/** Die Suchwörter, mit denen ein Wert beginnen kann: klein, ohne + davor, ohne ausgeschlossene, ab SEARCH_MIN_CHARACTERS Zeichen */
export function searchWordsForOffersOf(searchText: string): string[] {
  return searchWordsOf(searchText)
    .filter((word) => !word.startsWith('-'))
    .map((word) => word.replace(/^\+/, '').toLowerCase())
    .filter((word) => word.length >= SEARCH_MIN_CHARACTERS)
}

/**
 * Die Hook-Angebote eines Treffers, für jeden Kern gleich: jeder Hook-Wert, der mit einem Suchwort beginnt (so trifft
 * der Zwilling), wie die Gründe des Backends. additionOf gibt je Hook den Zusatz, z. B. Name und Bild eines Artikels;
 * null heißt ohne.
 */
export function hookOffersOfHit(
  coreKey: string,
  hit: SearchHit,
  searchWords: readonly string[],
  solrHooks: readonly Pick<SolrHook, 'key' | 'valueBadgeLinkTarget'>[],
  additionOf: (solrHook: Pick<SolrHook, 'key' | 'valueBadgeLinkTarget'>) => { additionText: string | null; previewBase64Png: string | null } | null = () => null,
): SearchOffer[] {
  const offers: SearchOffer[] = []
  for (const solrHook of solrHooks) {
    const raw = hit.fieldValues[solrHook.key]
    const values = raw === null || raw === undefined ? [] : (Array.isArray(raw) ? raw : [raw]).map(String)
    for (const value of values) {
      const word = searchWords.find((candidate) => value.toLowerCase().startsWith(candidate))
      if (word === undefined) {
        continue
      }
      const addition = additionOf(solrHook)
      offers.push({
        coreKey,
        fieldName: solrHook.key,
        value,
        word,
        additionText: addition?.additionText ?? null,
        previewBase64Png: addition?.previewBase64Png ?? null,
      })
    }
  }
  return offers
}

/** Das Wort, das gerade getippt wird: das letzte, solange kein Leerzeichen folgt, ohne + davor; leer sonst und bei einem ausgeschlossenen */
export function typedWordOf(searchText: string): string {
  if (searchText === '' || /\s$/.test(searchText)) {
    return ''
  }
  const words = searchText.trim().split(/\s+/)
  const last = words[words.length - 1] ?? ''
  return last.startsWith('-') ? '' : last.replace(/^\+/, '')
}

/** Der Suchtext mit dem getippten letzten Wort durch den ganzen Wert ersetzt, ein + davor bleibt */
export function withTypedWordCompleted(searchText: string, completedText: string): string {
  const words = searchText.trim().split(/\s+/)
  const last = words.pop() ?? ''
  words.push(last.startsWith('+') ? `+${completedText}` : completedText)
  return words.join(' ')
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

/**
 * Das Ergebnis eines Kerns (DmsSearchedDocumentsPage.java): Treffer insgesamt und eine Seite davon nach Score. In der
 * Maske stehen die bis dahin geladenen Seiten aneinander, page ist dann die letzte geladene.
 */
export interface SearchResultPage {
  totalFound: number
  page: number
  /** Treffer je Seite, app1.solr.search.hits im Backend */
  pageSize: number
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

/** Die Abfrage an einen Kern: Chips je Feld gebündelt (gleiches Feld ODER), ohne Seite die erste */
export function searchRequestOf(
  searchText: string,
  filters: readonly SearchFilter[],
  allWordsRequired: boolean,
  page = 0,
): SearchRequest {
  const exactFilterValuesByFieldName: Record<string, string[]> = {}
  for (const filter of filters) {
    const values = (exactFilterValuesByFieldName[filter.fieldName] ??= [])
    if (!values.includes(filter.value)) {
      values.push(filter.value)
    }
  }
  return { searchText, exactFilterValuesByFieldName, page, allWordsRequired }
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
