import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { Icon } from '../../components/Icon.tsx'
import { Popup } from '../../components/Popup.tsx'
import { useSession } from '../login/sessionContext.ts'
import { softDeleteUsers } from './Users.ts'

const FORM_ID = 'soft-delete-users-form'

interface SoftDeleteUsersPopupProps {
  open: boolean
  /** guid des angemeldeten Users */
  usersGuid: string
  onClose: () => void
}

/**
 * Die Sicherheitsabfrage, bevor der User sich selbst löscht. Gelöscht wird nur weich: Status DELETED, anmelden geht
 * danach nicht mehr. Zur Bestätigung das eigene Passwort. Danach lokal abmelden und zur Startseite.
 */
export function SoftDeleteUsersPopup({ open, usersGuid, onClose }: SoftDeleteUsersPopupProps) {
  const navigate = useNavigate()
  const { logout } = useSession()
  const [password, setPassword] = useState('')
  const [deleting, setDeleting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  function close() {
    setPassword('')
    setErrorMessage(null)
    onClose()
  }

  function confirmSoftDelete() {
    if (password === '') {
      return
    }
    setDeleting(true)
    setErrorMessage(null)
    softDeleteUsers(usersGuid, password)
      .then(() => {
        close()
        logout().finally(() => navigate('/'))
      })
      // Error statt nur RequestError: ohne https gibt es crypto.subtle nicht, dann scheitert schon das Hashen
      .catch((error: unknown) => setErrorMessage(error instanceof Error ? error.message : 'Unbekannter Fehler'))
      .finally(() => setDeleting(false))
  }

  function submitSoftDelete(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    confirmSoftDelete()
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={deleting} onClick={close}>
          Abbrechen
        </button>
        <DeleteButton label="Konto löschen" disabled={deleting || password === ''} onClick={confirmSoftDelete}>
          Konto löschen
        </DeleteButton>
      </span>
    </>
  )

  return (
    <Popup open={open} title="Konto löschen" footer={footer} onClose={close}>
      <div className="danger-callout">
        <Icon name="warning" />
        <div>
          <strong>Dein Konto wird deaktiviert.</strong>
          <ul>
            <li>Du kannst dich nicht mehr anmelden.</li>
            <li>Alle Sitzungen enden sofort, auch diese.</li>
            <li>Ein Admin kann das Konto wiederherstellen oder endgültig löschen.</li>
          </ul>
        </div>
      </div>
      <form id={FORM_ID} onSubmit={submitSoftDelete}>
        <label className="form-label" htmlFor={`${FORM_ID}-password`}>
          Gib zur Bestätigung dein Passwort ein
        </label>
        <input
          id={`${FORM_ID}-password`}
          className="form-control"
          type="password"
          autoComplete="current-password"
          required
          disabled={deleting}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
      </form>
    </Popup>
  )
}
