import { useTranslation } from 'react-i18next'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { translate } from '../../components/multilingual.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import type { EntityState } from './EntityState.ts'

interface AllEntityStatesAdminListProps {
  entityStates: EntityState[]
  onEdit: (entityState: EntityState) => void
  onDelete: (entityState: EntityState) => void
  /** nach Drag and Drop: die Status in neuer Reihenfolge */
  onReorder: (entityStates: EntityState[]) => void
}

/**
 * Tabelle aller Status für den Admin, ganz rechts die Aktionen Bearbeiten und Löschen. Zeigt nur an, lädt nichts:
 * die Daten kommen als Parameter, was die Aktionen tun, bestimmt der Aufrufer.
 */
export function AllEntityStatesAdminList({ entityStates, onEdit, onDelete, onReorder }: AllEntityStatesAdminListProps) {
  const { t } = useTranslation()

  const columns: TableColumn<EntityState>[] = [
    { header: '#', cell: (entityState) => entityState.listingPosition, unimportant: true },
    { header: t('common.key'), cell: (entityState) => <strong>{entityState.key}</strong> },
    { header: t('common.displayName'), cell: (entityState) => translate(entityState.displayName) },
    {
      header: t('entitystate.scope'),
      cell: (entityState) => entityState.scope ?? <span className="text-secondary">{t('entitystate.everywhere')}</span>,
    },
  ]

  return (
    <Table
      columns={columns}
      rows={entityStates}
      onReorder={onReorder}
      rowKey={(entityState) => entityState.key}
      detailTitle={(entityState) => entityState.key}
      emptyText={t('entitystate.empty')}
      actions={(entityState) => (
        <>
          <EditButton label={t('common.edit')} onClick={() => onEdit(entityState)} />
          <DeleteButton label={t('common.deleteAction')} onClick={() => onDelete(entityState)} />
        </>
      )}
    />
  )
}
