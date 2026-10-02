import { DeleteButton } from '../../components/DeleteButton.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { entityStatusName } from '../../components/entityStatus.ts'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { PasswordButton } from '../../components/PasswordButton.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import type { AccessRole } from '../accessrole/AccessRole.ts'
import { AccessRoleBadge } from '../accessrole/AccessRoleBadge.tsx'
import { BadgeList } from '../accessrole/BadgeList.tsx'
import type { AccessRoleCollection } from '../accessrolecollection/AccessRoleCollection.ts'
import type { Users } from './Users.ts'

interface AllUsersAdminListProps {
  users: Users[]
  /** alle AccessRoles, um die zugewiesenen als Badges zu zeigen */
  accessRoles: AccessRole[]
  /** die AR-Keys je User-guid, in der Reihenfolge der AccessRoles */
  accessRoleKeysByUsersGuid: Record<string, string[]>
  /** die ARC je User-guid. Fehlt ein User, hat er keine: Eine ARC ist optional. */
  accessRoleCollectionByUsersGuid: Record<string, AccessRoleCollection>
  onSetPassword: (users: Users) => void
  onEdit: (users: Users) => void
  onDelete: (users: Users) => void
}

/**
 * Tabelle aller User für den Admin, ganz rechts die Aktionen Passwort setzen, Bearbeiten und Löschen. Vor- und
 * Nachname stehen im Hoverlay der E-Mail. Daneben die ARC als Badge, danach die direkt zugeordneten AR.
 * Zeigt nur an, lädt nichts: die Daten kommen als Parameter, was die Aktionen tun, bestimmt der Aufrufer.
 */
export function AllUsersAdminList({
  users,
  accessRoles,
  accessRoleKeysByUsersGuid,
  accessRoleCollectionByUsersGuid,
  onSetPassword,
  onEdit,
  onDelete,
}: AllUsersAdminListProps) {
  const columns: TableColumn<Users>[] = [
    {
      header: 'E-Mail',
      cell: (user) => (
        // ohne Namen kein Hoverlay
        <Hoverlay text={[user.vorname, user.nachname].filter(Boolean).join(' ')}>
          <span>{user.email}</span>
        </Hoverlay>
      ),
    },
    { header: 'Status', cell: (user) => entityStatusName(user.status) },
    {
      header: 'ARC',
      cell: (user) => {
        const accessRoleCollection = accessRoleCollectionByUsersGuid[user.guid]
        return accessRoleCollection && <AccessRoleBadge badge={accessRoleCollection} size="full" shape="pill" />
      },
    },
    {
      header: 'AR',
      cell: (user) => <BadgeList keys={accessRoleKeysByUsersGuid[user.guid] ?? []} badges={accessRoles} shape="rect" />,
      unimportant: true,
    },
    { header: 'Username', cell: (user) => user.username, unimportant: true },
    { header: 'Angelegt', cell: (user) => new Date(user.createdAt).toLocaleString('de-DE'), unimportant: true },
    {
      header: 'Service',
      cell: (user) =>
        user.serviceAccount && (
          <>
            <Icon name="check" />
            <span className="visually-hidden">ja</span>
          </>
        ),
      unimportant: true,
    },
  ]

  return (
    <Table
      columns={columns}
      rows={users}
      rowKey={(user) => user.guid}
      detailTitle={(user) => user.email}
      emptyText="keine User"
      actions={(user) => (
        <>
          <PasswordButton label="Passwort setzen" onClick={() => onSetPassword(user)} />
          <EditButton label="bearbeiten" onClick={() => onEdit(user)} />
          <DeleteButton label="löschen" onClick={() => onDelete(user)} />
        </>
      )}
    />
  )
}
