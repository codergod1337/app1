import { request } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { translate } from '../../components/multilingual.ts'
import type { SearchRequest, SearchResultPage } from '../search/Search.ts'
import { solrFieldDisplayNameOf, type SolrField } from '../solrmanager/SolrField.ts'
import type { SolrHook } from '../solrmanager/SolrHook.ts'

/** Der Kern der Kontakte im SolrManager (SolrDocumentKontakteService.CORE_KEY): die 19er aus ABAS, Firmen und Ansprechpartner */
export const KONTAKTE_CORE_KEY = 'KONTAKTE'

/** Die Tür des Kerns KONTAKTE: Auflösen und damit die Kunden- und Lieferantenmappe. Ohne sie 403 vor Solr, außer für ADMIN. */
export const KONTAKTE_ACCESS_ROLE_KEY = 'KONTAKTE'

/** Die AR der Suche im Kern KONTAKTE: Sie gibt in der Suche alle Felder und Hooks frei. Ohne sie 403 vor Solr, außer für ADMIN. */
export const KONTAKTE_SEARCH_ACCESS_ROLE_KEY = 'KONTAKTE_SUCHE'

/** Firma oder Ansprechpartner: der Text des 19ers, „Adresse“ oder „Kontakt“ (SolrDocumentKontakteService.CONTACT_KIND_FIELD_NAME) */
export const KONTAKTE_CONTACT_KIND_FIELD_NAME = 'kontaktorig'

/** Der Wert von kontaktorig an einer Firma */
export const KONTAKTE_CONTACT_KIND_COMPANY = 'Adresse'

/** Der Wert von kontaktorig an einem Ansprechpartner */
export const KONTAKTE_CONTACT_KIND_PERSON = 'Kontakt'

/** Die Sternzeit des Dokuments ist seine id, die des 19ers */
export const KONTAKTE_ID_FIELD_NAME = 'id'

/** Der Sync des Backends: wann und mit welchen Gründen */
export const KONTAKTE_SYNC_DATE_FIELD_NAME = 'kontaktSyncDatum'
export const KONTAKTE_SYNC_ERROR_FIELD_NAME = 'kontaktSyncFehler'

/**
 * Kunde oder Lieferant, die zwei Mappen (User 2026-10-13): Eine Firma ist eine 19er-Adresse, ihre Rollen sind ihre
 * Nummern. Die Kundenmappe zeigt die Kundenseite, die Lieferantenmappe die Lieferantenseite.
 */
export type KontaktePartnerRole = 'KUNDE' | 'LIEFERANT'

/** Je Rolle der Hook mit der Nummer, der Pfad der Mappe, der Anzeigeort ihrer Dokumente und die Vorsilbe ihrer Geschäftsfelder */
export const KONTAKTE_PARTNER_ROLES: Record<
  KontaktePartnerRole,
  { numberHookName: string; mappePath: string; documentsDestinationKey: string; businessFieldPrefix: string; abasPartnerSternzeitFieldName: string }
> = {
  KUNDE: {
    numberHookName: 'kundenNummer',
    mappePath: '/kunde',
    documentsDestinationKey: 'KUNDENMAPPE',
    businessFieldPrefix: 'kunde',
    // die Sternzeit des ABAS-Kunden 0:1, der Kopie des 19ers
    abasPartnerSternzeitFieldName: 'kundeKontaktreferenz',
  },
  LIEFERANT: {
    numberHookName: 'lieferantenNummer',
    mappePath: '/lieferant',
    documentsDestinationKey: 'LIEFERANTENMAPPE',
    businessFieldPrefix: 'lieferant',
    // die Sternzeit des ABAS-Lieferanten 1:1, der Kopie des 19ers
    abasPartnerSternzeitFieldName: 'lieferantKontaktreferenz',
  },
}

/** Die festen Tabs der Mappe in dieser Reihenfolge; das Segment in der Adresse ist der Schlüssel */
export const KONTAKTE_MAPPE_TABS = ['overview', 'contactPersons', 'documents'] as const

export type KontakteMappeTab = (typeof KONTAKTE_MAPPE_TABS)[number]

export function isKontakteMappeTab(tab: string | undefined): tab is KontakteMappeTab {
  return tab !== undefined && (KONTAKTE_MAPPE_TABS as readonly string[]).includes(tab)
}

/** Der Weg in die Kunden- oder Lieferantenmappe einer Nummer, ohne Tab die Übersicht */
export function kontakteMappePath(partnerRole: KontaktePartnerRole, nummer: string, tab: KontakteMappeTab = 'overview'): string {
  const path = `${KONTAKTE_PARTNER_ROLES[partnerRole].mappePath}/${encodeURIComponent(nummer)}`
  return tab === 'overview' ? path : `${path}/${tab}`
}

/**
 * Die Mappe zu einem Kontakt aus dem Kern: mit Kundennummer die Kundenmappe, sonst mit Lieferantennummer die
 * Lieferantenmappe, sonst keine. Ein Ansprechpartner trägt die Nummer seiner Firma und landet so in ihrer Mappe.
 */
export function kontakteMappePathOf(document: Record<string, unknown>): string | null {
  const kundenNummer = kontakteValueOf(document, KONTAKTE_PARTNER_ROLES.KUNDE.numberHookName)
  if (kundenNummer !== '') {
    return kontakteMappePath('KUNDE', kundenNummer)
  }
  const lieferantenNummer = kontakteValueOf(document, KONTAKTE_PARTNER_ROLES.LIEFERANT.numberHookName)
  return lieferantenNummer !== '' ? kontakteMappePath('LIEFERANT', lieferantenNummer) : null
}

/** Der Name eines Feldes für den User: bei einem Hook sein Anzeigename, sonst der des Feldes im Kern, sonst der technische */
export function kontakteFieldLabel(solrFields: readonly SolrField[] | null, solrHooks: readonly SolrHook[] | null, fieldName: string, language?: LanguageCode): string {
  const hook = (solrHooks ?? []).find((candidate) => candidate.key === fieldName)
  if (hook) {
    return translate(hook.displayName, language) || hook.key
  }
  return solrFieldDisplayNameOf(solrFields ?? [], KONTAKTE_CORE_KEY, fieldName, language) ?? fieldName
}

/** Eine Seite einer Auflösung im Kern KONTAKTE (KontakteResolvedDocumentsPage.java) */
export interface KontakteResolvedDocumentsPage {
  totalFound: number
  page: number
  pageSize: number
  documents: Record<string, unknown>[]
}

/** Auflösen nach exakten Werten, mehrere Felder UND (SolrDocumentKontakteController.resolveKontakteDocuments); Tür KONTAKTE */
export function resolveKontakteDocuments(exactValueByFieldName: Record<string, string>, page = 0) {
  return request<KontakteResolvedDocumentsPage>('POST', '/api/rest/v1/solr/kontakte/resolve', { exactValueByFieldName, page })
}

/**
 * Alle Treffer einer Auflösung, Seite für Seite, bis die Gesamtzahl erreicht ist oder eine Seite leer bleibt (über
 * app1.solr.resolve.max-results liefert das Backend nichts mehr). Für die Ansprechpartner einer Firma.
 */
export async function resolveAllKontakteDocuments(exactValueByFieldName: Record<string, string>): Promise<Record<string, unknown>[]> {
  const documents: Record<string, unknown>[] = []
  for (let page = 0; ; page++) {
    const result = await resolveKontakteDocuments(exactValueByFieldName, page)
    documents.push(...result.data.documents)
    if (result.data.documents.length === 0 || documents.length >= result.data.totalFound) {
      return documents
    }
  }
}

/** Der Name der Firma oder des Ansprechpartners, ABAS-Feld name mit Vorsilbe, weil der Artikelkern name schon hat */
export const KONTAKTE_NAME_FIELD_NAME = 'kontaktName'

/** Der Sync-Status des Backends */
export const KONTAKTE_SYNC_STATUS_FIELD_NAME = 'kontaktSyncStatus'

/** Eine Liste, egal ob Solr einen Wert oder mehrere liefert; leer bei nichts */
export function kontakteValuesOf(value: unknown): string[] {
  if (value === null || value === undefined) {
    return []
  }
  return (Array.isArray(value) ? value : [value]).map((entry) => String(entry))
}

/** Der erste Wert eines Feldes als Text, leer bei nichts */
export function kontakteValueOf(document: Record<string, unknown>, fieldName: string): string {
  const values = kontakteValuesOf(document[fieldName])
  return values.length === 0 ? '' : values[0]
}

/** Die Suche im Kern KONTAKTE (SolrDocumentKontakteController.searchKontakteDocuments), derselbe Body wie bei DMS und ARTIKEL */
export function searchKontakteDocuments(searchRequest: SearchRequest) {
  return request<{ totalFound: number; hits: { fieldValues: Record<string, unknown>; reasons: { fieldName: string; snippet: string | null }[] }[] }>(
    'POST',
    '/api/rest/v1/solr/kontakte/search',
    searchRequest,
  ).then((result) => ({
    ...result,
    data: {
      totalFound: result.data.totalFound,
      // kein Schreibflag: Im Spiegelkern schreibt kein Mensch
      hits: result.data.hits.map((hit) => ({ fieldValues: hit.fieldValues, writable: false, reasons: hit.reasons })),
    } satisfies SearchResultPage,
  }))
}
