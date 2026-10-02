import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useLoad } from '../../api/useLoad.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { getAllAccessRoleUsersAssignments } from '../accessrole/AccessRoleUsersAssignment.ts'
import type { AccessRoleCollection } from '../accessrolecollection/AccessRoleCollection.ts'
import { getAllAccessRoleCollectionUsersAssignments } from '../accessrolecollection/AccessRoleCollectionUsersAssignment.ts'
import { AllUsersAdminList } from '../users/AllUsersAdminList.tsx'
import { SetUsersPasswordPopup } from '../users/SetUsersPasswordPopup.tsx'
import { deleteUsers, type Users } from '../users/Users.ts'
import { useAllUsers } from '../users/useUsers.ts'

/** Beim Verlassen dieses Tabs mitgeben, damit der header „zurück zu User“ anbietet */
const ORIGIN_STATE: OriginState = { origin: { path: '/admin/users', label: 'User' } }

/** Tab „User“ der Administration: alles unter dem subheader. */
export function UsersTab() {
  const navigate = useNavigate()
  const { users, errorMessage, reload } = useAllUsers()
  const { accessRoles, accessRoleCollections, errorMessage: accessRolesErrorMessage } = useMasterData()
  const { data: assignments, errorMessage: assignmentsErrorMessage } = useLoad(getAllAccessRoleUsersAssignments)
  const { data: arcAssignments, errorMessage: arcAssignmentsErrorMessage } = useLoad(
    getAllAccessRoleCollectionUsersAssignments,
  )
  // Popups: null, solange keins offen ist
  const [usersToSetPassword, setUsersToSetPassword] = useState<Users | null>(null)
  const [usersToDelete, setUsersToDelete] = useState<Users | null>(null)

  // Die AR-Keys je User, in der Reihenfolge der AccessRoles
  const accessRoleKeysByUsersGuid: Record<string, string[]> = {}
  for (const accessRole of accessRoles ?? []) {
    for (const assignment of assignments ?? []) {
      if (assignment.accessRoleKey === accessRole.key) {
        const usersAccessRoleKeys = accessRoleKeysByUsersGuid[assignment.usersGuid] ?? []
        usersAccessRoleKeys.push(accessRole.key)
        accessRoleKeysByUsersGuid[assignment.usersGuid] = usersAccessRoleKeys
      }
    }
  }

  // Die ARC je User, aufgelöst über die Stammdaten
  const accessRoleCollectionByUsersGuid: Record<string, AccessRoleCollection> = {}
  for (const arcAssignment of arcAssignments ?? []) {
    const accessRoleCollection = accessRoleCollections?.find(
      (candidate) => candidate.key === arcAssignment.accessRoleCollectionKey,
    )
    if (accessRoleCollection) {
      accessRoleCollectionByUsersGuid[arcAssignment.usersGuid] = accessRoleCollection
    }
  }

  const loaded = users !== null && accessRoles !== null && assignments !== null && arcAssignments !== null
  const shownErrorMessage =
    errorMessage ?? accessRolesErrorMessage ?? assignmentsErrorMessage ?? arcAssignmentsErrorMessage
  let footer
  if (shownErrorMessage !== null) {
    footer = <span className="text-danger">{shownErrorMessage}</span>
  } else if (!loaded) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
  } else {
    footer = <span>{users.length} User</span>
  }

  const newUsersButton = (
    <NewEntityButton entity="Users" onClick={() => navigate('/admin/users/new', { state: ORIGIN_STATE })} />
  )

  return (
    <>
      <ContentBox title="User" actions={newUsersButton} footer={footer}>
        {loaded && (
          <AllUsersAdminList
            users={users}
            accessRoles={accessRoles}
            accessRoleKeysByUsersGuid={accessRoleKeysByUsersGuid}
            accessRoleCollectionByUsersGuid={accessRoleCollectionByUsersGuid}
            onSetPassword={setUsersToSetPassword}
            onEdit={(user) => navigate(`/admin/users/edit/${user.guid}`, { state: ORIGIN_STATE })}
            onDelete={setUsersToDelete}
          />
        )}
      </ContentBox>
      <SetUsersPasswordPopup users={usersToSetPassword} onClose={() => setUsersToSetPassword(null)} />
      <DeletePopup
        open={usersToDelete !== null}
        title="User löschen"
        onConfirm={() => deleteUsers(usersToDelete?.guid ?? '')}
        onDeleted={reload}
        onClose={() => setUsersToDelete(null)}
      >
        Soll <strong>{usersToDelete?.email}</strong> wirklich gelöscht werden? Einstellungen, Zugangsdaten und
        AR-Zuordnungen des Users werden mitgelöscht.
      </DeletePopup>
    </>
  )
}
