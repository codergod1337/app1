import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate } from 'react-router'
import { Icon } from '../components/Icon.tsx'
import { useSession } from '../modul/login/sessionContext.ts'
import { ChangeUsersPasswordPopup } from '../modul/users/ChangeUsersPasswordPopup.tsx'

interface UserMenuProps {
  /** Restzeit bis zum nächsten Refresh als mm:ss. null: keiner geplant */
  countdown: string | null
}

/**
 * Das Menü hinter dem Profil-Symbol rechts im header: oben die Box, wer angemeldet ist (E-Mail, Session, Refresh),
 * darunter Profil, Passwort ändern und Abmelden. Schließt bei einem Klick daneben und mit Esc, lässt sich mit den
 * Pfeiltasten bedienen. Ohne Anmeldung zeigt es nichts.
 */
export function UserMenu({ countdown }: UserMenuProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const { sessionInfo, logout } = useSession()
  const [open, setOpen] = useState(false)
  const [changingPassword, setChangingPassword] = useState(false)
  const menu = useRef<HTMLDivElement>(null)
  const toggle = useRef<HTMLButtonElement>(null)
  const list = useRef<HTMLDivElement>(null)

  // Klick daneben und Esc schließen das Menü
  useEffect(() => {
    if (!open) {
      return
    }
    function closeOnOutsidePointer(event: PointerEvent) {
      if (!menu.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    function closeOnEscape(event: globalThis.KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false)
        toggle.current?.focus()
      }
    }
    document.addEventListener('pointerdown', closeOnOutsidePointer)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeOnOutsidePointer)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [open])

  if (sessionInfo === null || !sessionInfo.loggedIn) {
    return null
  }

  function menuItems(): HTMLElement[] {
    return [...(list.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])]
  }

  /** Pfeil runter auf dem Knopf öffnet das Menü und springt auf den ersten Eintrag */
  function openWithKeyboard(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setOpen(true)
      requestAnimationFrame(() => menuItems()[0]?.focus())
    }
  }

  /** Pfeiltasten, Pos1 und Ende wandern durch die Einträge, Tab verlässt das Menü */
  function moveFocus(event: KeyboardEvent<HTMLDivElement>) {
    const items = menuItems()
    const index = items.indexOf(document.activeElement as HTMLElement)
    let next: number | null = null
    if (event.key === 'ArrowDown') {
      next = (index + 1) % items.length
    } else if (event.key === 'ArrowUp') {
      next = (index - 1 + items.length) % items.length
    } else if (event.key === 'Home') {
      next = 0
    } else if (event.key === 'End') {
      next = items.length - 1
    } else if (event.key === 'Tab') {
      setOpen(false)
    }
    if (next !== null) {
      event.preventDefault()
      items[next]?.focus()
    }
  }

  /** Danach zur Startseite: Auf einer geschützten Seite schickte RequireLogin sonst sofort zum Login */
  function logoutAndGoHome() {
    setOpen(false)
    logout().finally(() => navigate('/'))
  }

  return (
    <div ref={menu} className="user-menu">
      {/* nur das Symbol mit dünnem Rand, kein Text, kein Pfeil: die E-Mail steht im Kopf der Klappe */}
      <button
        ref={toggle}
        type="button"
        className="header-icon-button user-menu-toggle"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls="user-menu-list"
        aria-label={t('layout.userMenu', { email: sessionInfo.email })}
        onClick={() => setOpen(!open)}
        onKeyDown={openWithKeyboard}
      >
        <Icon name="user" />
      </button>
      {open && (
        <div className="user-menu-panel">
          <div className="user-menu-head">
            <strong>{sessionInfo.email}</strong>
            <span className="user-menu-head-sub">
              {t('layout.session', { number: sessionInfo.sessionNumber })}
              {countdown !== null && ` · ${t('layout.refreshIn', { countdown })}`}
            </span>
          </div>
          <div
            ref={list}
            id="user-menu-list"
            className="user-menu-list"
            role="menu"
            aria-label={t('layout.menu')}
            onKeyDown={moveFocus}
          >
            <Link
              role="menuitem"
              className={pathname.startsWith('/profile') ? 'user-menu-item active' : 'user-menu-item'}
              to="/profile"
              onClick={() => setOpen(false)}
            >
              <Icon name="user" /> {t('layout.profile')}
            </Link>
            <button
              type="button"
              role="menuitem"
              className="user-menu-item"
              onClick={() => {
                setOpen(false)
                setChangingPassword(true)
              }}
            >
              <Icon name="password" /> {t('layout.changePassword')}
            </button>
            <div className="user-menu-divider" role="separator" />
            <button type="button" role="menuitem" className="user-menu-item" onClick={logoutAndGoHome}>
              <Icon name="logout" /> {t('layout.logout')}
            </button>
          </div>
        </div>
      )}
      <ChangeUsersPasswordPopup
        open={changingPassword}
        usersGuid={sessionInfo.usersGuid ?? ''}
        onClose={() => setChangingPassword(false)}
      />
    </div>
  )
}
