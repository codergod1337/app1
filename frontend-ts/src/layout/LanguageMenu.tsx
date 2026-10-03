import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { LANGUAGES } from '../branding/languages.ts'
import { useLanguage } from '../components/languageContext.ts'

/**
 * Die Sprachwahl im header, links neben dem Benutzermenü: nur die Flagge der gewählten Sprache mit dünnem Rand, die
 * Klappe dahinter (Hoverlay-Look) bietet alle aktivierten Sprachen mit Flagge und Landesname. Für Gäste und
 * Angemeldete; angemeldet wird die Wahl als UsersSetting LANGUAGE gespeichert (useLanguage). Gleiches Verhalten wie das
 * Benutzermenü: schließt bei einem Klick daneben und mit Esc.
 */
export function LanguageMenu() {
  const { t } = useTranslation()
  const { language, setLanguage } = useLanguage()
  const [open, setOpen] = useState(false)
  const menu = useRef<HTMLDivElement>(null)
  const toggle = useRef<HTMLButtonElement>(null)
  const current = LANGUAGES.find((candidate) => candidate.code === language) ?? LANGUAGES[0]

  // Klick daneben und Esc schließen das Menü
  useEffect(() => {
    if (!open) {
      return undefined
    }
    function closeOnOutsidePointer(event: PointerEvent) {
      if (!menu.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    function closeOnEscape(event: KeyboardEvent) {
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

  return (
    <div ref={menu} className="user-menu language-menu">
      {/* nur die Flagge mit dünnem Rand, kein Pfeil */}
      <button
        ref={toggle}
        type="button"
        className="header-icon-button language-menu-toggle"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls="language-menu-list"
        aria-label={t('layout.language', { country: current.countryName })}
        onClick={() => setOpen(!open)}
      >
        <span className={`fi fi-${current.flag}`} />
      </button>
      {open && (
        <div className="user-menu-panel language-menu-panel">
          <div id="language-menu-list" className="user-menu-list" role="menu" aria-label={t('layout.languageMenu')}>
            {LANGUAGES.map(({ code, flag, countryName }) => (
              <button
                key={code}
                type="button"
                role="menuitem"
                className={code === language ? 'user-menu-item active' : 'user-menu-item'}
                onClick={() => {
                  setOpen(false)
                  setLanguage(code)
                }}
              >
                <span className={`fi fi-${flag}`} /> {countryName}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
