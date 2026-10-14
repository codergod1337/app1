import { ADMIN_ACCESS_ROLE_KEY } from '../accessrole/AccessRole.ts'
import type { GraphMailFolder, MailboxesOfCurrentUser } from '../mailbox/Mailbox.ts'

/** Der Pfad des Moduls Archivieren */
export const ARCHIVIEREN_PATH = '/archivieren'

/** Die Rolle des Archivierens (MailboxService.ARCHIVE_ACCESS_ROLE_KEY): ohne sie kein Postfach, auch nicht das eigene */
export const ARCHIVIEREN_ACCESS_ROLE_KEY = 'NACHRICHTEN_ARCHIVIEREN'

/** Wer das Modul betritt: die Rolle des Archivierens oder ADMIN, eine reicht. Dieselben für Kachel und Route. */
export const ARCHIVIEREN_ENTRY_ACCESS_ROLE_KEYS: readonly string[] = [ARCHIVIEREN_ACCESS_ROLE_KEY, ADMIN_ACCESS_ROLE_KEY]

/**
 * UsersSetting (User 2026-10-14): die ausgeschalteten Postfächer als JSON-Liste ihrer Adressen. Gemerkt wird, was aus
 * ist, nicht was an ist: Ein neu zugewiesenes Postfach ist so von selbst eingeschaltet.
 */
export const ARCHIVIEREN_MAILBOXES_OFF_SETTING_KEY = 'ARCHIVIEREN_MAILBOXES_OFF'

/** UsersSetting: der gewählte Ordner, INBOX oder SENT_ITEMS; ohne Einstellung der Posteingang */
export const ARCHIVIEREN_FOLDER_SETTING_KEY = 'ARCHIVIEREN_FOLDER'

/** Ein Postfach zur Auswahl: ein Dienstpostfach oder die eigene Adresse */
export interface ArchivierenMailbox {
  /** klein geschrieben */
  emailAddress: string
  /** der Anzeigename des Dienstpostfachs, bei der eigenen Adresse die Adresse */
  label: string
  /** die eigene Adresse des Users, kein Dienstpostfach */
  own: boolean
}

/** Was das Modul an seine Reiter weitergibt (Outlet-Kontext) */
export interface ArchivierenOutletContext {
  /** alle Postfächer des Users; null, solange sie laden */
  mailboxes: ArchivierenMailbox[] | null
  /** die eingeschalteten */
  activeMailboxes: ArchivierenMailbox[]
  folder: GraphMailFolder
  /** Fehler beim Laden der Postfächer, z. B. 403 ohne die Rolle */
  mailboxesErrorMessage: string | null
}

/** Die Dienstpostfächer in ihrer Reihenfolge, dahinter die eigene Adresse, wenn sie erlaubt ist */
export function archivierenMailboxesOf(mailboxesOfCurrentUser: MailboxesOfCurrentUser): ArchivierenMailbox[] {
  const mailboxes: ArchivierenMailbox[] = mailboxesOfCurrentUser.assignedMailboxes.map((mailbox) => ({
    emailAddress: mailbox.emailAddress,
    label: mailbox.displayName,
    own: false,
  }))
  const ownEmailAddress = mailboxesOfCurrentUser.ownEmailAddress
  if (ownEmailAddress !== null && !mailboxes.some((mailbox) => mailbox.emailAddress === ownEmailAddress)) {
    mailboxes.push({ emailAddress: ownEmailAddress, label: ownEmailAddress, own: true })
  }
  return mailboxes
}

/** Die ausgeschalteten Adressen aus der Einstellung; fehlt sie oder ist sie kaputt, ist nichts aus */
export function switchedOffMailboxesOf(settingValue: string | null): string[] {
  if (settingValue === null) {
    return []
  }
  try {
    const parsed: unknown = JSON.parse(settingValue)
    return Array.isArray(parsed) ? parsed.filter((entry): entry is string => typeof entry === 'string') : []
  } catch {
    return []
  }
}

/** Der Ordner aus der Einstellung, ohne oder bei einem fremden Wert der Posteingang */
export function archivierenFolderOf(settingValue: string | null): GraphMailFolder {
  return settingValue === 'SENT_ITEMS' ? 'SENT_ITEMS' : 'INBOX'
}
