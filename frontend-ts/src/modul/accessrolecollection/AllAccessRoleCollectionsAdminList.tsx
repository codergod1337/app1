import { DeleteButton } from '../../components/DeleteButton.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { translate } from '../../components/multilingual.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { AccessRoleBadge } from '../accessrole/AccessRoleBadge.tsx'
import type { AccessRoleCollection } from './AccessRoleCollection.ts'

interface AllAccessRoleCollectionsAdminListProps {
  accessRoleCollections: AccessRoleCollection[]
  onEdit: (accessRoleCollection: AccessRoleCollection) => void
  onDelete: (accessRoleCollection: AccessRoleCollection) => void
  /** nach Drag and Drop: die ARCs in neuer Reihenfolge */
  onReorder: (accessRoleCollections: AccessRoleCollection[]) => void
}

/**
 * Tabelle aller AccessRoleCollections für den Admin: Badges als Pille, Key und Beschreibung. Welche AR eine ARC
 * verleiht, zeigt die ARC-AR-Matrix. Zeigt nur an, lädt nichts: die Daten kommen als Parameter, was die Aktionen
 * tun, bestimmt der Aufrufer.
 */
export function AllAccessRoleCollectionsAdminList({
  accessRoleCollections,
  onEdit,
  onDelete,
  onReorder,
}: AllAccessRoleCollectionsAdminListProps) {
  const columns: TableColumn<AccessRoleCollection>[] = [
    { header: '#', cell: (arc) => arc.listingPosition, unimportant: true },
    { header: 'Badge', cell: (arc) => <AccessRoleBadge badge={arc} size="full" shape="pill" />, unimportant: true },
    { header: 'Kompakt', cell: (arc) => <AccessRoleBadge badge={arc} size="compact" shape="pill" /> },
    { header: 'Key', cell: (arc) => arc.key },
    { header: 'Beschreibung', cell: (arc) => translate(arc.description), unimportant: true },
  ]

  return (
    <Table
      columns={columns}
      rows={accessRoleCollections}
      rowKey={(arc) => arc.key}
      detailTitle={(arc) => arc.key}
      emptyText="keine AccessRoleCollections"
      onReorder={onReorder}
      actions={(arc) => (
        <>
          <EditButton label="bearbeiten" onClick={() => onEdit(arc)} />
          <DeleteButton label="löschen" onClick={() => onDelete(arc)} />
        </>
      )}
    />
  )
}
