import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { ContentBox } from '../../components/ContentBox.tsx'
import type { LoginState } from './LoginPage.tsx'
import { useSession } from './sessionContext.ts'

interface RequireLoginProps {
  /** Ohne: angemeldet genügt. Mit: der User braucht diese AR, z. B. ADMIN. */
  accessRoleKey?: string
  children: ReactNode
}

/**
 * Wächter um Module, die eine Anmeldung brauchen. Nicht angemeldet: zum Login und danach hierher zurück. Fehlt die AR:
 * „Kein Zugriff“ statt der Seite. Solange die Sitzung beim Start lädt, wird gewartet statt umgeleitet, sonst landete
 * jedes Neuladen einer geschützten Seite beim Login.
 *
 * Nur die Anzeige: Was jemand wirklich darf, entscheidet das Backend.
 */
export function RequireLogin({ accessRoleKey, children }: RequireLoginProps) {
  const { sessionInfo, hasAccessRole } = useSession()
  const location = useLocation()

  if (sessionInfo === null) {
    return (
      <div className="main-content">
        <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
      </div>
    )
  }
  if (!sessionInfo.loggedIn) {
    const loginState: LoginState = { from: location.pathname }
    return <Navigate to="/login" replace state={loginState} />
  }
  if (accessRoleKey !== undefined && !hasAccessRole(accessRoleKey)) {
    return (
      <div className="main-content">
        <ContentBox title="Kein Zugriff">
          Dafür fehlt die AccessRole <strong>{accessRoleKey}</strong>.
        </ContentBox>
      </div>
    )
  }
  return <>{children}</>
}
