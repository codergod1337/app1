import type { ComponentType } from 'react'
import type { RequestResult } from '../../api/client.ts'
import type { OriginState } from '../../components/Origin.ts'
import { ADMIN_ACCESS_ROLE_KEY } from '../accessrole/AccessRole.ts'
import { ARTIKEL_CORE_KEY, ARTIKEL_SEARCH_ACCESS_ROLE_KEY, artikelOffersOfHit, artikelPath, searchArtikelDocuments } from '../artikel/Artikel.ts'
import { ArtikelSearchResultList } from '../artikel/ArtikelSearchResultList.tsx'
import { DMS_CORE_KEY, DMS_SEARCH_ACCESS_ROLE_KEY, dmsSolrDocumentPath, FILE_SUB_CLASS_FIELD_NAME, searchDmsDocuments } from '../dms/Dms.ts'
import { DmsSearchResultList } from '../dms/DmsSearchResultList.tsx'
import {
  KONTAKTE_CORE_KEY,
  KONTAKTE_SEARCH_ACCESS_ROLE_KEY,
  kontakteCompletionsOfHit,
  kontakteMappePathOf,
  kontakteOffersOfHit,
  searchKontakteDocuments,
} from '../kontakte/Kontakte.ts'
import { KontakteSearchResultList } from '../kontakte/KontakteSearchResultList.tsx'
import {
  MESSAGE_SUB_CLASS_FIELD_NAME,
  NACHRICHTEN_CORE_KEY,
  NACHRICHTEN_SEARCH_ACCESS_ROLE_KEY,
  nachrichtenCompletionsOfHit,
  nachrichtenOffersOfHit,
  searchNachrichtenDocuments,
} from '../nachrichten/Nachrichten.ts'
import { NachrichtenSearchResultList } from '../nachrichten/NachrichtenSearchResultList.tsx'
import type { SolrHook } from '../solrmanager/SolrHook.ts'
import {
  hookOffersOfHit,
  type SearchFilter,
  type SearchHit,
  type SearchOffer,
  type SearchRequest,
  type SearchResultPage,
  type SearchTextCompletion,
} from './Search.ts'

/** Was die Trefferliste eines Kerns bekommt: seine Treffer, die Herkunft für die Wege aus der Suche, das Einloggen eines Werts als Filter */
export interface SearchResultListProps {
  hits: SearchHit[]
  origin: OriginState
  /** ein Wert aus dem Treffer als Filter einloggen, z. B. ein Hook-Wert */
  onFilter: (filter: SearchFilter) => void
}

/** Die Schlüssel der Kernnamen im Sprachkatalog, search.cores.<key> */
export type SearchCoreLabelKey = 'DMS' | 'ARTIKEL' | 'KONTAKTE' | 'NACHRICHTEN'

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
  /**
   * die Seite eines Treffers, wenn der Schlüssel exakt eingetippt wurde: Die Maske zeigt ihn deutlich, Enter öffnet sie
   * (User 2026-10-14, kein automatischer Sprung mehr). null: in diesem Kern nie
   */
  directPathOf: ((fieldValues: Record<string, unknown>) => string | null) | null
  /**
   * Die Filter-Angebote eines Treffers über der Eingabe (User 2026-10-13): Feld, Wert und woran man ihn erkennt. Die
   * Suchwörter sind klein, ohne + davor und ab drei Zeichen (searchWordsForOffersOf), die Hooks kommen aus den Stammdaten.
   */
  offersOf: (hit: SearchHit, searchWords: readonly string[], solrHooks: readonly SolrHook[]) => SearchOffer[]
  /** Die Vervollständigungen des gerade getippten Worts aus einem Treffer, z. B. eine E-Mail-Adresse; leer, wo es keine gibt */
  completionsOf: (hit: SearchHit, typedWord: string) => SearchTextCompletion[]
  ResultList: ComponentType<SearchResultListProps>
}

/** Die Kerne der Suche in der Reihenfolge der Treffergruppen: DMS, ARTIKEL, KONTAKTE und NACHRICHTEN */
export const SEARCH_CORES: readonly SearchCore[] = [
  {
    key: DMS_CORE_KEY,
    labelKey: 'DMS',
    searchAccessRoleKey: DMS_SEARCH_ACCESS_ROLE_KEY,
    search: searchDmsDocuments,
    idOf: (fieldValues) => (typeof fieldValues.id === 'string' ? fieldValues.id : null),
    // die id ist der SHA-256 der Bytes: Wer ihn tippt, meint genau dieses Dokument, ein halber Hash trifft nie
    directPathOf: (fieldValues) => (typeof fieldValues.id === 'string' ? dmsSolrDocumentPath(fieldValues.id) : null),
    // die Hook-Werte, die mit einem Suchwort beginnen, ohne Zusatz
    offersOf: (hit, searchWords, solrHooks) => hookOffersOfHit(DMS_CORE_KEY, hit, searchWords, solrHooks),
    completionsOf: () => [],
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
    offersOf: artikelOffersOfHit,
    completionsOf: () => [],
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
    offersOf: kontakteOffersOfHit,
    completionsOf: kontakteCompletionsOfHit,
    ResultList: KontakteSearchResultList,
  },
  {
    key: NACHRICHTEN_CORE_KEY,
    labelKey: 'NACHRICHTEN',
    searchAccessRoleKey: NACHRICHTEN_SEARCH_ACCESS_ROLE_KEY,
    search: searchNachrichtenDocuments,
    idOf: (fieldValues) => (typeof fieldValues.id === 'string' ? fieldValues.id : null),
    // kein Direktsprung: Die id ist eine kleine laufende Nummer und träfe jede getippte Belegnummer
    directPathOf: null,
    offersOf: nachrichtenOffersOfHit,
    completionsOf: nachrichtenCompletionsOfHit,
    ResultList: NachrichtenSearchResultList,
  },
]

/**
 * Der Kern, der als Auflöser auch abgewählt mitsucht (nach der Vorlage, User 2026-10-13): Seine Treffer bleiben
 * unsichtbar, nur seine Angebote und Vervollständigungen erscheinen. Wer „Vermes“ tippt und nur Artikel sucht, bekommt
 * so die Kundennummer mit Firmennamen als Filter.
 */
export const SEARCH_RESOLVER_CORE_KEY = KONTAKTE_CORE_KEY

/**
 * Ein spezialFilterErsterOrdnung (User 2026-10-14): „fsc.“ wählt eine Dateiart, „msc.“ eine Nachrichtenart. Die
 * Eingabe öffnet die Arten als Kacheln, die Wahl wird ein Chip mit eigenem Aussehen (SearchSubClassChip). Sein Feld
 * gibt es nur in einem Kern: Solange der Chip steht, sucht nur dieser Kern, die anderen Kernschalter sind gesperrt.
 * Immer nur einer zugleich, ein neuer ersetzt den alten. Nur mit der Such-Rolle seines Kerns.
 */
export interface SpezialFilterErsterOrdnung {
  /** womit die Eingabe beginnt, klein; zugleich der Schlüssel im Sprachkatalog, search.<prefix ohne Punkt> */
  prefix: 'fsc.' | 'msc.'
  /** das Feld des Chips */
  fieldName: string
  /** der einzige Kern mit diesem Feld */
  coreKey: string
}

export const SPEZIAL_FILTER_ERSTER_ORDNUNG: readonly SpezialFilterErsterOrdnung[] = [
  { prefix: 'fsc.', fieldName: FILE_SUB_CLASS_FIELD_NAME, coreKey: DMS_CORE_KEY },
  { prefix: 'msc.', fieldName: MESSAGE_SUB_CLASS_FIELD_NAME, coreKey: NACHRICHTEN_CORE_KEY },
]

/** Der spezialFilterErsterOrdnung zu einem Chip-Feld, null für jedes andere Feld */
export function spezialFilterErsterOrdnungOfField(fieldName: string): SpezialFilterErsterOrdnung | null {
  return SPEZIAL_FILTER_ERSTER_ORDNUNG.find((entry) => entry.fieldName === fieldName) ?? null
}

/** Die Kerne, die dieser User durchsuchen darf: die Such-Rolle des Kerns, ADMIN darf jeden */
export function searchCoresOf(hasAccessRole: (accessRoleKey: string) => boolean): SearchCore[] {
  return SEARCH_CORES.filter((core) => hasAccessRole(ADMIN_ACCESS_ROLE_KEY) || hasAccessRole(core.searchAccessRoleKey))
}
