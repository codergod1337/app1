import type { SearchRequest, SearchResultPage } from './Search.ts'

/**
 * Der Zwischenspeicher der Suche für die Sitzung (User 2026-10-07): Wer auf einen Treffer geht und zurückkommt, sieht
 * dieselben Treffer sofort, ohne Request und ohne Tipp-Pause; erst ein neues Zeichen oder ein anderer Chip fragt das
 * Backend.
 *
 * Lebt außerhalb der Komponente, nur im Speicher, nie im Browser abgelegt. Der Schlüssel trägt die guid des Users:
 * Ein anderer User im selben Fenster sieht nie fremde Treffer. Jeder Schreibvorgang leert ihn (clearSearchResultCache),
 * sonst zeigte die Suche nach dem Bearbeiten den alten Stand. Höchstens MAX_ENTRIES, die ältesten fallen raus.
 */
const MAX_ENTRIES = 50

const results = new Map<string, SearchResultPage>()

function keyOf(usersGuid: string | null, coreKey: string, request: SearchRequest): string {
  return JSON.stringify([usersGuid, coreKey, request.searchText, request.exactFilterValuesByFieldName])
}

export function cachedSearchResult(usersGuid: string | null, coreKey: string, request: SearchRequest): SearchResultPage | undefined {
  return results.get(keyOf(usersGuid, coreKey, request))
}

export function rememberSearchResult(usersGuid: string | null, coreKey: string, request: SearchRequest, result: SearchResultPage) {
  const key = keyOf(usersGuid, coreKey, request)
  results.delete(key)
  results.set(key, result)
  while (results.size > MAX_ENTRIES) {
    const oldest = results.keys().next().value
    if (oldest === undefined) {
      break
    }
    results.delete(oldest)
  }
}

/** Nach jedem Schreibvorgang an Dokumenten: Upload, Bearbeiten, Löschen. Die nächste Suche fragt wieder das Backend. */
export function clearSearchResultCache() {
  results.clear()
}
