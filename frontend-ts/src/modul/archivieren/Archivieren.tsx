import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router'
import { useLoad } from '../../api/useLoad.ts'
import { Icon } from '../../components/Icon.tsx'
import { Subheader, type SubheaderTab, type SubheaderToggle } from '../../components/Subheader.tsx'
import { showToast } from '../../components/toastStore.ts'
import { useUsersSettings } from '../../components/usersSettingsContext.ts'
import { GRAPH_MAIL_FOLDERS, getMailboxesOfCurrentUser } from '../mailbox/Mailbox.ts'
import {
  ARCHIVIEREN_FOLDER_SETTING_KEY,
  ARCHIVIEREN_MAILBOXES_OFF_SETTING_KEY,
  ARCHIVIEREN_PATH,
  archivierenFolderOf,
  archivierenMailboxesOf,
  switchedOffMailboxesOf,
  type ArchivierenOutletContext,
} from './Archivieren.ts'

/**
 * Das Modul Archivieren (User 2026-10-14, nach der Archivierung der Vorlage): Mails aus den Postfächern des Users ins
 * Archiv. Eintritt mit NACHRICHTEN_ARCHIVIEREN oder ADMIN.
 *
 * Im Subheader je Postfach ein Schalter, die Dienstpostfächer und, wenn erlaubt, die eigene Adresse, dahinter die
 * Wahl des Ordners: Posteingang oder Gesendet. Beides steht in den UsersSettings und gilt nach jedem Neuladen. Nur die
 * eingeschalteten Postfächer werden abgerufen; welche er überhaupt hat, sagt das Backend (/mine).
 */
export function Archivieren() {
  const { t } = useTranslation()
  const { settingValue, changeSetting } = useUsersSettings()
  const { data: mailboxesOfCurrentUser, errorMessage: mailboxesErrorMessage } = useLoad(getMailboxesOfCurrentUser)
  const mailboxes = mailboxesOfCurrentUser ? archivierenMailboxesOf(mailboxesOfCurrentUser) : null
  const switchedOff = switchedOffMailboxesOf(settingValue(ARCHIVIEREN_MAILBOXES_OFF_SETTING_KEY))
  const folder = archivierenFolderOf(settingValue(ARCHIVIEREN_FOLDER_SETTING_KEY))
  const activeMailboxes = (mailboxes ?? []).filter((mailbox) => !switchedOff.includes(mailbox.emailAddress))

  function saveSetting(key: string, value: string) {
    changeSetting(key, value).catch((error: unknown) =>
      showToast({ kind: 'error', title: t('archivieren.settingNotSaved'), text: error instanceof Error ? error.message : undefined }),
    )
  }

  /** Ein Postfach an- oder ausschalten; Adressen, die er nicht mehr hat, fallen dabei aus der Einstellung */
  function toggleMailbox(emailAddress: string) {
    const known = (mailboxes ?? []).map((mailbox) => mailbox.emailAddress)
    const next = switchedOff.includes(emailAddress)
      ? switchedOff.filter((candidate) => candidate !== emailAddress)
      : [...switchedOff, emailAddress]
    saveSetting(ARCHIVIEREN_MAILBOXES_OFF_SETTING_KEY, JSON.stringify(next.filter((candidate) => known.includes(candidate))))
  }

  const tabs: SubheaderTab[] = [{ label: t('archivieren.tabs.mailboxes'), icon: 'mailboxes', to: `${ARCHIVIEREN_PATH}/mailboxes` }]
  const toggles: SubheaderToggle[] = [
    ...(mailboxes ?? []).map((mailbox) => ({
      key: mailbox.emailAddress,
      label: mailbox.label,
      icon: mailbox.own ? ('user' as const) : ('mailboxes' as const),
      active: !switchedOff.includes(mailbox.emailAddress),
      hint: mailbox.own ? t('archivieren.ownMailboxHint', { emailAddress: mailbox.emailAddress }) : mailbox.emailAddress,
      onClick: () => toggleMailbox(mailbox.emailAddress),
    })),
    ...GRAPH_MAIL_FOLDERS.map((candidate, index) => ({
      key: `folder-${candidate}`,
      label: t(`archivieren.folders.${candidate}`),
      icon: candidate === 'INBOX' ? ('inbox' as const) : ('send' as const),
      active: folder === candidate,
      separatorBefore: index === 0,
      onClick: () => saveSetting(ARCHIVIEREN_FOLDER_SETTING_KEY, candidate),
    })),
  ]

  const context: ArchivierenOutletContext = { mailboxes, activeMailboxes, folder, mailboxesErrorMessage }

  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="archivieren" /> {t('archivieren.title')}
          </>
        }
        tabs={tabs}
        toggles={toggles}
        sticky
      />
      <div className="main-content">
        <Outlet context={context} />
      </div>
    </>
  )
}
