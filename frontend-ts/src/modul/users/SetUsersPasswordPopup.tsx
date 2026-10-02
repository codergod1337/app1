import { useState, type FormEvent } from 'react'
import { Popup } from '../../components/Popup.tsx'
import type { Users } from './Users.ts'
import { setUsersCredentialsPassword } from './UsersCredentials.ts'

const FORM_ID = 'set-users-password-form'

interface SetUsersPasswordPopupProps {
  /** der User, dessen Passwort gesetzt wird. null: Popup zu */
  users: Users | null
  onClose: () => void
}

/**
 * Admin setzt das Passwort eines Users oder setzt es zurück, das alte muss er nicht kennen.
 * Neues Passwort und Wiederholung, gehasht wird vor dem Senden (UsersCredentials.ts).
 */
export function SetUsersPasswordPopup({ users, onClose }: SetUsersPasswordPopupProps) {
  const [newPassword, setNewPassword] = useState('')
  const [repeatedPassword, setRepeatedPassword] = useState('')
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const passwordsMatch = newPassword !== '' && newPassword === repeatedPassword

  function close() {
    setNewPassword('')
    setRepeatedPassword('')
    setSaved(false)
    setErrorMessage(null)
    onClose()
  }

  function submitPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (users === null || !passwordsMatch) {
      return
    }
    setSaving(true)
    setErrorMessage(null)
    setUsersCredentialsPassword(users.guid, newPassword)
      .then(() => setSaved(true))
      // Error statt nur RequestError: ohne https gibt es crypto.subtle nicht, dann scheitert schon das Hashen
      .catch((error: unknown) => setErrorMessage(error instanceof Error ? error.message : 'Unbekannter Fehler'))
      .finally(() => setSaving(false))
  }

  const footer = saved ? (
    <span>Passwort gesetzt.</span>
  ) : (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={close}>
          Abbrechen
        </button>
        <button type="submit" form={FORM_ID} className="button-save" disabled={saving || !passwordsMatch}>
          Passwort setzen
        </button>
      </span>
    </>
  )

  return (
    <Popup open={users !== null} title={`Passwort setzen: ${users?.email ?? ''}`} footer={footer} onClose={close}>
      {saved ? (
        <p className="mb-0">
          Das neue Passwort von <strong>{users?.email}</strong> gilt ab sofort.
        </p>
      ) : (
        <form id={FORM_ID} onSubmit={submitPassword}>
          <fieldset className="row g-3" disabled={saving}>
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
          </fieldset>
        </form>
      )}
    </Popup>
  )
}
