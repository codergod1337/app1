import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { ContentBox } from '../../components/ContentBox.tsx'
import { claimLogin, startLogin, type Login2faTokens } from './Session.ts'
import { useSession } from './sessionContext.ts'

const FORM_ID = 'login-form'

/** State beim Öffnen: wohin es nach dem Login zurückgeht, z. B. die Seite, von der RequireLogin hierher geschickt hat */
export interface LoginState {
  from: string
}

/** form: E-Mail und Passwort. waiting: wartet auf den Aktivierungslink. done: angemeldet, gleich geht es zurück */
type LoginPhase = 'form' | 'waiting' | 'done'

/**
 * Die Anmeldung mit 2FA: E-Mail und Passwort, dann der Aktivierungslink, dann holt diese Seite die Sitzung im
 * Sekundentakt ab und springt zurück, wo man herkam. Bis es Mailversand gibt, steht der Aktivierungslink hier auf der
 * Seite. Der Token steht darin hinter dem #: Diesen Teil schickt der Browser nie an einen Server.
 */
export function LoginPage() {
  const { loggedIn, reloadSession } = useSession()
  const navigate = useNavigate()
  const from = (useLocation().state as Partial<LoginState> | null)?.from ?? '/'
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [phase, setPhase] = useState<LoginPhase>('form')
  const [loginTokens, setLoginTokens] = useState<Login2faTokens | null>(null)
  const [sending, setSending] = useState(false)
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
        setLoginTokens(result.data)
        setPassword('')
        setPhase('waiting')
      })
      // Error statt nur RequestError: ohne https gibt es crypto.subtle nicht, dann scheitert schon das Hashen
      .catch((error: unknown) => setErrorMessage(error instanceof Error ? error.message : 'Unbekannter Fehler'))
      .finally(() => setSending(false))
  }

  // Im Sekundentakt abholen, solange gewartet wird
  useEffect(() => {
    if (phase !== 'waiting' || loginTokens === null) {
      return
    }
    const pollToken = loginTokens.pollToken
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
          setLoginTokens(null)
          setPhase('form')
        })
        .finally(() => {
          claimRunning.current = false
        })
    }, 1000)
    return () => clearInterval(interval)
  }, [phase, loginTokens, reloadSession])

  function cancelLogin() {
    setLoginTokens(null)
    setPhase('form')
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
            {phase === 'waiting' && loginTokens !== null && (
              <>
                <p>
                  <span className="spinner-border spinner-border-sm me-2" role="status" aria-label="wartet" />
                  Warte auf die Bestätigung …
                </p>
                {/* Bis es Mailversand gibt: der Link, der sonst in der Mail stünde. Öffnet ein neues Fenster, dieses wartet. */}
                <p className="mb-0">
                  Solange es keinen Mailversand gibt, steht der Link hier:{' '}
                  <a href={`/login/confirm#${loginTokens.confirmationToken}`} target="_blank" rel="noreferrer">
                    Aktivierungslink
                  </a>
                </p>
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
