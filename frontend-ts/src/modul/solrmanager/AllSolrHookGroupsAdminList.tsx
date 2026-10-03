import { useTranslation } from 'react-i18next'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { translate } from '../../components/multilingual.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import type { SolrHookGroup } from './SolrHookGroup.ts'

interface AllSolrHookGroupsAdminListProps {
  solrHookGroups: SolrHookGroup[]
  /** wie viele Hooks in jeder Gruppe stehen, nach key */
  hookCountByKey: Record<string, number>
  onEdit: (solrHookGroup: SolrHookGroup) => void
  onDelete: (solrHookGroup: SolrHookGroup) => void
  /** nach Drag and Drop: die Gruppen in neuer Reihenfolge */
  onReorder: (solrHookGroups: SolrHookGroup[]) => void
}

/**
 * Tabelle aller Hook-Gruppen für den Admin, ganz rechts die Aktionen Bearbeiten und Löschen. Zeigt nur an, lädt
 * nichts: die Daten kommen als Parameter, was die Aktionen tun, bestimmt der Aufrufer.
 */
export function AllSolrHookGroupsAdminList({
  solrHookGroups,
  hookCountByKey,
  onEdit,
  onDelete,
  onReorder,
}: AllSolrHookGroupsAdminListProps) {
  const { t } = useTranslation()

  const columns: TableColumn<SolrHookGroup>[] = [
    { header: '#', cell: (solrHookGroup) => solrHookGroup.listingPosition, unimportant: true },
    { header: t('common.key'), cell: (solrHookGroup) => <strong>{solrHookGroup.key}</strong> },
    { header: t('common.displayName'), cell: (solrHookGroup) => translate(solrHookGroup.displayName) },
    {
      header: t('solrmanager.hookGroups.columns.hooks'),
      cell: (solrHookGroup) => hookCountByKey[solrHookGroup.key] ?? 0,
      unimportant: true,
    },
  ]

  return (
    <Table
      columns={columns}
      rows={solrHookGroups}
      onReorder={onReorder}
      rowKey={(solrHookGroup) => solrHookGroup.key}
      detailTitle={(solrHookGroup) => solrHookGroup.key}
      emptyText={t('solrmanager.hookGroups.empty')}
      actions={(solrHookGroup) => (
        <>
          <EditButton label={t('common.edit')} onClick={() => onEdit(solrHookGroup)} />
          <DeleteButton label={t('common.deleteAction')} onClick={() => onDelete(solrHookGroup)} />
        </>
      )}
    />
  )
}
