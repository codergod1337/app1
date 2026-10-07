import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { ContentBox } from '../../components/ContentBox.tsx'
import { claimLogin, confirmLogin, startLogin, type Login2faPollToken } from './Session.ts'
import { useSession } from './sessionContext.ts'

const FORM_ID = 'login-form'

/** State beim Öffnen: wohin es nach dem Login zurückgeht, z. B. die Seite, von der RequireLogin hierher geschickt hat */
export interface LoginState {
  from: string
}

/** form: E-Mail und Passwort. waiting: wartet auf den Aktivierungslink. done: angemeldet, gleich geht es zurück */
type LoginPhase = 'form' | 'waiting' | 'done'

/**
 * Die Anmeldung mit 2FA: E-Mail und Passwort, dann geht die Mail mit Aktivierungslink und Token an den User, dann holt
 * diese Seite die Sitzung im Sekundentakt ab und springt zurück, wo man herkam. Bestätigen geht auf zwei Wegen: der
 * Link öffnet die ConfirmLoginPage im neuen Fenster, oder der Token aus der Mail wird hier eingefügt. Im Link steht
 * der Token hinter dem #: Diesen Teil schickt der Browser nie an einen Server.
 */
export function LoginPage() {
  const { loggedIn, reloadSession } = useSession()
  const navigate = useNavigate()
  const from = (useLocation().state as Partial<LoginState> | null)?.from ?? '/'
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [phase, setPhase] = useState<LoginPhase>('form')
  const [loginStart, setLoginStart] = useState<Login2faPollToken | null>(null)
  const [sending, setSending] = useState(false)
  // der Token aus der Mail, von Hand eingefügt statt über den Link
  const [confirmationToken, setConfirmationToken] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  // Ein Abholen kann länger als eine Sekunde dauern: nie zwei gleichzeitig
  const claimRunning = useRef(false)

  // Schon oder soeben angemeldet: zurück, wo man herkam
  useEffect(() => {
    if (loggedIn) {
      navigate(from, { replace: true })
    }
  }, [loggedIn, from, navigate])

  function submitLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSending(true)
    setErrorMessage(null)
    startLogin(email, password)
      .then((result) => {
        setLoginStart(result.data)
        setPassword('')
        setPhase('waiting')
      })
      // Error statt nur RequestError: ohne https gibt es crypto.subtle nicht, dann scheitert schon das Hashen
      .catch((error: unknown) => setErrorMessage(error instanceof Error ? error.message : 'Unbekannter Fehler'))
      .finally(() => setSending(false))
  }

  // Im Sekundentakt abholen, solange gewartet wird
  useEffect(() => {
    if (phase !== 'waiting' || loginStart === null) {
      return
    }
    const pollToken = loginStart.pollToken
    const interval = setInterval(() => {
      if (claimRunning.current) {
        return
      }
      claimRunning.current = true
      claimLogin(pollToken)
        .then((result) => {
          if (result.data.status === 'LOGGED_IN') {
            // Abholen ist einmalig: sofort aufhören, sonst scheiterte der nächste Takt
            setPhase('done')
            return reloadSession()
          }
        })
        .catch((error: unknown) => {
          // Wartezeile abgelaufen oder unbekannt: zurück zum Formular
          setErrorMessage(error instanceof Error ? error.message : 'Unbekannter Fehler')
          setLoginStart(null)
          setPhase('form')
        })
        .finally(() => {
          claimRunning.current = false
        })
    }, 1000)
    return () => clearInterval(interval)
  }, [phase, loginStart, reloadSession])

  function cancelLogin() {
    setLoginStart(null)
    setConfirmationToken('')
    setPhase('form')
  }

  /** Der eingefügte Token: derselbe Endpunkt wie der Link. Danach holt der nächste Takt die Sitzung ab. */
  function submitConfirmationToken(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setConfirming(true)
    setErrorMessage(null)
    confirmLogin(confirmationToken.trim())
      .then(() => setConfirmationToken(''))
      .catch((error: unknown) => setErrorMessage(error instanceof Error ? error.message : 'Unbekannter Fehler'))
      .finally(() => setConfirming(false))
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      <span className="ms-auto d-flex gap-2">
        {phase === 'form' && (
          <button type="submit" form={FORM_ID} className="button-save" disabled={sending}>
            Anmelden
          </button>
        )}
        {phase === 'waiting' && (
          <button type="button" className="button-cancel" onClick={cancelLogin}>
            Abbrechen
          </button>
        )}
      </span>
    </>
  )

  return (
    <div className="main-content">
      <div className="row justify-content-center">
        <div className="col-md-8 col-lg-5">
          <ContentBox title="Anmelden" footer={footer}>
            {phase === 'form' && (
              <form id={FORM_ID} onSubmit={submitLogin}>
                <fieldset className="row g-3" disabled={sending}>
                  <div className="col-12">
                    <label className="form-label" htmlFor={`${FORM_ID}-email`}>
                      E-Mail
                    </label>
                    {/* type="text" statt "email": ob es das Konto gibt, entscheidet allein das Backend */}
                    <input
                      id={`${FORM_ID}-email`}
                      className="form-control"
                      autoComplete="username"
                      required
                      value={email}
                      onChange={(event) => setEmail(event.target.value)}
                    />
                  </div>
                  <div className="col-12">
                    <label className="form-label" htmlFor={`${FORM_ID}-password`}>
                      Passwort
                    </label>
                    <input
                      id={`${FORM_ID}-password`}
                      className="form-control"
                      type="password"
                      autoComplete="current-password"
                      required
                      value={password}
                      onChange={(event) => setPassword(event.target.value)}
                    />
                  </div>
                </fieldset>
              </form>
            )}
            {phase === 'waiting' && loginStart !== null && (
              <>
                <p>
                  <span className="spinner-border spinner-border-sm me-2" role="status" aria-label="wartet" />
                  Warte auf die Bestätigung …
                </p>
                <p>
                  Eine E-Mail ist unterwegs an <strong>{email}</strong>. Öffne den Link darin, oder füge den Code aus
                  der Mail hier ein. Dieses Fenster meldet sich dann von selbst an.
                </p>
                <form className="d-flex gap-2" onSubmit={submitConfirmationToken}>
                  <input
                    className="form-control"
                    autoComplete="one-time-code"
                    placeholder="Code aus der Mail"
                    aria-label="Code aus der Mail"
                    value={confirmationToken}
                    onChange={(event) => setConfirmationToken(event.target.value)}
                  />
                  <button type="submit" className="button-save" disabled={confirming || confirmationToken.trim() === ''}>
                    Bestätigen
                  </button>
                </form>
              </>
            )}
            {phase === 'done' && (
              <p className="mb-0">
                <span className="spinner-border spinner-border-sm me-2" role="status" aria-label="lädt" />
                Angemeldet, einen Moment …
              </p>
            )}
          </ContentBox>
        </div>
      </div>
    </div>
  )
}
