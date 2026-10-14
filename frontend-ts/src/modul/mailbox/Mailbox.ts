import { request } from '../../api/client.ts'

/**
 * Ein Postfach, aus dem archiviert wird (Mailbox.java, nach der Vorlage). Am Postfach hängen die ARC-Positionen, die
 * aus ihm archivieren dürfen; Archivieren verlangt dazu die Rolle NACHRICHTEN_ARCHIVIEREN, beides prüft das Backend bei
 * jedem Aufruf. Nicht in den Stammdaten: Die ganze Liste bekommt nur der Admin, jeder andere User nur seine.
 */
export interface Mailbox {
  /** die Adresse, klein geschrieben, zugleich der Schlüssel, ändert sich nie */
  emailAddress: string
  /** Klartext für die Auswahl, z. B. „Bestellungen“, nicht mehrsprachig: ein Eigenname */
  displayName: string
  /** wozu das Postfach dient, Klartext */
  description: string | null
  listingPosition: number
  /** die ARC-Positionen, die aus diesem Postfach archivieren dürfen */
  accessRoleCollectionKeys: string[] | null
}

/** Aus welchen Postfächern der angemeldete User archivieren darf (MailboxesOfCurrentUser.java) */
export interface MailboxesOfCurrentUser {
  /** die Dienstpostfächer, an denen seine ARC oder eine ihrer Slave-ARCs steht, beim Admin alle */
  assignedMailboxes: Mailbox[]
  /** seine eigene E-Mail, zusätzlich wählbar; null, wenn sie nicht auf die Domäne der Postfächer endet */
  ownEmailAddress: string | null
}

/** Die Ordner eines Postfachs, aus denen archiviert wird (GraphMailFolder.java): Posteingang und Gesendete Elemente */
export type GraphMailFolder = 'INBOX' | 'SENT_ITEMS'

/** Alle Ordner in der Reihenfolge der Schalter */
export const GRAPH_MAIL_FOLDERS: readonly GraphMailFolder[] = ['INBOX', 'SENT_ITEMS']

/** Eine Zeile der Postfachliste (GraphMailHeader.java): genug, um eine Mail wiederzuerkennen */
export interface GraphMailHeader {
  /** die feste id der Mail im Postfach (ImmutableId) */
  exchangeMailId: string
  subject: string
  fromName: string | null
  /** klein geschrieben */
  fromAddress: string | null
  /** klein geschrieben, für die gesendeten Mails */
  toRecipientAddresses: string[]
  /** ISO-Zeitpunkt */
  receivedDateTime: string | null
  /** ISO-Zeitpunkt */
  sentDateTime: string | null
  hasAttachments: boolean
  /** die ersten rund 255 Zeichen als reiner Text */
  bodyPreview: string
  /** die Message-ID aus dem Kopf der Mail: Über sie fragt die Liste, ob die Mail schon archiviert ist. null ohne */
  internetMessageId: string | null
}

/** Ein Beteiligter einer Mail (GraphMailRecipient.java), Name und Adresse getrennt */
export interface GraphMailRecipient {
  name: string | null
  /** klein geschrieben */
  address: string
}

/** Ein Anhang einer Mail ohne Bytes (GraphMailAttachmentInfo.java) */
export interface GraphMailAttachmentInfo {
  name: string
  contentType: string | null
  /** Bytes laut Graph */
  size: number
  /** die DMS-id; null bei einem Anhang ohne Bytes (angehängte Mail, Verweis) */
  sha256: string | null
  /** im Mailtext eingebettet, z. B. ein Bild der Signatur */
  inline: boolean
}

/** Eine Mail zum Anzeigen vor dem Archivieren (GraphMailView.java): Bilder im Text schon als data: eingesetzt */
export interface GraphMailView {
  exchangeMailId: string
  subject: string
  from: GraphMailRecipient | null
  toRecipients: GraphMailRecipient[]
  ccRecipients: GraphMailRecipient[]
  bccRecipients: GraphMailRecipient[]
  sentDateTime: string | null
  receivedDateTime: string | null
  importance: string | null
  /** die ganze Mail samt Zitat oder Weiterleitung */
  bodyHtml: string
  /** nur der neue Teil; das wird ohne „ganze Mail“ archiviert */
  uniqueBodyHtml: string
  attachments: GraphMailAttachmentInfo[]
}

// ===== Endpunkte des MailboxController: die Postfächer des angemeldeten Users und ihre Mails =====

const MAILBOX_PATH = '/api/rest/v1/graph/mailbox'

/** Seine Postfächer. 403 ohne die Rolle NACHRICHTEN_ARCHIVIEREN. */
export function getMailboxesOfCurrentUser() {
  return request<MailboxesOfCurrentUser>('GET', `${MAILBOX_PATH}/mine`)
}

/** Die neuesten Mails eines Ordners, neueste zuerst. 403 ohne Zugriff auf dieses Postfach. */
export function getMailHeaders(mailbox: string, folder: GraphMailFolder) {
  return request<GraphMailHeader[]>('POST', `${MAILBOX_PATH}/headers`, { mailbox, folder })
}

/** Eine Mail mit allen Feldern, das Graph-JSON unverändert. 403 ohne Zugriff, 404 wenn das Postfach sie nicht hat. */
export function getMail(mailbox: string, exchangeMailId: string) {
  return request<Record<string, unknown>>('POST', `${MAILBOX_PATH}/mail`, { mailbox, exchangeMailId })
}

/** Eine Mail zum Anzeigen: Kopf, ganze Mail und neuer Teil mit eingesetzten Bildern, Anhänge ohne Bytes */
export function getMailView(mailbox: string, exchangeMailId: string) {
  return request<GraphMailView>('POST', `${MAILBOX_PATH}/mail/view`, { mailbox, exchangeMailId })
}

// ===== Endpunkte des MailboxAdminController =====

const MAILBOX_ADMIN_PATH = '/api/rest/v1/admin/graph/mailbox'

/** Alle Postfächer, sortiert nach Position und Adresse. Nur für den Admin. */
export function getAllMailboxes() {
  return request<Mailbox[]>('GET', MAILBOX_ADMIN_PATH)
}

/** Neues Postfach. Die Adresse schreibt das Backend klein, eine unbekannte ARC ist 400, eine vorhandene Adresse 409. */
export function createMailbox(newMailboxData: Mailbox) {
  return request<Mailbox>('POST', MAILBOX_ADMIN_PATH, newMailboxData)
}

/** Alle Felder, die Adresse bestimmt das Postfach und ändert sich nie. */
export function updateMailbox(changedMailboxData: Mailbox) {
  return request<Mailbox>('PUT', MAILBOX_ADMIN_PATH, changedMailboxData)
}

/** Jede Adresse bekommt ihre neue Position. Zurück kommen alle in neuer Reihenfolge. */
export function changeMailboxPositions(newPositionByEmailAddress: Record<string, number>) {
  return request<Mailbox[]>('POST', `${MAILBOX_ADMIN_PATH}/position`, newPositionByEmailAddress)
}

/** Löscht das Postfach endgültig. */
export function deleteMailbox(emailAddress: string) {
  return request<Mailbox>('DELETE', MAILBOX_ADMIN_PATH, { emailAddress })
}
