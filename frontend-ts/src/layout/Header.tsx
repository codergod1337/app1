import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate } from 'react-router'
import { branding } from '../branding/branding.ts'
import { translateLabel } from '../branding/i18n.ts'
import { Icon } from '../components/Icon.tsx'
import { useOrigin } from '../components/Origin.ts'
import type { LoginState } from '../modul/login/LoginPage.tsx'
import { useSession } from '../modul/login/sessionContext.ts'
import { LanguageMenu } from './LanguageMenu.tsx'
import { OwnAccessRoleCollection } from './OwnAccessRoleCollection.tsx'
import { UserMenu } from './UserMenu.tsx'

/** Restzeit bis zum Zeitpunkt als mm:ss */
function formatCountdown(until: number, now: number): string {
  const remainingSeconds = Math.max(0, Math.floor((until - now) / 1000))
  const minutes = String(Math.floor(remainingSeconds / 60)).padStart(2, '0')
  const seconds = String(remainingSeconds % 60).padStart(2, '0')
  return `${minutes}:${seconds}`
}

/**
 * Kopfzeile jeder Seite, für beide Zustände gebaut: Nicht angemeldet ist ein normaler Zustand.
 * Rechts die Sprachwahl (Flagge, für alle), dann entweder „Anmelden“ oder die eigene ARC und das Benutzermenü.
 * Sitzungsplatz und Countdown bis zum nächsten Refresh stehen nur im Kopf des Benutzermenüs.
 */
export function Header() {
  const { t } = useTranslation()
  const origin = useOrigin()
  const navigate = useNavigate()
  const location = useLocation()
  const { sessionInfo, nextRefreshAt } = useSession()
  const [now, setNow] = useState(() => Date.now())
  const countdown = nextRefreshAt !== null ? formatCountdown(nextRefreshAt, now) : null

  // Sekundentakt für den Countdown, nur solange ein Refresh geplant ist
  useEffect(() => {
    if (nextRefreshAt === null) {
      return
    }
    const interval = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(interval)
  }, [nextRefreshAt])

  function login() {
    const loginState: LoginState = { from: location.pathname }
    navigate('/login', { state: loginState })
  }

  return (
    <header className="header">
      <Link className="header-brand" to="/">
        <Icon name="home" />
        <span>{branding.companyName}</span>
      </Link>
      {/* Nur solange ein origin mitgegeben ist, also nachdem man einen Tab verlassen hat. Das label ist ein Schlüssel oder, bei noch nicht umgestellten Modulen, der Text selbst */}
      {origin && (
        <Link className="button-cancel header-back" to={origin.path}>
          <Icon name="back" /> {t('layout.backTo', { label: translateLabel(origin.label) })}
        </Link>
      )}
      <div className="header-session">
        <LanguageMenu />
        {sessionInfo?.loggedIn === true && (
          <>
            <OwnAccessRoleCollection />
            <UserMenu countdown={countdown} />
          </>
        )}
        {sessionInfo?.loggedIn === false && location.pathname !== '/login' && (
          <button type="button" className="button-other" onClick={login}>
            <Icon name="login" /> {t('layout.login')}
          </button>
        )}
      </div>
    </header>
  )
}
