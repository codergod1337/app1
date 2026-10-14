import { useTranslation } from 'react-i18next'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { messageSubClassNameOf } from '../messagesubclass/MessageSubClass.ts'
import type { Mailbox } from './Mailbox.ts'

interface AllMailboxesAdminListProps {
  mailboxes: Mailbox[]
  onEdit: (mailbox: Mailbox) => void
  onDelete: (mailbox: Mailbox) => void
  /** nach Drag and Drop: die Postfächer in neuer Reihenfolge */
  onReorder: (mailboxes: Mailbox[]) => void
}

/**
 * Tabelle aller Postfächer für den Admin (User 2026-10-13, nach dem MailboxTab der Vorlage): Adresse, Name, die
 * ARC-Positionen, die daraus archivieren dürfen, und die Nachrichtenart, die das Postfach automatisch bekommt (lose,
 * aus den Nachrichtenarten gelesen). Ganz rechts Bearbeiten und Löschen, sortierbar per Drag and Drop.
 */
export function AllMailboxesAdminList({ mailboxes, onEdit, onDelete, onReorder }: AllMailboxesAdminListProps) {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const { messageSubClasses } = useMasterData()

  /** Die Nachrichtenart, an der dieses Postfach hängt; leer ohne */
  function messageSubClassNameOfMailbox(mailbox: Mailbox): string {
    const messageSubClass = (messageSubClasses ?? []).find((candidate) => (candidate.mailboxEmailAddresses ?? []).includes(mailbox.emailAddress))
    return messageSubClass ? messageSubClassNameOf(messageSubClass, language) : ''
  }

  const columns: TableColumn<Mailbox>[] = [
    { header: '#', cell: (mailbox) => mailbox.listingPosition, unimportant: true },
    { header: t('mailbox.columns.emailAddress'), cell: (mailbox) => <strong>{mailbox.emailAddress}</strong> },
    { header: t('common.displayName'), cell: (mailbox) => mailbox.displayName },
    {
      header: t('mailbox.columns.accessRoleCollectionKeys'),
      cell: (mailbox) =>
        (mailbox.accessRoleCollectionKeys ?? []).length > 0 ? (mailbox.accessRoleCollectionKeys ?? []).join(', ') : <span className="text-muted">{t('mailbox.columns.onlyAdmin')}</span>,
      unimportant: true,
    },
    {
      header: t('mailbox.columns.messageSubClass'),
      cell: (mailbox) => messageSubClassNameOfMailbox(mailbox) || <span className="text-muted">{t('mailbox.columns.none')}</span>,
      unimportant: true,
    },
  ]

  return (
    <Table
      columns={columns}
      rows={mailboxes}
      onReorder={onReorder}
      rowKey={(mailbox) => mailbox.emailAddress}
      detailTitle={(mailbox) => mailbox.emailAddress}
      emptyText={t('mailbox.empty')}
      actions={(mailbox) => (
        <>
          <EditButton label={t('mailbox.edit')} onClick={() => onEdit(mailbox)} />
          <DeleteButton label={t('mailbox.delete')} onClick={() => onDelete(mailbox)} />
        </>
      )}
    />
  )
}
