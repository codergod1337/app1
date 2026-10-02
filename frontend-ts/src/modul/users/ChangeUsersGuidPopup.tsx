import { useState, type FormEvent } from 'react'
import { RequestError } from '../../api/client.ts'
import { GuidInput } from '../../components/GuidInput.tsx'
import { Popup } from '../../components/Popup.tsx'
import { changeUsersGuid, type Users } from './Users.ts'

const FORM_ID = 'change-users-guid-form'

interface ChangeUsersGuidPopupProps {
  open: boolean
  /** der User, dessen guid geändert wird */
  users: Users
  /** nach dem Ändern: der User unter der neuen guid */
  onChanged: (changedUsers: Users) => void
  onClose: () => void
}

/** Admin ändert die guid eines Users. Zugangsdaten und Einstellungen ziehen im Backend mit um. */
export function ChangeUsersGuidPopup({ open, users, onChanged, onClose }: ChangeUsersGuidPopupProps) {
  const [guidNew, setGuidNew] = useState('')
  const [saving, setSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  function close() {
    setGuidNew('')
    setErrorMessage(null)
    onClose()
  }

  function submitGuid(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setErrorMessage(null)
    changeUsersGuid(users.guid, guidNew)
      .then((result) => {
        close()
        onChanged(result.data)
      })
      .catch((error: unknown) => setErrorMessage(error instanceof RequestError ? error.message : 'Unbekannter Fehler'))
      .finally(() => setSaving(false))
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={close}>
          Abbrechen
        </button>
        <button type="submit" form={FORM_ID} className="button-save" disabled={saving}>
          guid ändern
        </button>
      </span>
    </>
  )

  return (
    <Popup open={open} title={`guid ändern: ${users.email}`} footer={footer} onClose={close}>
      <form id={FORM_ID} onSubmit={submitGuid}>
        <fieldset className="row g-3" disabled={saving}>
          <div className="col-md-6">
            <label className="form-label" htmlFor={`${FORM_ID}-current`}>
              Aktuelle guid
            </label>
            <input id={`${FORM_ID}-current`} className="form-control font-monospace" readOnly value={users.guid} />
          </div>
          <div className="col-md-6">
            <label className="form-label" htmlFor={`${FORM_ID}-new`}>
              Neue guid
            </label>
            <GuidInput id={`${FORM_ID}-new`} required value={guidNew} onChange={setGuidNew} />
          </div>
        </fieldset>
      </form>
    </Popup>
  )
}
