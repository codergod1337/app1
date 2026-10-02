import { useCallback, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { useLoad } from '../../api/useLoad.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { useOrigin } from '../../components/Origin.ts'
import {
  changeAccessRoleUsersAssignments,
  getAccessRoleKeysByUsersGuid,
} from '../accessrole/AccessRoleUsersAssignment.ts'
import { BadgeToggleList } from '../accessrole/BadgeToggleList.tsx'
import { ChangeUsersGuidPopup } from '../users/ChangeUsersGuidPopup.tsx'
import { getUsersByGuid, updateUsers, type Users, type UsersData } from '../users/Users.ts'
import { UsersForm } from '../users/UsersForm.tsx'

const FORM_ID = 'edit-users-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/admin/users'

/**
 * Admin bearbeitet einen User, die guid steht im Pfad. guid und createdAt stehen nur zur Anzeige da, die guid
 * ändert ein eigenes Popup. Darunter die AccessRoles des Users zum Anklicken, gespeichert mit demselben Knopf.
 * Abbrechen und Speichern springen zum origin.
 */
export function EditUsersTab() {
  const guid = useParams().guid ?? ''
  const navigate = useNavigate()
  const location = useLocation()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const loadUsers = useCallback(() => getUsersByGuid(guid), [guid])
  const { data: users, errorMessage: loadErrorMessage } = useLoad(loadUsers)
  const { accessRoles, errorMessage: accessRolesErrorMessage } = useMasterData()
  const loadAccessRoleKeys = useCallback(() => getAccessRoleKeysByUsersGuid(guid), [guid])
  const { data: loadedAccessRoleKeys, errorMessage: accessRoleKeysErrorMessage } = useLoad(loadAccessRoleKeys)
  // null, solange der Admin keine Badge angeklickt hat: dann gilt der geladene Stand
  const [selectedAccessRoleKeys, setSelectedAccessRoleKeys] = useState<string[] | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  const [changingGuid, setChangingGuid] = useState(false)

  const shownAccessRoleKeys = selectedAccessRoleKeys ?? loadedAccessRoleKeys

  /** Erst den User, dann die AR-Zuordnungen, aber nur, wenn an den Badges etwas angeklickt wurde */
  function saveUsers(changedUsersData: UsersData) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateUsers(guid, changedUsersData)
      .then(() =>
        selectedAccessRoleKeys === null ? undefined : changeAccessRoleUsersAssignments(guid, selectedAccessRoleKeys),
      )
      .then(() => navigate(originPath))
      .catch((error: unknown) => {
        setSaveErrorMessage(error instanceof RequestError ? error.message : 'Unbekannter Fehler')
        setSaving(false)
      })
  }

  /** Nach dem guid-Wechsel dieselbe Maske unter der neuen guid, der origin bleibt */
  function showChangedUsers(changedUsers: Users) {
    navigate(`/admin/users/edit/${changedUsers.guid}`, { replace: true, state: location.state })
  }

  const errorMessage = loadErrorMessage ?? accessRolesErrorMessage ?? accessRoleKeysErrorMessage ?? saveErrorMessage
  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {users === null && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
      )}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={() => navigate(originPath)}>
          Abbrechen
        </button>
        <button type="submit" form={FORM_ID} className="button-save" disabled={saving || users === null}>
          Speichern
        </button>
      </span>
    </>
  )

  return (
    <ContentBox title={users ? `User bearbeiten: ${users.email}` : 'User bearbeiten'} footer={footer}>
      {/* Erst mit den geladenen Daten anzeigen, damit das Formular mit ihnen startet */}
      {users !== null && (
        <>
          <div className="row g-3 mb-3">
            <div className="col-md-6">
              <label className="form-label" htmlFor={`${FORM_ID}-guid`}>
                guid
              </label>
              <div className="d-flex align-items-center gap-2">
                <input id={`${FORM_ID}-guid`} className="form-control font-monospace" readOnly value={users.guid} />
                <EditButton label="guid ändern" onClick={() => setChangingGuid(true)} />
              </div>
            </div>
            <div className="col-md-6">
              <label className="form-label" htmlFor={`${FORM_ID}-created-at`}>
                Angelegt
              </label>
              <input
                id={`${FORM_ID}-created-at`}
                className="form-control"
                readOnly
                value={new Date(users.createdAt).toLocaleString('de-DE')}
              />
            </div>
          </div>
          <UsersForm
            formId={FORM_ID}
            initialUsersData={{
              email: users.email,
              username: users.username ?? undefined,
              vorname: users.vorname ?? undefined,
              nachname: users.nachname ?? undefined,
              serviceAccount: users.serviceAccount,
              status: users.status,
            }}
            disabled={saving}
            onSubmit={saveUsers}
          />
          <div className="mt-3">
            <label className="form-label">AccessRoles</label>
            {accessRoles !== null && shownAccessRoleKeys !== null ? (
              <BadgeToggleList
                badges={accessRoles}
                selectedKeys={shownAccessRoleKeys}
                shape="rect"
                disabled={saving}
                onChange={setSelectedAccessRoleKeys}
              />
            ) : (
              <span className="spinner-border spinner-border-sm d-block" role="status" aria-label="lädt" />
            )}
          </div>
          <ChangeUsersGuidPopup
            open={changingGuid}
            users={users}
            onChanged={showChangedUsers}
            onClose={() => setChangingGuid(false)}
          />
        </>
      )}
    </ContentBox>
  )
}
