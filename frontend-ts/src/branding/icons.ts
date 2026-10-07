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
  fileExtensions: { pack: 'bootstrap', id: 'file-earmark-code' },
  fileMatrix: { pack: 'bootstrap', id: 'grid-3x3' },
  fileSubClassAcl: { pack: 'bootstrap', id: 'file-earmark-lock' },
  accessRoleCollectionAccessRoles: { pack: 'bootstrap', id: 'ui-checks-grid' },
  masterData: { pack: 'bootstrap', id: 'database-gear' },
  entityStates: { pack: 'bootstrap', id: 'flag-fill' },
  solrManager: { pack: 'bootstrap', id: 'hdd-network-fill' },
  dms: { pack: 'bootstrap', id: 'folder2-open' },
  // wie die Hooks-Spalte im Dokumente-Tab der Vorlage
  solrHooks: { pack: 'bootstrap', id: 'diagram-3' },
  solrHookGroups: { pack: 'bootstrap', id: 'tags' },
  solrCores: { pack: 'bootstrap', id: 'hdd-stack' },
  /** das Modul GraphManager: Microsoft Graph, Mailvorlagen und später die Postfächer */
  graph: { pack: 'bootstrap', id: 'microsoft' },
  mailTemplates: { pack: 'bootstrap', id: 'envelope-paper' },
  /** das Modul Freigaben: Dateifreigaben und Feldfreigaben je Kern */
  grant: { pack: 'bootstrap', id: 'person-check-fill' },
  documentGrants: { pack: 'bootstrap', id: 'file-earmark-check' },
  fieldGrants: { pack: 'bootstrap', id: 'list-check' },
  hookGrants: { pack: 'bootstrap', id: 'link-45deg' },
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
  upload: { pack: 'bootstrap', id: 'upload' },
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
