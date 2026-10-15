import { request } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { translate } from '../../components/multilingual.ts'
import { ADMIN_ACCESS_ROLE_KEY } from '../accessrole/AccessRole.ts'
import { hookOffersOfHit, type SearchHit, type SearchOffer, type SearchRequest, type SearchResultPage, type SearchTextCompletion } from '../search/Search.ts'
import { solrFieldDisplayNameOf, type SolrField } from '../solrmanager/SolrField.ts'
import type { SolrHook } from '../solrmanager/SolrHook.ts'

/** Der Kern der archivierten Mails (SolrDocumentNachrichtenService.CORE_KEY) */
export const NACHRICHTEN_CORE_KEY = 'NACHRICHTEN'

/** Das Feld mit der Nachrichtenart (MSC) einer Nachricht, gibt es nur in diesem Kern */
export const MESSAGE_SUB_CLASS_FIELD_NAME = 'messageSubClassKey'

/** Die Tür des Kerns: Nachrichten lesen. Welche, entscheidet readAcl aus der Nachrichtenart. */
export const NACHRICHTEN_ACCESS_ROLE_KEY = 'NACHRICHTEN'

/** Die AR der Suche im Kern NACHRICHTEN: alle Felder und Hooks, readAcl gilt weiter */
export const NACHRICHTEN_SEARCH_ACCESS_ROLE_KEY = 'NACHRICHTEN_SUCHE'

/** Wer die Seite einer Nachricht öffnen darf, eine reicht: wie die Tür im Backend (SolrDocumentNachrichtenUtilsService.requireAccessRole) */
export const NACHRICHTEN_ENTRY_ACCESS_ROLE_KEYS = [NACHRICHTEN_ACCESS_ROLE_KEY, NACHRICHTEN_SEARCH_ACCESS_ROLE_KEY, ADMIN_ACCESS_ROLE_KEY] as const

/** Die Seite einer Nachricht samt Verlauf: /nachricht/17, die laufende Nummer ist eine Long-id */
export const NACHRICHT_PATH = '/nachricht'

export function nachrichtPath(id: string): string {
  return `${NACHRICHT_PATH}/${encodeURIComponent(id)}`
}

/** Die Felder vom Typ EMAIL, deren Adressen die Suche beim Tippen vervollständigt */
const NACHRICHTEN_ADDRESS_FIELD_NAMES = ['fromAddress', 'senderAddress', 'toRecipientAddresses', 'ccRecipientAddresses', 'bccRecipientAddresses']

/** Eine Liste, egal ob Solr einen Wert oder mehrere liefert; leer bei nichts */
export function nachrichtenValuesOf(value: unknown): string[] {
  if (value === null || value === undefined) {
    return []
  }
  return (Array.isArray(value) ? value : [value]).map((entry) => String(entry))
}

/** Der erste Wert eines Feldes als Text, leer bei nichts */
export function nachrichtenValueOf(document: Record<string, unknown>, fieldName: string): string {
  const values = nachrichtenValuesOf(document[fieldName])
  return values.length === 0 ? '' : values[0]
}

/** Der Name eines Feldes für den User: bei einem Hook sein Anzeigename, sonst der des Feldes im Kern, sonst der technische */
export function nachrichtenFieldLabel(solrFields: readonly SolrField[] | null, solrHooks: readonly SolrHook[] | null, fieldName: string, language?: LanguageCode): string {
  const hook = (solrHooks ?? []).find((candidate) => candidate.key === fieldName)
  if (hook) {
    return translate(hook.displayName, language) || hook.key
  }
  return solrFieldDisplayNameOf(solrFields ?? [], NACHRICHTEN_CORE_KEY, fieldName, language) ?? fieldName
}

/** Eine Seite einer Auflösung im Kern NACHRICHTEN (NachrichtenResolvedDocumentsPage.java) */
export interface NachrichtenResolvedDocumentsPage {
  totalFound: number
  page: number
  pageSize: number
  documents: Record<string, unknown>[]
}

const NACHRICHTEN_PATH = '/api/rest/v1/solr/nachrichten'

/** Eine Nachricht mit den Feldern, die der User lesen darf; 404, wenn es sie nicht gibt oder er sie nicht sehen darf */
export function getNachricht(id: string) {
  return request<Record<string, unknown>>('GET', `${NACHRICHTEN_PATH}/id/${encodeURIComponent(id)}`)
}

/** Der Verlauf einer Nachricht, älteste zuerst, sie selbst eingeschlossen; was der User nicht sehen darf, fehlt */
export function getNachrichtConversation(id: string) {
  return request<Record<string, unknown>[]>('GET', `${NACHRICHTEN_PATH}/conversation/${encodeURIComponent(id)}`)
}

/** Auflösen nach exakten Werten, mehrere Felder UND, neueste zuerst; Tür NACHRICHTEN */
export function resolveNachrichtenDocuments(exactValueByFieldName: Record<string, string>, page = 0) {
  return request<NachrichtenResolvedDocumentsPage>('POST', `${NACHRICHTEN_PATH}/resolve`, { exactValueByFieldName, page })
}

/** Die Suche im Kern NACHRICHTEN (SolrDocumentNachrichtenController.searchNachrichtenDocuments), derselbe Body wie bei DMS */
export function searchNachrichtenDocuments(searchRequest: SearchRequest) {
  return request<SearchResultPage>('POST', `${NACHRICHTEN_PATH}/search`, searchRequest)
}

/** Die Filter-Angebote eines Treffers: wie in jedem Kern die Hook-Werte, die mit einem Suchwort beginnen */
export function nachrichtenOffersOfHit(hit: SearchHit, searchWords: readonly string[], solrHooks: readonly SolrHook[]): SearchOffer[] {
  return hookOffersOfHit(NACHRICHTEN_CORE_KEY, hit, searchWords, solrHooks)
}

/**
 * Die Vervollständigungen eines Treffers wie im Kern KONTAKTE: jede Adresse der Beteiligten, die mit dem gerade
 * getippten Wort beginnt und länger ist, ab drei Zeichen. Übernommen läuft die Suche im E-Mail-Modus.
 */
export function nachrichtenCompletionsOfHit(hit: SearchHit, typedWord: string): SearchTextCompletion[] {
  const typed = typedWord.toLowerCase()
  if (typed.length < 3) {
    return []
  }
  const addresses = new Set<string>()
  for (const fieldName of NACHRICHTEN_ADDRESS_FIELD_NAMES) {
    for (const address of nachrichtenValuesOf(hit.fieldValues[fieldName])) {
      if (address.length > typed.length && address.toLowerCase().startsWith(typed)) {
        addresses.add(address)
      }
    }
  }
  return [...addresses].map((address) => ({ coreKey: NACHRICHTEN_CORE_KEY, typedWord, completedText: address, additionText: null }))
}

/** Ein Bild im archivierten Mailtext verweist so auf seine Datei im DMS: dms:<sha256>, die id des Dokuments */
const DMS_IMAGE_REFERENCE = /dms:([0-9a-f]{64})/g

/** Die DMS-ids aller Bilder im Text, ohne Doppelte */
export function dmsImageIdsOf(html: string): string[] {
  return [...new Set([...html.matchAll(DMS_IMAGE_REFERENCE)].map((match) => match[1]))]
}

/** Der Text mit den geladenen Bildern: dms:<id> wird zur blob:-Adresse, ein Bild ohne geladene Datei bleibt stehen */
export function withImageUrls(html: string, urlById: Readonly<Record<string, string>>): string {
  return html.replace(DMS_IMAGE_REFERENCE, (reference, id: string) => urlById[id] ?? reference)
}
