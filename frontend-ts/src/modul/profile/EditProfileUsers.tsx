import { useCallback, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { useLoad } from '../../api/useLoad.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useOrigin } from '../../components/Origin.ts'
import { useSession } from '../login/sessionContext.ts'
import { getUsersByGuid, updateUsersNames, type Users, type UsersNamesData } from '../users/Users.ts'

const FORM_ID = 'edit-profile-users-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/profile'

interface ProfileUsersFormProps {
  users: Users
  disabled: boolean
  onSubmit: (usersNamesData: UsersNamesData) => void
}

/** Die Felder, startet mit den geladenen Werten */
function ProfileUsersForm({ users, disabled, onSubmit }: ProfileUsersFormProps) {
  const [username, setUsername] = useState(users.username ?? '')
  const [vorname, setVorname] = useState(users.vorname ?? '')
  const [nachname, setNachname] = useState(users.nachname ?? '')

  function submitUsersNames(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    // Leere Felder gar nicht erst mitschicken, das Backend leert sie dann
    onSubmit({ username: username || undefined, vorname: vorname || undefined, nachname: nachname || undefined })
  }

  return (
    <form id={FORM_ID} onSubmit={submitUsersNames}>
      <fieldset className="row g-3" disabled={disabled}>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${FORM_ID}-email`}>
            E-Mail
          </label>
          <input id={`${FORM_ID}-email`} className="form-control" readOnly value={users.email} />
          <div className="form-text">Mit der E-Mail meldest du dich an. Ändern kann sie nur der Admin.</div>
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${FORM_ID}-username`}>
            Username
          </label>
          <input
            id={`${FORM_ID}-username`}
            className="form-control"
            maxLength={100}
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${FORM_ID}-vorname`}>
            Vorname
          </label>
          <input
            id={`${FORM_ID}-vorname`}
            className="form-control"
            maxLength={100}
            value={vorname}
            onChange={(event) => setVorname(event.target.value)}
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${FORM_ID}-nachname`}>
            Nachname
          </label>
          <input
            id={`${FORM_ID}-nachname`}
            className="form-control"
            maxLength={100}
            value={nachname}
            onChange={(event) => setNachname(event.target.value)}
          />
        </div>
      </fieldset>
    </form>
  )
}

/** Der User ändert username, Vor- und Nachname. Abbrechen und Speichern springen zum origin. */
export function EditProfileUsers() {
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const usersGuid = useSession().sessionInfo?.usersGuid ?? ''
  const loadUsers = useCallback(() => getUsersByGuid(usersGuid), [usersGuid])
  const { data: users, errorMessage: loadErrorMessage } = useLoad(loadUsers)
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)

  function saveUsersNames(changedUsersNamesData: UsersNamesData) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateUsersNames(usersGuid, changedUsersNamesData)
      .then(() => navigate(originPath))
      .catch((error: unknown) => {
        setSaveErrorMessage(error instanceof RequestError ? error.message : 'Unbekannter Fehler')
        setSaving(false)
      })
  }

  const errorMessage = loadErrorMessage ?? saveErrorMessage
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
    <ContentBox title="Name bearbeiten" footer={footer}>
      {/* Erst mit den geladenen Daten anzeigen, damit das Formular mit ihnen startet */}
      {users !== null && <ProfileUsersForm users={users} disabled={saving} onSubmit={saveUsersNames} />}
    </ContentBox>
  )
}
