import { useTranslation } from 'react-i18next'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { FileSubClassSymbol } from '../filesubclass/FileSubClassSymbol.tsx'
import { type MessageSubClass, messageSubClassNameOf } from './MessageSubClass.ts'

interface AllMessageSubClassesAdminListProps {
  messageSubClasses: MessageSubClass[]
  onEdit: (messageSubClass: MessageSubClass) => void
  onDelete: (messageSubClass: MessageSubClass) => void
  /** nach Drag and Drop: die Arten in neuer Reihenfolge */
  onReorder: (messageSubClasses: MessageSubClass[]) => void
}

/**
 * Tabelle aller Nachrichtenarten für den Admin (User 2026-10-13, nach dem MessageSubClassTab der Vorlage): Symbol,
 * Key, Name, Leserollen, Hookpflege, Anzeigeorte und Postfächer, ganz rechts Bearbeiten und Löschen, sortierbar per
 * Drag and Drop. Zeigt nur an, lädt nichts.
 */
export function AllMessageSubClassesAdminList({ messageSubClasses, onEdit, onDelete, onReorder }: AllMessageSubClassesAdminListProps) {
  const { t } = useTranslation()
  const { language } = useLanguage()

  /** Eine Liste als Text, leer der Hinweis */
  function listText(values: string[] | null, emptyText: string) {
    return values && values.length > 0 ? values.join(', ') : <span className="text-muted">{emptyText}</span>
  }

  const columns: TableColumn<MessageSubClass>[] = [
    { header: '#', cell: (messageSubClass) => messageSubClass.listingPosition, unimportant: true },
    { header: t('messagesubclass.columns.symbol'), cell: (messageSubClass) => <FileSubClassSymbol fileSubClass={messageSubClass} /> },
    { header: t('messagesubclass.columns.key'), cell: (messageSubClass) => messageSubClass.key },
    { header: t('messagesubclass.columns.name'), cell: (messageSubClass) => messageSubClassNameOf(messageSubClass, language), unimportant: true },
    { header: t('messagesubclass.columns.read'), cell: (messageSubClass) => listText(messageSubClass.readAccessRoleKeys, t('messagesubclass.columns.onlyAdmin')), unimportant: true },
    { header: t('messagesubclass.columns.write'), cell: (messageSubClass) => listText(messageSubClass.writeAccessRoleKeys, t('messagesubclass.columns.onlyAdmin')), unimportant: true },
    { header: t('messagesubclass.columns.displayContextKeys'), cell: (messageSubClass) => listText(messageSubClass.displayContextKeys, t('messagesubclass.columns.none')), unimportant: true },
    {
      header: t('messagesubclass.columns.mailboxEmailAddresses'),
      cell: (messageSubClass) => listText(messageSubClass.mailboxEmailAddresses, t('messagesubclass.columns.none')),
      unimportant: true,
    },
  ]

  return (
    <Table
      columns={columns}
      rows={messageSubClasses}
      onReorder={onReorder}
      rowKey={(messageSubClass) => messageSubClass.key}
      detailTitle={(messageSubClass) => messageSubClass.key}
      emptyText={t('messagesubclass.empty')}
      actions={(messageSubClass) => (
        <>
          <EditButton label={t('messagesubclass.edit')} onClick={() => onEdit(messageSubClass)} />
          <DeleteButton label={t('messagesubclass.delete')} onClick={() => onDelete(messageSubClass)} />
        </>
      )}
    />
  )
}
