import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { Popup } from '../../components/Popup.tsx'
import { useSession } from '../login/sessionContext.ts'
import { changeUsersCredentialsPassword } from './UsersCredentials.ts'

const FORM_ID = 'change-users-password-form'

interface ChangeUsersPasswordPopupProps {
  open: boolean
  /** guid des angemeldeten Users */
  usersGuid: string
  onClose: () => void
}

/**
 * Der User ändert sein eigenes Passwort: das alte, das neue und die Wiederholung, gehasht wird vor dem Senden
 * (UsersCredentials.ts). Danach beendet das Backend alle Sitzungen, auch diese: Schließen meldet ab und führt zum Login.
 */
export function ChangeUsersPasswordPopup({ open, usersGuid, onClose }: ChangeUsersPasswordPopupProps) {
  const navigate = useNavigate()
  const { logout } = useSession()
  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [repeatedPassword, setRepeatedPassword] = useState('')
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const passwordsMatch = newPassword !== '' && newPassword === repeatedPassword

  function close() {
    setOldPassword('')
    setNewPassword('')
    setRepeatedPassword('')
    setSaved(false)
    setErrorMessage(null)
    onClose()
    // Die Sitzung gilt nach dem Wechsel nicht mehr
    if (saved) {
      logout().finally(() => navigate('/login'))
    }
  }

  function submitPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (oldPassword === '' || !passwordsMatch) {
      return
    }
    setSaving(true)
    setErrorMessage(null)
    changeUsersCredentialsPassword(usersGuid, oldPassword, newPassword)
      .then(() => setSaved(true))
      // Error statt nur RequestError: ohne https gibt es crypto.subtle nicht, dann scheitert schon das Hashen
      .catch((error: unknown) => setErrorMessage(error instanceof Error ? error.message : 'Unbekannter Fehler'))
      .finally(() => setSaving(false))
  }

  const footer = saved ? (
    <button type="button" className="button-save ms-auto" onClick={close}>
      Neu anmelden
    </button>
  ) : (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={close}>
          Abbrechen
        </button>
        <button
          type="submit"
          form={FORM_ID}
          className="button-save"
          disabled={saving || oldPassword === '' || !passwordsMatch}
        >
          Passwort ändern
        </button>
      </span>
    </>
  )

  return (
    <Popup open={open} title="Passwort ändern" footer={footer} onClose={close}>
      {saved ? (
        <p className="mb-0">Passwort geändert. Alle Sitzungen sind beendet, auch diese. Melde dich mit dem neuen Passwort wieder an.</p>
      ) : (
        <form id={FORM_ID} onSubmit={submitPassword}>
          <fieldset className="row g-3" disabled={saving}>
            <div className="col-12">
              <label className="form-label" htmlFor={`${FORM_ID}-old`}>
                Altes Passwort
              </label>
              <input
                id={`${FORM_ID}-old`}
                className="form-control"
                type="password"
                autoComplete="current-password"
                required
                value={oldPassword}
                onChange={(event) => setOldPassword(event.target.value)}
              />
            </div>
            <div className="col-md-6">
              <label className="form-label" htmlFor={`${FORM_ID}-new`}>
                Neues Passwort
              </label>
              <input
                id={`${FORM_ID}-new`}
                className="form-control"
                type="password"
                autoComplete="new-password"
                required
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
              />
            </div>
            <div className="col-md-6">
              <label className="form-label" htmlFor={`${FORM_ID}-repeated`}>
                Neues Passwort wiederholen
              </label>
              <input
                id={`${FORM_ID}-repeated`}
                className={repeatedPassword !== '' && !passwordsMatch ? 'form-control is-invalid' : 'form-control'}
                type="password"
                autoComplete="new-password"
                required
                value={repeatedPassword}
                onChange={(event) => setRepeatedPassword(event.target.value)}
              />
              <div className="invalid-feedback">Die Wiederholung stimmt nicht überein.</div>
            </div>
            <div className="col-12 small">Danach endet jede Sitzung, auch diese. Du meldest dich mit dem neuen Passwort wieder an.</div>
          </fieldset>
        </form>
      )}
    </Popup>
  )
}
