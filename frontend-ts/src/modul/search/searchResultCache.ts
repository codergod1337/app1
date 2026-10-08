import type { SearchFilterOffer, SearchRequest, SearchResultPage } from './Search.ts'

/**
 * Der Zwischenspeicher der Suche für die Sitzung (User 2026-10-07): Wer auf einen Treffer geht und zurückkommt, sieht
 * dieselben Treffer sofort, ohne Request und ohne Tipp-Pause; erst ein neues Zeichen, ein anderer Chip oder eine
 * andere Seite fragt das Backend. Dasselbe für die Filterangebote der Vervollständigung.
 *
 * Lebt außerhalb der Komponente, nur im Speicher, nie im Browser abgelegt. Der Schlüssel trägt die guid des Users:
 * Ein anderer User im selben Fenster sieht nie fremde Treffer. Jeder Schreibvorgang leert ihn (clearSearchResultCache),
 * sonst zeigte die Suche nach dem Bearbeiten den alten Stand. Höchstens MAX_ENTRIES je Art, die ältesten fallen raus.
 */
const MAX_ENTRIES = 50

const results = new Map<string, SearchResultPage>()
const offers = new Map<string, SearchFilterOffer[]>()

function keyOf(usersGuid: string | null, coreKey: string, request: SearchRequest): string {
  return JSON.stringify([
    usersGuid,
    coreKey,
    request.searchText,
    request.exactFilterValuesByFieldName,
    request.dateRangeFilterByFieldName,
    request.page,
    request.size,
  ])
}

function remember<T>(store: Map<string, T>, key: string, value: T) {
  store.delete(key)
  store.set(key, value)
  while (store.size > MAX_ENTRIES) {
    const oldest = store.keys().next().value
    if (oldest === undefined) {
      break
    }
    store.delete(oldest)
  }
}

export function cachedSearchResult(usersGuid: string | null, coreKey: string, request: SearchRequest): SearchResultPage | undefined {
  return results.get(keyOf(usersGuid, coreKey, request))
}

export function rememberSearchResult(usersGuid: string | null, coreKey: string, request: SearchRequest, result: SearchResultPage) {
  remember(results, keyOf(usersGuid, coreKey, request), result)
}

export function cachedSearchOffers(usersGuid: string | null, coreKey: string, request: SearchRequest): SearchFilterOffer[] | undefined {
  return offers.get(keyOf(usersGuid, coreKey, request))
}

export function rememberSearchOffers(usersGuid: string | null, coreKey: string, request: SearchRequest, found: SearchFilterOffer[]) {
  remember(offers, keyOf(usersGuid, coreKey, request), found)
}

/** Nach jedem Schreibvorgang an Dokumenten: Upload, Bearbeiten, Löschen. Die nächste Suche fragt wieder das Backend. */
export function clearSearchResultCache() {
  results.clear()
  offers.clear()
}
