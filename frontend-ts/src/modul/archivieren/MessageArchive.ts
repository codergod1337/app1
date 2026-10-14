import { request } from '../../api/client.ts'
import type { AbasBelegnummerLookupResult } from '../belegnummer/Belegnummer.ts'
import type { GraphMailFolder, GraphMailView } from '../mailbox/Mailbox.ts'

/** Wie ein Anhang beim Archivieren dasteht (MessageArchiveAttachmentState.java) */
export type MessageArchiveAttachmentState = 'NEW' | 'MERGE' | 'SIGNATURE' | 'IGNORED' | 'NO_FILE' | 'NO_ACCESS'

/** Ein Anhang auf der Aufbereiten-Seite, ohne Bytes (MessageArchiveAttachment.java) */
export interface MessageArchiveAttachment {
  name: string
  contentType: string | null
  size: number
  /** die DMS-id; null bei NO_FILE */
  sha256: string | null
  inline: boolean
  /** ein Bild, das als data: direkt im HTML steht, kein Anhang bei Graph */
  embeddedInText: boolean
  state: MessageArchiveAttachmentState
  /** die Dateiart des vorhandenen DMS-Dokuments bei MERGE, SIGNATURE und IGNORED */
  existingFileSubClassKey: string | null
  /** nur bei NEW: je beschreibbarer Dateiart der feste Teil des Vorschlags aus den Triggerwörtern, auch 0 */
  fileSubClassBaseScores: Record<string, number>
}

/** Die Rolle eines Beteiligten in der Mail */
export type MessageArchiveParticipantRole = 'FROM' | 'SENDER' | 'TO' | 'CC' | 'BCC'

/** Ein Beteiligter mit den 19er-Kontakten zu seiner Adresse (MessageArchiveParticipant.java) */
export interface MessageArchiveParticipant {
  role: MessageArchiveParticipantRole
  name: string | null
  address: string
  /** die Kontakte aus dem Kern KONTAKTE, so weit der User sie lesen darf */
  contacts: Record<string, unknown>[]
}

/** Eine Nummer, die ABAS kennt (MessageArchiveNumber.java) */
export interface MessageArchiveNumber {
  lookup: AbasBelegnummerLookupResult
  /** der Hook, dessen abasObjectTypes ihren Typ nennen; null: der User wählt */
  suggestedHookKey: string | null
}

/** Alles für die Aufbereiten-Seite (MessageArchivePreparation.java) */
export interface MessageArchivePreparation {
  mail: GraphMailView
  attachments: MessageArchiveAttachment[]
  numbers: MessageArchiveNumber[]
  participants: MessageArchiveParticipant[]
  /** false: ohne die AR KONTAKTE, die Kontakte fehlen */
  contactsAllowed: boolean
  /** der feste Teil des Vorschlags je Nachrichtenart: Postfach 3, je Triggerwort-Treffer 1; nur Arten mit Punkten */
  messageSubClassBaseScores: Record<string, number>
  /** die vorgeschlagenen Hooks: Kunden- und Lieferantennummern der Kontakte, Nummern mit erkanntem Hook */
  suggestedHooks: Record<string, string[]>
}

/** Die Dateiart, wenn kein Triggerwort und kein Trigger-Hook trifft (User 2026-10-14) */
export const MESSAGE_ARCHIVE_FALLBACK_FILE_SUB_CLASS_KEY = 'TEMP'

/** Punkte je Trigger-Hook mit Wert, für Nachrichtenart und Dateiart */
const TRIGGER_HOOK_POINTS = 2

/** Was mit einer Datei beim Archivieren geschieht (MessageArchiveAttachmentAction.java) */
export type MessageArchiveAttachmentAction = 'ARCHIVE' | 'SIGNATURE' | 'IGNORE' | 'SKIP'

/** Was die Seite zum Archivieren schickt, nur die Entscheidungen (MessageArchiveRequest.java) */
export interface MessageArchiveRequest {
  mailbox: string
  exchangeMailId: string
  folder: GraphMailFolder
  messageSubClassKey: string
  /** die ganze Mail samt Verlauf, sonst nur der neue Teil */
  wholeMail: boolean
  hooks: Record<string, string[]>
  attachments: { sha256: string; action: MessageArchiveAttachmentAction; fileSubClassKey: string | null }[]
}

/** Was beim Archivieren herauskam (MessageArchiveResult.java) */
export interface MessageArchiveResult {
  /** die laufende Nummer der Nachricht */
  id: string
  newAttachments: number
  mergedAttachments: number
  signatures: number
  ignored: number
}

const MESSAGE_ARCHIVE_PATH = '/api/rest/v1/message/archive'

/** Alles für die Aufbereiten-Seite einer Mail. Speichert nichts. */
export function prepareMessageArchive(mailbox: string, exchangeMailId: string) {
  return request<MessageArchivePreparation>('POST', `${MESSAGE_ARCHIVE_PATH}/prepare`, { mailbox, exchangeMailId })
}

/** Archiviert die Mail: Anhänge ins DMS, die Nachricht in den Kern NACHRICHTEN. 409, wenn sie schon im Archiv liegt. */
export function archiveMessage(archiveRequest: MessageArchiveRequest) {
  return request<MessageArchiveResult>('POST', MESSAGE_ARCHIVE_PATH, archiveRequest)
}

/**
 * Welche Mails der Postfachliste schon im Archiv liegen: internetMessageId -> laufende Nummer der Nachricht. Fehlt eine,
 * ist sie nicht archiviert. Mit der Rolle des Archivierens, ohne Leserecht an den Nachrichten.
 */
export function getArchivedNachrichtenIds(internetMessageIds: readonly string[]) {
  return request<Record<string, string>>('POST', `${MESSAGE_ARCHIVE_PATH}/archived`, { internetMessageIds })
}

/**
 * Der Vorschlag unter Kandidaten (User 2026-10-14): fester Teil aus dem Backend (Postfach, Triggerwörter) plus 2 Punkte
 * je Trigger-Hook, der an der Nachricht einen Wert hat. So springt der Vorschlag mit, sobald Hooks gesetzt werden. Die
 * meisten Punkte gewinnen, bei Gleichstand der erste in der Reihenfolge der Kandidaten (Listenposition); ohne Punkte
 * null. Nur ein Vorschlag: Was archiviert wird, wählt der User, das Backend prüft es.
 */
export function suggestedKeyOf(
  candidates: readonly { key: string; triggerHookKeys: readonly string[] | null }[],
  baseScores: Readonly<Record<string, number>>,
  hooks: Readonly<Record<string, readonly string[]>>,
): string | null {
  let best: string | null = null
  let bestScore = 0
  for (const candidate of candidates) {
    const hookPoints = (candidate.triggerHookKeys ?? []).filter((hookKey) => (hooks[hookKey]?.length ?? 0) > 0).length * TRIGGER_HOOK_POINTS
    const score = (baseScores[candidate.key] ?? 0) + hookPoints
    if (score > bestScore) {
      best = candidate.key
      bestScore = score
    }
  }
  return best
}
