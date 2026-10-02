import { DeleteButton } from '../../components/DeleteButton.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { Icon } from '../../components/Icon.tsx'
import { translate } from '../../components/multilingual.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import type { AccessRole } from './AccessRole.ts'
import { AccessRoleBadge } from './AccessRoleBadge.tsx'

const COLUMNS: TableColumn<AccessRole>[] = [
  { header: '#', cell: (accessRole) => accessRole.listingPosition, unimportant: true },
  { header: 'Badge', cell: (accessRole) => <AccessRoleBadge badge={accessRole} size="full" />, unimportant: true },
  { header: 'Kompakt', cell: (accessRole) => <AccessRoleBadge badge={accessRole} size="compact" /> },
  { header: 'Key', cell: (accessRole) => accessRole.key },
  { header: 'Beschreibung', cell: (accessRole) => translate(accessRole.description), unimportant: true },
  {
    header: 'Sys',
    cell: (accessRole) =>
      accessRole.system && (
        <>
          <Icon name="check" />
          <span className="visually-hidden">ja</span>
        </>
      ),
  },
]

interface AllAccessRolesAdminListProps {
  accessRoles: AccessRole[]
  onEdit: (accessRole: AccessRole) => void
  onDelete: (accessRole: AccessRole) => void
  /** nach Drag and Drop: die Rollen in neuer Reihenfolge */
  onReorder: (accessRoles: AccessRole[]) => void
}

/**
 * Tabelle aller AccessRoles für den Admin, ganz rechts die Aktionen Bearbeiten und Löschen.
 * system-Rollen lassen sich nicht löschen, ihr Mülleimer ist gesperrt.
 * Zeigt nur an, lädt nichts: die Rollen kommen als Parameter, was die Aktionen tun, bestimmt der Aufrufer.
 */
export function AllAccessRolesAdminList({ accessRoles, onEdit, onDelete, onReorder }: AllAccessRolesAdminListProps) {
  return (
    <Table
      columns={COLUMNS}
      rows={accessRoles}
      onReorder={onReorder}
      rowKey={(accessRole) => accessRole.key}
      detailTitle={(accessRole) => accessRole.key}
      emptyText="keine AccessRoles"
      actions={(accessRole) => (
        <>
          <EditButton label="bearbeiten" onClick={() => onEdit(accessRole)} />
          <DeleteButton
            label={accessRole.system ? 'system-Rolle, kann nicht gelöscht werden' : 'löschen'}
            disabled={accessRole.system}
            onClick={() => onDelete(accessRole)}
          />
        </>
      )}
    />
  )
}
