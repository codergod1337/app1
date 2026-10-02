import { useState, type FormEvent } from 'react'
import { ENTITY_STATUSES, entityStatusName } from '../../components/entityStatus.ts'
import type { UsersData } from './Users.ts'

interface UsersFormProps {
  /** id des form-Elements, damit ein Knopf außerhalb (z. B. im contentbox-footer) es abschicken kann */
  formId: string
  /** Startwerte beim Bearbeiten. Fehlen sie, ist das Formular leer (neuer User). */
  initialUsersData?: UsersData
  /** sperrt alle Felder, z. B. während gespeichert wird */
  disabled: boolean
  onSubmit: (usersData: UsersData) => void
}

/**
 * Nur die Eingabefelder eines Users für den Admin, zum Anlegen und zum Bearbeiten. guid und createdAt stehen nicht
 * darin, die vergibt das Backend. Speichert nichts selbst: die Daten gehen an onSubmit.
 */
export function UsersForm({ formId, initialUsersData, disabled, onSubmit }: UsersFormProps) {
  const [email, setEmail] = useState(initialUsersData?.email ?? '')
  const [username, setUsername] = useState(initialUsersData?.username ?? '')
  const [vorname, setVorname] = useState(initialUsersData?.vorname ?? '')
  const [nachname, setNachname] = useState(initialUsersData?.nachname ?? '')
  const [serviceAccount, setServiceAccount] = useState(initialUsersData?.serviceAccount ?? false)
  const [status, setStatus] = useState(initialUsersData?.status ?? 'ACTIVE')
  // Ein Status, den das Frontend nicht kennt, bleibt wählbar, sonst ginge er beim Speichern verloren
  const statusOptions = ENTITY_STATUSES.includes(status) ? ENTITY_STATUSES : [...ENTITY_STATUSES, status]

  function submitUsers(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    // Leere Felder gar nicht erst mitschicken
    onSubmit({
      email,
      username: username || undefined,
      vorname: vorname || undefined,
      nachname: nachname || undefined,
      serviceAccount,
      status,
    })
  }

  return (
    <form id={formId} onSubmit={submitUsers}>
      <fieldset className="row g-3" disabled={disabled}>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-email`}>
            E-Mail
          </label>
          <input
            id={`${formId}-email`}
            className="form-control"
            required
            pattern=".*\S.*"
            maxLength={255}
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-username`}>
            Username
          </label>
          <input
            id={`${formId}-username`}
            className="form-control"
            maxLength={100}
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-vorname`}>
            Vorname
          </label>
          <input
            id={`${formId}-vorname`}
            className="form-control"
            maxLength={100}
            value={vorname}
            onChange={(event) => setVorname(event.target.value)}
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-nachname`}>
            Nachname
          </label>
          <input
            id={`${formId}-nachname`}
            className="form-control"
            maxLength={100}
            value={nachname}
            onChange={(event) => setNachname(event.target.value)}
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-status`}>
            Status
          </label>
          <select
            id={`${formId}-status`}
            className="form-select"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
          >
            {statusOptions.map((statusOption) => (
              <option key={statusOption} value={statusOption}>
                {entityStatusName(statusOption)}
              </option>
            ))}
          </select>
        </div>
        <div className="col-12">
          <div className="form-check">
            <input
              id={`${formId}-service-account`}
              className="form-check-input"
              type="checkbox"
              checked={serviceAccount}
              onChange={(event) => setServiceAccount(event.target.checked)}
            />
            <label className="form-check-label" htmlFor={`${formId}-service-account`}>
              Service-Account (kein Mensch, sondern ein Backend-Dienst)
            </label>
          </div>
        </div>
      </fieldset>
    </form>
  )
}
