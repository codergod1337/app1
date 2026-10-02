import { useMemo, useState } from 'react'
import { RequestError } from '../../api/client.ts'
import { useLoad } from '../../api/useLoad.ts'
import { entityStatusName } from '../../components/entityStatus.ts'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { Matrix } from '../../components/Matrix.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { AccessRoleBadge } from '../accessrole/AccessRoleBadge.tsx'
import type { AccessRoleCollection } from '../accessrolecollection/AccessRoleCollection.ts'
import {
  changeAccessRoleCollectionUsersAssignment,
  getAllAccessRoleCollectionUsersAssignments,
} from '../accessrolecollection/AccessRoleCollectionUsersAssignment.ts'
import { getAllUsers, type Users } from '../users/Users.ts'

/** Vor- und Nachname, sonst der username, sonst die E-Mail */
function usersName(users: Users): string {
  return [users.vorname, users.nachname].filter(Boolean).join(' ') || users.username || users.email
}

/**
 * Tab „User-ARC“ der Administration: welcher User auf welcher Position (ARC) sitzt. Links alle User, die ältesten
 * zuerst, oben die ARCs nach ihrer Position. Jeder User hat höchstens eine ARC, eine Zeile wirkt deshalb wie eine
 * Radio-Gruppe: Klick auf eine leere Zelle setzt diese ARC und ersetzt die bisherige, Klick auf das × entzieht sie.
 */
export function UsersAccessRoleCollectionMatrixTab() {
  const { data: users, errorMessage: usersErrorMessage } = useLoad(getAllUsers)
  const { data: assignments, errorMessage: assignmentsErrorMessage } = useLoad(getAllAccessRoleCollectionUsersAssignments)
  const { accessRoleCollections, errorMessage: masterDataErrorMessage } = useMasterData()
  // Was seit dem Laden geklickt wurde, je User-guid der Stand aus dem Backend (null: keine ARC)
  const [changedKeyByUsersGuid, setChangedKeyByUsersGuid] = useState<Record<string, string | null>>({})
  const [savingUsersGuid, setSavingUsersGuid] = useState<string | null>(null)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)

  const keyByUsersGuid = useMemo(() => {
    const loadedKeyByUsersGuid: Record<string, string | null> = {}
    for (const assignment of assignments ?? []) {
      loadedKeyByUsersGuid[assignment.usersGuid] = assignment.accessRoleCollectionKey
    }
    return { ...loadedKeyByUsersGuid, ...changedKeyByUsersGuid }
  }, [assignments, changedKeyByUsersGuid])

  // die ältesten zuerst, bei gleichem Zeitpunkt nach E-Mail, damit die Reihenfolge stabil bleibt
  const sortedUsers = useMemo(
    () =>
      [...(users ?? [])].sort(
        (a, b) =>
          new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime() || a.email.localeCompare(b.email),
      ),
    [users],
  )

  function toggleAccessRoleCollection(user: Users, accessRoleCollection: AccessRoleCollection) {
    if (savingUsersGuid !== null) {
      return
    }
    const currentKey = keyByUsersGuid[user.guid] ?? null
    const newKey = currentKey === accessRoleCollection.key ? null : accessRoleCollection.key
    setSavingUsersGuid(user.guid)
    setSaveErrorMessage(null)
    changeAccessRoleCollectionUsersAssignment(user.guid, newKey)
      .then((result) =>
        setChangedKeyByUsersGuid((current) => ({ ...current, [user.guid]: result.data?.accessRoleCollectionKey ?? null })),
      )
      .catch((error: unknown) => setSaveErrorMessage(error instanceof RequestError ? error.message : 'Unbekannter Fehler'))
      .finally(() => setSavingUsersGuid(null))
  }

  const errorMessage = usersErrorMessage ?? assignmentsErrorMessage ?? masterDataErrorMessage ?? saveErrorMessage
  const loading = users === null || assignments === null || accessRoleCollections === null

  return (
    <>
      {/* Nur bei Fehler oder während des Ladens, sonst steht die Matrix allein */}
      {(errorMessage !== null || loading) && (
        <div className="matrix-message">
          {errorMessage !== null ? (
            <span className="text-danger">{errorMessage}</span>
          ) : (
            <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
          )}
        </div>
      )}
      {!loading && (
        <Matrix
          rows={sortedUsers}
          columns={accessRoleCollections}
          rowKey={(user) => user.guid}
          columnKey={(accessRoleCollection) => accessRoleCollection.key}
          rowsLabel="User"
          columnsLabel="AccessRoleCollection"
          rowHeader={(user) => (
            <span className="matrix-row-head-content">
              {/* Normal hat jeder User eine Position: Wer keine hat, fällt hier auf */}
              {!keyByUsersGuid[user.guid] && (
                <Hoverlay text="keine Position">
                  <span className="text-warning">
                    <Icon name="warning" />
                  </span>
                </Hoverlay>
              )}
              {user.serviceAccount && (
                <Hoverlay text="Service-Account: kein Mensch, sondern ein Backend-Dienst">
                  <span className="matrix-chip">SYS</span>
                </Hoverlay>
              )}
              <Hoverlay text={user.email}>
                <span>{usersName(user)}</span>
              </Hoverlay>
              {user.status !== 'ACTIVE' && <span className="matrix-chip">{entityStatusName(user.status)}</span>}
            </span>
          )}
          columnHeader={(accessRoleCollection) => <AccessRoleBadge badge={accessRoleCollection} size="full" shape="pill" />}
          isMarked={(user, accessRoleCollection) => keyByUsersGuid[user.guid] === accessRoleCollection.key}
          cellLabel={(user, accessRoleCollection) => `${accessRoleCollection.key} für ${usersName(user)} umschalten`}
          onCellClick={toggleAccessRoleCollection}
          disabled={savingUsersGuid !== null}
        />
      )}
    </>
  )
}
