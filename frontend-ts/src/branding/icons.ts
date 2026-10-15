import type { PackSymbol } from '../components/PackSymbol.ts'

/**
 * Zuordnung Name → Symbol aus einem der Pakete (Bootstrap Icons, Tabler, Lucide).
 * Komponenten fordern Symbole nur über den Namen an. Wer die Symbole tauschen will, ändert nur diese Liste.
 */
export const icons = {
  home: { pack: 'bootstrap', id: 'house-fill' },
  admin: { pack: 'bootstrap', id: 'gear-fill' },
  users: { pack: 'bootstrap', id: 'people-fill' },
  accessRoles: { pack: 'bootstrap', id: 'shield-lock-fill' },
  accessRoleCollections: { pack: 'tabler', id: 'desk' },
  fileSubClasses: { pack: 'bootstrap', id: 'files' },
  /** die Anzeigeorte der Dateiarten: wo eine Dateiart als Abschnitt steht */
  fileSubClassDestinations: { pack: 'bootstrap', id: 'signpost-split' },
  /** die Nachrichtenarten (MSC): wer eine archivierte Nachricht lesen und ihre Hooks pflegen darf */
  messageSubClasses: { pack: 'bootstrap', id: 'envelope-paper' },
  /** die Postfächer, aus denen archiviert wird */
  mailboxes: { pack: 'bootstrap', id: 'mailbox' },
  // Kachel und Modul Archivieren: Mails aus den Postfächern ins Archiv
  archivieren: { pack: 'bootstrap', id: 'archive-fill' },
  // Kachel und Modul Testdaten: Dummy-Daten für die Solr-Kerne
  testfakedata: { pack: 'bootstrap', id: 'database-fill-add' },
  // Kachel und Modul Anfragenmonitor: die Map der Anfragen an den Login-Endpunkt, live
  requestmonitor: { pack: 'bootstrap', id: 'activity' },
  // Todo-Jobs: die Kachel, der Schreibtisch im Profil, die Zahl im header (User 2026-10-16)
  todojob: { pack: 'bootstrap', id: 'clipboard-check' },
  // ein Servant trägt sich für einen Gruppenjob ein
  claim: { pack: 'bootstrap', id: 'hand-index-thumb' },
  // Rückfrage des Servants und Rückantwort des Owners
  question: { pack: 'bootstrap', id: 'chat-left-text' },
  reply: { pack: 'bootstrap', id: 'reply-fill' },
  comment: { pack: 'bootstrap', id: 'chat-dots' },
  // die Frist eines Jobs
  due: { pack: 'bootstrap', id: 'alarm' },
  // der Owner gibt die Rückmeldung zurück
  returned: { pack: 'bootstrap', id: 'arrow-counterclockwise' },
  // der Ordner Posteingang eines Postfachs
  inbox: { pack: 'bootstrap', id: 'inbox-fill' },
  // eine Mail hat Anhänge
  attachment: { pack: 'bootstrap', id: 'paperclip' },
  fileExtensions: { pack: 'bootstrap', id: 'file-earmark-code' },
  fileMatrix: { pack: 'bootstrap', id: 'grid-3x3' },
  fileSubClassAcl: { pack: 'bootstrap', id: 'file-earmark-lock' },
  accessRoleCollectionAccessRoles: { pack: 'bootstrap', id: 'ui-checks-grid' },
  masterData: { pack: 'bootstrap', id: 'database-gear' },
  entityStates: { pack: 'bootstrap', id: 'flag-fill' },
  solrManager: { pack: 'bootstrap', id: 'hdd-network-fill' },
  /** das Modul AbasManager: die Anbindung an das ABAS-ERP, Sternzeiten holen und spiegeln */
  abasManager: { pack: 'bootstrap', id: 'cloud-arrow-down-fill' },
  /** das Modul Mapping: JSON als Baum ansehen und Feldern JSON-Adressen hinterlegen */
  mapping: { pack: 'bootstrap', id: 'diagram-2-fill' },
  /** das Modul Infosysteme: die hinterlegten Aufrufe bei ABAS mit Platzhaltern, von Subfetches und Masken genutzt */
  infosys: { pack: 'bootstrap', id: 'collection-fill' },
  dms: { pack: 'bootstrap', id: 'folder2-open' },
  /** das Modul Artikel: die Artikelmappe aus dem Kern ARTIKEL, dem Spiegel des ERP */
  artikel: { pack: 'bootstrap', id: 'box-seam' },
  /** ein Treffer des Kerns KONTAKTE, der eine Firma ist (kontaktorig „Adresse“): das Gebäude wie in der Vorlage */
  kontaktFirma: { pack: 'bootstrap', id: 'building' },
  /** ein Treffer des Kerns KONTAKTE, der ein Ansprechpartner ist (kontaktorig „Kontakt“): der Kopf wie in der Vorlage */
  kontaktPerson: { pack: 'bootstrap', id: 'person' },
  /** das Modul Belegnummer: eine Nummer bei ABAS nachschlagen, was sie ist und wo sie steht */
  belegnummer: { pack: 'bootstrap', id: 'upc-scan' },
  /** die Favoriten-Ansicht einer Dokumentenliste: eigene Reihenfolge, Abschnitte auf- und zuklappbar */
  favorite: { pack: 'bootstrap', id: 'star-fill' },
  // wie die Hooks-Spalte im Dokumente-Tab der Vorlage
  solrHooks: { pack: 'bootstrap', id: 'diagram-3' },
  solrHookGroups: { pack: 'bootstrap', id: 'tags' },
  solrCores: { pack: 'bootstrap', id: 'hdd-stack' },
  /** das Modul GraphManager: Microsoft Graph, Mailvorlagen und später die Postfächer */
  graph: { pack: 'bootstrap', id: 'microsoft' },
  mailTemplates: { pack: 'bootstrap', id: 'envelope-paper' },
  /** Auflösen: exakt nach Feld und Wert */
  resolve: { pack: 'bootstrap', id: 'funnel-fill' },
  /** das Auge: eine Datei im Viewer ansehen */
  view: { pack: 'bootstrap', id: 'eye-fill' },
  /** die Daten zu einem Dokument */
  details: { pack: 'bootstrap', id: 'info-circle-fill' },
  /** der Primärschlüssel eines Kerns: das Feld id, der uniqueKey in Solr */
  documentId: { pack: 'bootstrap', id: 'key' },
  search: { pack: 'bootstrap', id: 'search' },
  /** ein Filter der Suche, der Trichter ohne Füllung, anders als resolve */
  filter: { pack: 'bootstrap', id: 'funnel' },
  /** der Volltext eines Dokuments, z. B. als Treffergrund */
  fullText: { pack: 'bootstrap', id: 'file-text' },
  download: { pack: 'bootstrap', id: 'download' },
  /** Freigeben per Abholschein: der Link an jemanden ohne Konto */
  share: { pack: 'bootstrap', id: 'share-fill' },
  upload: { pack: 'bootstrap', id: 'upload' },
  /** eine neue Fassung an eine Versionskette hängen: das Journal mit dem Pfeil nach oben, wie in der Vorlage */
  newVersion: { pack: 'bootstrap', id: 'journal-arrow-up' },
  camera: { pack: 'bootstrap', id: 'camera-fill' },
  add: { pack: 'bootstrap', id: 'plus-lg' },
  back: { pack: 'bootstrap', id: 'arrow-left' },
  edit: { pack: 'bootstrap', id: 'pencil-fill' },
  delete: { pack: 'bootstrap', id: 'trash3-fill' },
  password: { pack: 'bootstrap', id: 'key-fill' },
  login: { pack: 'bootstrap', id: 'box-arrow-in-right' },
  logout: { pack: 'bootstrap', id: 'box-arrow-right' },
  user: { pack: 'bootstrap', id: 'person-circle' },
  refresh: { pack: 'bootstrap', id: 'arrow-repeat' },
  check: { pack: 'bootstrap', id: 'check-lg' },
  error: { pack: 'bootstrap', id: 'exclamation-lg' },
  info: { pack: 'bootstrap', id: 'info-lg' },
  close: { pack: 'bootstrap', id: 'x-lg' },
  drag: { pack: 'bootstrap', id: 'grip-vertical' },
  expand: { pack: 'bootstrap', id: 'chevron-down' },
  /** der zugeklappte Aufklapper, Gegenstück zu expand */
  collapsed: { pack: 'bootstrap', id: 'chevron-right' },
  copy: { pack: 'bootstrap', id: 'copy' },
  calendar: { pack: 'bootstrap', id: 'calendar3' },
  session: { pack: 'bootstrap', id: 'display' },
  private: { pack: 'bootstrap', id: 'eye-slash' },
  locked: { pack: 'bootstrap', id: 'lock-fill' },
  warning: { pack: 'bootstrap', id: 'exclamation-triangle-fill' },
  guestbook: { pack: 'bootstrap', id: 'journal-bookmark-fill' },
  send: { pack: 'bootstrap', id: 'send-fill' },
  userDeleted: { pack: 'bootstrap', id: 'person-x' },
  website: { pack: 'bootstrap', id: 'globe2' },
  mobile: { pack: 'bootstrap', id: 'phone' },
  discord: { pack: 'bootstrap', id: 'discord' },
  steam: { pack: 'bootstrap', id: 'steam' },
  instagram: { pack: 'bootstrap', id: 'instagram' },
} satisfies Record<string, PackSymbol>

export type IconName = keyof typeof icons
