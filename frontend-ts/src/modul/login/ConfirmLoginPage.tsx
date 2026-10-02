import { useEffect, useState } from 'react'
import { useLocation } from 'react-router'
import { ContentBox } from '../../components/ContentBox.tsx'
import { confirmLogin } from './Session.ts'

/**
 * Die Seite hinter dem Aktivierungslink (/login/confirm#token), im neuen Fenster. Liest den Token hinter dem # und
 * bestätigt die Anmeldung. Hier entsteht keine Sitzung: Die holt das ursprüngliche Fenster ab. Ein zweites Bestätigen
 * (Doppelklick, Neuladen) gilt im Backend als Erfolg.
 */
export function ConfirmLoginPage() {
  const confirmationToken = useLocation().hash.slice(1)
  // null: wird noch bestätigt
  const [confirmed, setConfirmed] = useState<boolean | null>(confirmationToken ? null : false)
  const [errorMessage, setErrorMessage] = useState<string | null>(
    confirmationToken ? null : 'Kein Bestätigungs-Token in der Adresse.',
  )

  useEffect(() => {
    if (!confirmationToken) {
      return
    }
    let active = true
    confirmLogin(confirmationToken)
      .then(() => {
        if (active) {
          setConfirmed(true)
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setConfirmed(false)
          setErrorMessage(error instanceof Error ? error.message : 'Unbekannter Fehler')
        }
      })
    return () => {
      active = false
    }
  }, [confirmationToken])

  return (
    <div className="main-content">
      <div className="row justify-content-center">
        <div className="col-md-8 col-lg-5">
          <ContentBox title="Anmeldung bestätigen">
            {confirmed === null && (
              <p className="mb-0">
                <span className="spinner-border spinner-border-sm me-2" role="status" aria-label="lädt" />
                Bestätige …
              </p>
            )}
            {confirmed === true && (
              <p className="mb-0">
                Bestätigt. Die Anmeldung wird im ursprünglichen Fenster abgeschlossen, dieses Fenster kann geschlossen
                werden.
              </p>
            )}
            {confirmed === false && <p className="mb-0 text-danger">{errorMessage}</p>}
          </ContentBox>
        </div>
      </div>
    </div>
  )
}
