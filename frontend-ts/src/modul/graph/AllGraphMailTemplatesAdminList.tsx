import { useTranslation } from 'react-i18next'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { translate } from '../../components/multilingual.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import type { GraphMailTemplate } from './GraphMailTemplate.ts'

interface AllGraphMailTemplatesAdminListProps {
  graphMailTemplates: GraphMailTemplate[]
  onEdit: (graphMailTemplate: GraphMailTemplate) => void
  onDelete: (graphMailTemplate: GraphMailTemplate) => void
  onReorder: (newOrder: GraphMailTemplate[]) => void
}

/** Die Tabelle der Mailvorlagen, nur Anzeige. Daten und Aktionen kommen von außen. */
export function AllGraphMailTemplatesAdminList({
  graphMailTemplates,
  onEdit,
  onDelete,
  onReorder,
}: AllGraphMailTemplatesAdminListProps) {
  const { t } = useTranslation()

  const columns: TableColumn<GraphMailTemplate>[] = [
    { header: '#', cell: (graphMailTemplate) => graphMailTemplate.listingPosition, unimportant: true },
    { header: t('common.key'), cell: (graphMailTemplate) => <strong>{graphMailTemplate.key}</strong> },
    {
      header: t('graph.mailTemplates.columns.type'),
      cell: (graphMailTemplate) => t(`graph.mailTemplates.types.${graphMailTemplate.type}`),
    },
    { header: t('common.displayName'), cell: (graphMailTemplate) => translate(graphMailTemplate.displayName) },
    {
      header: t('graph.mailTemplates.columns.subject'),
      cell: (graphMailTemplate) => translate(graphMailTemplate.subject),
      unimportant: true,
    },
    {
      header: t('graph.mailTemplates.columns.updatedAt'),
      cell: (graphMailTemplate) =>
        graphMailTemplate.updatedAt ? new Date(graphMailTemplate.updatedAt).toLocaleString() : '',
      unimportant: true,
    },
  ]

  return (
    <Table
      columns={columns}
      rows={graphMailTemplates}
      onReorder={onReorder}
      rowKey={(graphMailTemplate) => graphMailTemplate.key}
      detailTitle={(graphMailTemplate) => graphMailTemplate.key}
      emptyText={t('graph.mailTemplates.empty')}
      actions={(graphMailTemplate) => (
        <>
          <EditButton label={t('common.edit')} onClick={() => onEdit(graphMailTemplate)} />
          <DeleteButton label={t('common.deleteAction')} onClick={() => onDelete(graphMailTemplate)} />
        </>
      )}
    />
  )
}
