import { useTranslation } from 'react-i18next'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { translate } from '../../components/multilingual.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import type { SolrHook } from './SolrHook.ts'
import { SolrHookChip } from './SolrHookChip.tsx'
import type { SolrHookGroup } from './SolrHookGroup.ts'

interface AllSolrHooksAdminListProps {
  solrHooks: SolrHook[]
  /** die Gruppen aus den Stammdaten, für die Spalte Gruppe */
  solrHookGroups: SolrHookGroup[]
  onEdit: (solrHook: SolrHook) => void
  onDelete: (solrHook: SolrHook) => void
  /** nach Drag and Drop: die Hooks in neuer Reihenfolge */
  onReorder: (solrHooks: SolrHook[]) => void
}

/**
 * Tabelle aller Hooks für den Admin, ganz rechts die Aktionen Bearbeiten und Löschen. Zeigt nur an, lädt nichts: die
 * Daten kommen als Parameter, was die Aktionen tun, bestimmt der Aufrufer.
 */
export function AllSolrHooksAdminList({
  solrHooks,
  solrHookGroups,
  onEdit,
  onDelete,
  onReorder,
}: AllSolrHooksAdminListProps) {
  const { t } = useTranslation()

  function groupName(solrHook: SolrHook): string {
    const solrHookGroup = solrHookGroups.find((candidate) => candidate.key === solrHook.hookGroupKey)
    return solrHookGroup ? translate(solrHookGroup.displayName) || solrHookGroup.key : (solrHook.hookGroupKey ?? '')
  }

  const columns: TableColumn<SolrHook>[] = [
    { header: '#', cell: (solrHook) => solrHook.listingPosition, unimportant: true },
    { header: t('solrmanager.hooks.columns.chip'), cell: (solrHook) => <SolrHookChip solrHook={solrHook} /> },
    { header: t('solrmanager.hooks.columns.fieldName'), cell: (solrHook) => solrHook.key },
    { header: t('solrmanager.hooks.columns.group'), cell: groupName, unimportant: true },
    {
      header: t('solrmanager.hooks.columns.description'),
      cell: (solrHook) => translate(solrHook.description),
      unimportant: true,
    },
  ]

  return (
    <Table
      columns={columns}
      rows={solrHooks}
      onReorder={onReorder}
      rowKey={(solrHook) => solrHook.key}
      detailTitle={(solrHook) => solrHook.key}
      emptyText={t('solrmanager.hooks.empty')}
      actions={(solrHook) => (
        <>
          <EditButton label={t('common.edit')} onClick={() => onEdit(solrHook)} />
          <DeleteButton label={t('common.deleteAction')} onClick={() => onDelete(solrHook)} />
        </>
      )}
    />
  )
}
