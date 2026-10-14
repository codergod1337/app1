import { request } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { translate } from '../../components/multilingual.ts'
import type { PackSymbol } from '../../components/PackSymbol.ts'

/**
 * Eine Nachrichtenart (MSC), wie sie das Backend liefert (MessageSubClass.java), nach der Vorlage: das Gegenstück zur
 * Dateiart. An ihr hängt, wer eine archivierte Nachricht lesen und wer ihre Hooks pflegen darf. Gelesen wird über die
 * Stammdaten.
 */
export interface MessageSubClass {
  /** z. B. REKLAMATION: nur A–Z, 0–9 und _, beginnt und endet mit einem Großbuchstaben */
  key: string
  /** mehrsprachiges JSON, z. B. {"de":"Reklamation"} */
  displayName: string
  /** mehrsprachiges JSON wie displayName */
  description: string | null
  listingPosition: number
  /** Symbol der Nachrichtenart, wie an der Dateiart */
  symbol: PackSymbol | null
  /** Farbe des Symbols, immer Hex. null: Standard */
  color: string | null
  /** wer Nachrichten dieser Art lesen darf. Enthält immer auch alle Schreibrollen, das setzt das Backend durch. */
  readAccessRoleKeys: string[] | null
  /** wer die Hooks einer Nachricht dieser Art pflegen darf, nie die Nachricht selbst */
  writeAccessRoleKeys: string[] | null
  /** die Anzeigeorte aus demselben Katalog wie bei den Dateiarten (FileSubClassDestination), z. B. KUNDENMAPPE */
  displayContextKeys: string[] | null
  /**
   * die Postfächer, die an dieser Art hängen: Später wird eine Nachricht anhand ihres Postfachs dieser Art zugeordnet
   * oder die Art danach vorausgewählt; ein Postfach an höchstens einer Art
   */
  mailboxEmailAddresses: string[] | null
  /** Wörter in Betreff und Mailtext, die für diese Art sprechen: je Treffer ein Punkt beim Vorschlag */
  triggerWords: string[] | null
  /** Hooks, deren Wert für diese Art spricht, z. B. auftragsNummer für AUFTRAG: je Hook mit Wert zwei Punkte */
  triggerHookKeys: string[] | null
}

/** Der Anzeigename einer Nachrichtenart in der Sprache, sonst der key */
export function messageSubClassNameOf(messageSubClass: MessageSubClass, language: LanguageCode): string {
  return translate(messageSubClass.displayName, language) || messageSubClass.key
}

// ===== Endpunkte des MessageSubClassAdminController. Gelesen wird über die Stammdaten (useMasterData). =====

const MESSAGE_SUB_CLASS_ADMIN_PATH = '/api/rest/v1/admin/messagesubclass'

/** Neue Nachrichtenart. Unbekannte AR-Keys lässt das Backend weg, ein unbekannter Anzeigeort ist 400, ein schon vergebenes Postfach 409. */
export function createMessageSubClass(newMessageSubClassData: MessageSubClass) {
  return request<MessageSubClass>('POST', MESSAGE_SUB_CLASS_ADMIN_PATH, newMessageSubClassData)
}

/** Alle Felder, der key bestimmt die Art und ändert sich nie. */
export function updateMessageSubClass(changedMessageSubClassData: MessageSubClass) {
  return request<MessageSubClass>('PUT', MESSAGE_SUB_CLASS_ADMIN_PATH, changedMessageSubClassData)
}

/** Jeder key bekommt seine neue Position, z. B. { ANGEBOT: 1, REKLAMATION: 2 }. Zurück kommen alle in neuer Reihenfolge. */
export function changeMessageSubClassPositions(newPositionByKey: Record<string, number>) {
  return request<MessageSubClass[]>('POST', `${MESSAGE_SUB_CLASS_ADMIN_PATH}/position`, newPositionByKey)
}

/** Löscht endgültig. Nachrichten dieser Art haben danach keinen Namen und kein Symbol mehr. */
export function deleteMessageSubClass(key: string) {
  return request<MessageSubClass>('DELETE', MESSAGE_SUB_CLASS_ADMIN_PATH, { key })
}
