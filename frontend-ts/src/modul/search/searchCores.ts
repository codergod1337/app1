import type { ComponentType } from 'react'
import type { RequestResult } from '../../api/client.ts'
import type { OriginState } from '../../components/Origin.ts'
import {
  DMS_ACCESS_ROLE_KEY,
  DMS_CORE_KEY,
  DMS_SEARCH_ACCESS_ROLE_KEY,
  DMS_SEARCH_DATE_RANGE_FILTER_FIELD_NAMES,
  DMS_SEARCH_EXACT_FILTER_FIELD_NAMES,
  dmsSolrDocumentPath,
  searchDmsDocuments,
  suggestDmsDocuments,
} from '../dms/Dms.ts'
import { DmsSearchResultList } from '../dms/DmsSearchResultList.tsx'
import type { SearchFilter, SearchHit, SearchRequest, SearchResultPage, SearchSuggestResult } from './Search.ts'

/** Was die Trefferliste eines Kerns bekommt: seine Treffer, die Herkunft für die Wege aus der Suche, das Einloggen eines Werts als Filter */
export interface SearchResultListProps {
  hits: SearchHit[]
  origin: OriginState
  /** ein Wert aus dem Treffer als Filter einloggen, z. B. ein Hook-Wert */
  onFilter: (filter: SearchFilter) => void
}

/** Die Schlüssel der Kernnamen im Sprachkatalog, search.cores.<key> */
export type SearchCoreLabelKey = 'DMS'

/**
 * Die Anbindung eines Kerns an die Suchmaske, nach dem Katalog suchkerne.js und der Kern-Anbindung der Vorlage: ein
 * neuer Kern ist genau ein Eintrag hier plus seine Trefferkomponente in seinem Modul, die Seite bleibt unberührt.
 */
export interface SearchCore {
  key: string
  labelKey: SearchCoreLabelKey
  /** die Tür des Kerns, <KERN> */
  accessRoleKey: string
  /** die Such-Rolle, <KERN>_SUCHE, nur sie schaltet den Kern in der Maske frei */
  searchAccessRoleKey: string
  /** die festen Felder, nach denen der Kern exakt filtert; die Hooks kommen aus den Stammdaten für jeden Kern dazu */
  exactFilterFieldNames: readonly string[]
  /** die Datumsfelder, für die der Kern einen Zeitraum nimmt */
  dateRangeFilterFieldNames: readonly string[]
  search: (request: SearchRequest) => Promise<RequestResult<SearchResultPage>>
  /** die Vervollständigung beim Tippen: kompakte Treffer und Filterangebote, derselbe Body ohne Seite */
  suggest: (request: SearchRequest) => Promise<RequestResult<SearchSuggestResult>>
  /** der Schlüssel eines Treffers, für den Direktsprung */
  idOf: (document: Record<string, unknown>) => string | null
  /** die Seite eines Treffers, wenn der Schlüssel exakt eingetippt wurde. null: kein Direktsprung in diesem Kern */
  directPathOf: ((document: Record<string, unknown>) => string | null) | null
  ResultList: ComponentType<SearchResultListProps>
}

/** Die Kerne der Suche in der Reihenfolge der Treffergruppen. Heute nur das DMS, ARTIKEL ist später ein Eintrag. */
export const SEARCH_CORES: readonly SearchCore[] = [
  {
    key: DMS_CORE_KEY,
    labelKey: 'DMS',
    accessRoleKey: DMS_ACCESS_ROLE_KEY,
    searchAccessRoleKey: DMS_SEARCH_ACCESS_ROLE_KEY,
    exactFilterFieldNames: DMS_SEARCH_EXACT_FILTER_FIELD_NAMES,
    dateRangeFilterFieldNames: DMS_SEARCH_DATE_RANGE_FILTER_FIELD_NAMES,
    search: searchDmsDocuments,
    suggest: suggestDmsDocuments,
    idOf: (document) => (typeof document.id === 'string' ? document.id : null),
    // die id ist der SHA-256 der Bytes: Wer ihn tippt, meint genau dieses Dokument, ein halber Hash trifft nie
    directPathOf: (document) => (typeof document.id === 'string' ? dmsSolrDocumentPath(document.id) : null),
    ResultList: DmsSearchResultList,
  },
]

/** Die Kerne, die dieser User durchsuchen darf: Tür und Such-Rolle, beides */
export function searchCoresOf(hasAccessRole: (accessRoleKey: string) => boolean): SearchCore[] {
  return SEARCH_CORES.filter((core) => hasAccessRole(core.accessRoleKey) && hasAccessRole(core.searchAccessRoleKey))
}
