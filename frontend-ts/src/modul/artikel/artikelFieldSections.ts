import {
  ARTIKEL_INSPECTION_PROCEDURE_FIELD_NAME,
  ARTIKEL_MASTER_FIELD_NAME,
  ARTIKEL_NAME_FIELD_NAME,
  ARTIKEL_NUMBER_HOOK_NAME,
  ARTIKEL_SEARCH_TERM_FIELD_NAME,
  ARTIKEL_SYNC_DATE_FIELD_NAME,
  ARTIKEL_SYNC_ERROR_FIELD_NAME,
  ARTIKEL_SYNC_STATUS_FIELD_NAME,
  type ArtikelTab,
} from './Artikel.ts'

/**
 * Je Tab der Mappe die Felder des Dokuments, an denen er hängt (User 2026-10-13): Der Tab erscheint nur, wenn eines
 * davon im Dokument steht und nicht leer ist. Das Dokument trägt nur Felder, die der User lesen darf (Feldgrenze des
 * Backends), also ist das Recht und Daten in einer Prüfung. Tabs ohne Eintrag stehen immer da: Übersicht, Dokumente,
 * Details. Bei den vier Referenz-Tabs zählt auch die Ablage der aufaggregierten Suche, denn ein Artikel ohne eigene
 * Liste kann über Rückverweise und Alternativen Treffer haben. Prüfungen hängt am Prüfverfahren oder am QM-Schalter,
 * die Ist-Seite kennt die Mappe erst nach dem Laden. Das Backend prüft dieselben Felder vor jedem Nachladen (403).
 */
export const ARTIKEL_TAB_FIELD_NAMES: Partial<Record<ArtikelTab, readonly string[]>> = {
  bom: ['tableProduct'],
  operations: ['tableOperation'],
  tools: ['werkzeugYreferenzText', 'werkzeugAggregiertArtikelNummer'],
  alternatives: ['alternativeYreferenzText', 'alternativeAggregiertArtikelNummer'],
  goesWith: ['gehtmitYreferenzText', 'gehtmitAggregiertArtikelNummer'],
  requires: ['benoetigtYreferenzText', 'benoetigtAggregiertArtikelNummer'],
  customer: ['tkunde'],
  supplier: ['ylieferant', 'yvendor'],
  inspections: [ARTIKEL_INSPECTION_PROCEDURE_FIELD_NAME, 'yqmappqmartex'],
}

/** Die Schlüssel der Sektionsnamen im Sprachkatalog, artikel.details.sections.<key> */
export type ArtikelFieldSectionKey =
  | 'designation'
  | 'units'
  | 'commercial'
  | 'stock'
  | 'weight'
  | 'classification'
  | 'flags'
  | 'pahling'
  | 'audit'
  | 'remaining'

/** Eine Sektion des Details-Tabs: ihre Felder des Kerns ARTIKEL in Anzeigereihenfolge, nach den ABAS-Namen */
export interface ArtikelFieldSection {
  key: ArtikelFieldSectionKey
  fieldNames: readonly string[]
}

/**
 * Die Sektionen des Details-Tabs nach dem DetailsTab der Vorlage, mit den ABAS-Namen der Felder (User 2026-10-10).
 * Was keine Sektion nennt und kein Tab zeigt, kommt in „Übrige Felder“, damit beim Durchgehen jedes Feld sichtbar ist.
 */
export const ARTIKEL_FIELD_SECTIONS: readonly ArtikelFieldSection[] = [
  { key: 'designation', fieldNames: ['vbez', 'vkbez', 'ftext', 'bem', 'beschr'] },
  { key: 'units', fieldNames: ['ve', 'ge', 'le', 'vpe', 'epe', 'lme', 'bme'] },
  { key: 'commercial', fieldNames: ['vpr', 'ekpr', 'lpr', 'mpr', 'planpr1', 'vwaehr', 'kbprwaeh'] },
  { key: 'stock', fieldNames: ['bestand', 'lgbestand', 'zlgbestand', 'zupname', 'abpname', 'lggew'] },
  { key: 'weight', fieldNames: ['gewicht', 'brgewicht', 'ntgewicht', 'mate', 'vol'] },
  { key: 'classification', fieldNames: ['sachname', 'wgruppename', 'dispoa', 'ahnumname', 'wstoffname', 'drbelegart'] },
  { key: 'flags', fieldNames: ['gefahrgut', 'skfaehig', 'serpflicht', 'intrarel', 'nwpflicht', 'bsart', 'istProduktionsartikel'] },
  {
    // die y-Felder, die Custom-Felder der Firma: jedes wird gespeichert (User 2026-10-11), die Mappe zeigt sie hier
    key: 'pahling',
    fieldNames: [
      'yartnrzusatz',
      ARTIKEL_MASTER_FIELD_NAME,
      'ymasterslave',
      'ykundenbeistellung',
      'ykategorie',
      'yzeichndatum',
      'ydispoa',
      'ydispoaMge',
      'yqmappqmpruefv',
      'yqmappqmartex',
      'ylieferantartikel',
    ],
  },
  {
    key: 'audit',
    fieldNames: ['erfasserzeichen', 'zeich', 'erfass', 'stand', ARTIKEL_SYNC_DATE_FIELD_NAME, ARTIKEL_SYNC_STATUS_FIELD_NAME, ARTIKEL_SYNC_ERROR_FIELD_NAME],
  },
]

/** Die Felder der Übersicht, nach der Stammdaten-Liste der Vorlage; die Nummer steht im Kopf */
export const ARTIKEL_OVERVIEW_FIELD_NAMES: readonly string[] = [
  ARTIKEL_NAME_FIELD_NAME,
  ARTIKEL_SEARCH_TERM_FIELD_NAME,
  'zmart',
  'bsartText',
  'zeichn',
  'index',
  'gtin',
  've',
  'ge',
]

/** Eine Spalte einer Tabelle der Mappe: der Schlüssel im Zeilen-Objekt und wie die Zelle aussieht */
export interface ArtikelTableColumn {
  /** der Name der Spalte im Tabellen-Eintrag des Mappings, zugleich der Schlüssel der Beschriftung (artikel.tableColumns) */
  key: string
  width?: string
  /** der Wert ist eine hauseigene Artikelnummer und führt in ihre Mappe */
  link?: boolean
  /** vor dem Wert steht der schmale ABAS-Knopf mit der Sternzeit der Zeile (sternzeitColumnKey) */
  abasButton?: boolean
  /** die Einheit aus dieser Spalte steht hinter dem Wert, z. B. Menge und Einheit in einer Zelle */
  unitKey?: string
  /** der Wert als Badge, z. B. der Typ einer Stücklistenzeile */
  badge?: boolean
  /**
   * Eine Spalte ohne eigenen Wert (User 2026-10-13): das Symbol einer Dateiart mit der Zahl der DMS-Treffer, aufgelöst
   * nach der Dateiart und dem Hook, dessen Wert aus der Spalte valueColumnKey derselben Zeile kommt
   */
  documentsOf?: { fileSubClassKey: string; hookName: string; valueColumnKey: string }
  /** am Handy ausgeblendet, im Detail-Popup zu sehen */
  unimportant?: boolean
}

/** Ein Tabellenfeld der Mappe: das multiValued-Feld mit je Wert einer Zeile als JSON-Objekt, und seine Spalten */
export interface ArtikelTableField {
  fieldName: string
  /** die Spalte, die je Zeile die Sternzeit des Objekts trägt, für den ABAS-Knopf; null ohne */
  sternzeitColumnKey: string | null
  columns: readonly ArtikelTableColumn[]
}

/**
 * Die Tabellenfelder (User 2026-10-10: eine Tabelle in einem Feld, 2026-10-11: schön anzeigen und über die Sternzeit
 * nach ABAS verlinken): je Wert eine Zeile als JSON-Objekt mit den Spalten des Tabellen-Eintrags im Mapping. Die
 * Spaltenschlüssel sind die Namen dort, die Beschriftung kommt aus dem Sprachkatalog (artikel.tableColumns).
 */
export const ARTIKEL_TABLE_FIELDS: Record<'bom' | 'operations', ArtikelTableField> = {
  bom: {
    fieldName: 'tableProduct',
    sternzeitColumnKey: 'elem',
    columns: [
      { key: 'zn', width: '4rem' },
      { key: 'elemText', link: true, abasButton: true, width: '13rem' },
      // die Datenblätter des Bauteils aus dem DMS: Symbol mit Trefferzahl, Treffer im Hoverlay (User 2026-10-13)
      { key: 'documents', width: '6rem', documentsOf: { fileSubClassKey: 'DATENBLATT', hookName: ARTIKEL_NUMBER_HOOK_NAME, valueColumnKey: 'elemText' } },
      { key: 'elname' },
      { key: 'menge', unitKey: 'elle', width: '9rem' },
      { key: 'telementicon', badge: true, width: '9rem', unimportant: true },
    ],
  },
  operations: {
    fieldName: 'tableOperation',
    sternzeitColumnKey: 'elem',
    columns: [
      { key: 'zn', width: '4rem' },
      { key: 'elemText', abasButton: true, width: '13rem' },
      { key: 'elname' },
      { key: 'mgrname', unimportant: true },
      { key: 'zesek', width: '7rem', unimportant: true },
      { key: 'zrsek', width: '7rem', unimportant: true },
    ],
  },
}

/** Die Spalten der Paarlisten je Tab, index-parallele Felder in Anzeigereihenfolge; die erste ist die Artikelnummer, wo es eine gibt */
export const ARTIKEL_PAIR_LIST_FIELD_NAMES = {
  tools: ['werkzeugYreferenzText'],
  alternatives: ['alternativeYreferenzText'],
  goesWith: ['gehtmitYreferenzText'],
  requires: ['benoetigtYreferenzText'],
  customer: ['tkunde', 'tkuartnr', 'tkuartbez'],
  supplier: ['ylieferant', 'ylieferantartnr', 'ylieferantartbez'],
  manufacturer: ['yvendorText', 'yvendorName', 'yvendorbstnr'],
} as const

/** Die Spalte einer Paarliste, die eine hauseigene Artikelnummer trägt und in die Mappe führt */
export const ARTIKEL_PAIR_LIST_LINK_FIELD_NAMES: readonly string[] = [
  'werkzeugYreferenzText',
  'alternativeYreferenzText',
  'gehtmitYreferenzText',
  'benoetigtYreferenzText',
]

/** Felder, die ein Tab schon zeigt oder die nur Technik sind: Sie fehlen in „Übrige Felder“ */
export const ARTIKEL_FIELD_NAMES_SHOWN_ELSEWHERE: readonly string[] = [
  ...ARTIKEL_OVERVIEW_FIELD_NAMES,
  ...Object.values(ARTIKEL_PAIR_LIST_FIELD_NAMES).flat(),
  ARTIKEL_TABLE_FIELDS.bom.fieldName,
  ARTIKEL_TABLE_FIELDS.operations.fieldName,
  'werkzeugYreferenz',
  'alternativeYreferenz',
  'gehtmitYreferenz',
  'benoetigtYreferenz',
  // die Ablage der aufaggregierten Suche je Flag (ArtikelReferenceFlag.java), zeigt der Tab des Flags
  'tableWerkzeugAggregiert',
  'werkzeugAggregiertArtikelNummer',
  'werkzeugAggregiertTiefe',
  'werkzeugAggregiertAm',
  'tableGehtmitAggregiert',
  'gehtmitAggregiertArtikelNummer',
  'gehtmitAggregiertTiefe',
  'gehtmitAggregiertAm',
  'tableAlternativeAggregiert',
  'alternativeAggregiertArtikelNummer',
  'alternativeAggregiertTiefe',
  'alternativeAggregiertAm',
  'tableBenoetigtAggregiert',
  'benoetigtAggregiertArtikelNummer',
  'benoetigtAggregiertTiefe',
  'benoetigtAggregiertAm',
  'yvendor',
  'yreferenz',
  'artikelVorschauBildBase64Png',
  'id',
  'cursorDate',
  'userGrantedAccess',
  '_version_',
]
