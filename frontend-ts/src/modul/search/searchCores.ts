import type { ComponentType } from 'react'
import type { RequestResult } from '../../api/client.ts'
import type { OriginState } from '../../components/Origin.ts'
import { ADMIN_ACCESS_ROLE_KEY } from '../accessrole/AccessRole.ts'
import { ARTIKEL_CORE_KEY, ARTIKEL_SEARCH_ACCESS_ROLE_KEY, artikelPath, searchArtikelDocuments } from '../artikel/Artikel.ts'
import { ArtikelSearchResultList } from '../artikel/ArtikelSearchResultList.tsx'
import { DMS_CORE_KEY, DMS_SEARCH_ACCESS_ROLE_KEY, dmsSolrDocumentPath, searchDmsDocuments } from '../dms/Dms.ts'
import { DmsSearchResultList } from '../dms/DmsSearchResultList.tsx'
import { KONTAKTE_CORE_KEY, KONTAKTE_SEARCH_ACCESS_ROLE_KEY, kontakteMappePathOf, searchKontakteDocuments } from '../kontakte/Kontakte.ts'
import { KontakteSearchResultList } from '../kontakte/KontakteSearchResultList.tsx'
import type { SearchFilter, SearchHit, SearchRequest, SearchResultPage } from './Search.ts'

/** Was die Trefferliste eines Kerns bekommt: seine Treffer, die Herkunft für die Wege aus der Suche, das Einloggen eines Werts als Filter */
export interface SearchResultListProps {
  hits: SearchHit[]
  origin: OriginState
  /** ein Wert aus dem Treffer als Filter einloggen, z. B. ein Hook-Wert */
  onFilter: (filter: SearchFilter) => void
}

/** Die Schlüssel der Kernnamen im Sprachkatalog, search.cores.<key> */
export type SearchCoreLabelKey = 'DMS' | 'ARTIKEL' | 'KONTAKTE'

/**
 * Die Anbindung eines Kerns an die Suchmaske, nach dem Katalog suchkerne.js und der Kern-Anbindung der Vorlage: ein
 * neuer Kern ist genau ein Eintrag hier plus seine Trefferkomponente in seinem Modul, die Seite bleibt unberührt.
 */
export interface SearchCore {
  key: string
  labelKey: SearchCoreLabelKey
  /** die Such-Rolle des Kerns, <KERN>_SUCHE: nur sie (oder ADMIN) schaltet den Kern in der Maske frei */
  searchAccessRoleKey: string
  search: (request: SearchRequest) => Promise<RequestResult<SearchResultPage>>
  /** der Schlüssel eines Treffers, für den Direktsprung */
  idOf: (fieldValues: Record<string, unknown>) => string | null
  /** die Seite eines Treffers, wenn der Schlüssel exakt eingetippt wurde. null: kein Direktsprung in diesem Kern */
  directPathOf: ((fieldValues: Record<string, unknown>) => string | null) | null
  ResultList: ComponentType<SearchResultListProps>
}

/** Die Kerne der Suche in der Reihenfolge der Treffergruppen: DMS, ARTIKEL und KONTAKTE */
export const SEARCH_CORES: readonly SearchCore[] = [
  {
    key: DMS_CORE_KEY,
    labelKey: 'DMS',
    searchAccessRoleKey: DMS_SEARCH_ACCESS_ROLE_KEY,
    search: searchDmsDocuments,
    idOf: (fieldValues) => (typeof fieldValues.id === 'string' ? fieldValues.id : null),
    // die id ist der SHA-256 der Bytes: Wer ihn tippt, meint genau dieses Dokument, ein halber Hash trifft nie
    directPathOf: (fieldValues) => (typeof fieldValues.id === 'string' ? dmsSolrDocumentPath(fieldValues.id) : null),
    ResultList: DmsSearchResultList,
  },
  {
    key: ARTIKEL_CORE_KEY,
    labelKey: 'ARTIKEL',
    searchAccessRoleKey: ARTIKEL_SEARCH_ACCESS_ROLE_KEY,
    search: searchArtikelDocuments,
    idOf: (fieldValues) => (typeof fieldValues.id === 'string' ? fieldValues.id : null),
    // die id ist die Artikelnummer: Wer sie exakt tippt, meint diese Mappe
    directPathOf: (fieldValues) => (typeof fieldValues.id === 'string' ? artikelPath(fieldValues.id) : null),
    ResultList: ArtikelSearchResultList,
  },
  {
    key: KONTAKTE_CORE_KEY,
    labelKey: 'KONTAKTE',
    searchAccessRoleKey: KONTAKTE_SEARCH_ACCESS_ROLE_KEY,
    search: searchKontakteDocuments,
    idOf: (fieldValues) => (typeof fieldValues.id === 'string' ? fieldValues.id : null),
    // die id ist die Sternzeit des 19ers: Wer sie exakt tippt, meint diese Firma oder ihren Ansprechpartner, also die
    // Kundenmappe, ohne Kundennummer die Lieferantenmappe
    directPathOf: kontakteMappePathOf,
    ResultList: KontakteSearchResultList,
  },
]

/** Die Kerne, die dieser User durchsuchen darf: die Such-Rolle des Kerns, ADMIN darf jeden */
export function searchCoresOf(hasAccessRole: (accessRoleKey: string) => boolean): SearchCore[] {
  return SEARCH_CORES.filter((core) => hasAccessRole(ADMIN_ACCESS_ROLE_KEY) || hasAccessRole(core.searchAccessRoleKey))
}
