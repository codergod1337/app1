import { useEffect, useRef, type ReactNode } from 'react'
import { NavLink, useLocation } from 'react-router'
import type { IconName } from '../branding/icons.ts'
import { Hoverlay } from './Hoverlay.tsx'
import { Icon } from './Icon.tsx'
import { PackIcon } from './PackIcon.tsx'
import type { PackSymbol } from './PackSymbol.ts'

export interface SubheaderTab {
  label: string
  /** festes Symbol der Oberfläche */
  icon?: IconName
  /** Symbol aus den Daten, z. B. das eines Solr-Kerns. Steht beides da, kommt erst icon, dann symbol. */
  symbol?: PackSymbol
  /** Farbe des symbol, Hex. Fehlt sie, gilt die Schriftfarbe des Tabs */
  color?: string
  /** Ziel des Tabs, z. B. /admin/users */
  to: string
}

/**
 * Ein Schalter in der Leiste, kein Ziel: sieht aus wie ein Tab, schaltet aber etwas an oder aus, z. B. einen Kern der
 * Suche (nach den Kernreitern der SuchePage der Vorlage). Mehrere dürfen zugleich an sein.
 */
export interface SubheaderToggle {
  key: string
  label: string
  icon?: IconName
  /** Symbol aus den Daten, z. B. das eines Solr-Kerns */
  symbol?: PackSymbol
  /** die Farbe, in der der Schalter leuchtet, wenn er an ist, z. B. die Kernfarbe; ohne Angabe die des Themes */
  color?: string
  /** die Schrift auf dieser Farbe */
  textColor?: string
  active: boolean
  onClick: () => void
  /** gesperrt, nicht versteckt: Man soll sehen, was es gäbe, z. B. einen Kern ohne Such-Rolle */
  disabled?: boolean
  /** Erklärung im Hoverlay, z. B. welche AR fehlt oder was der Schalter kostet */
  hint?: ReactNode
  /** ein Strich davor: Der Schalter gehört nicht zur Reihe davor, z. B. ändert er die Darstellung statt einen Kern zu wählen */
  separatorBefore?: boolean
}

interface SubheaderProps {
  /** erste Zelle: Modulname oder Status */
  status: ReactNode
  /** optional, ohne Tabs nur die Statuszelle */
  tabs?: SubheaderTab[]
  /** optional, Schalter hinter den Tabs in derselben Leiste */
  toggles?: SubheaderToggle[]
  /** optional, Knöpfe rechts außen, z. B. die Aktionen und Schließen des Viewers */
  actions?: ReactNode
  /** bleibt beim Scrollen stehen (Bootstrap sticky-top) */
  sticky?: boolean
}

/**
 * Der subheader aller Module: Statuszelle, dahinter die Tabs und die Schalter, rechts außen die Aktionen. Der aktive
 * Tab bekommt von NavLink die Klasse active, ein eingeschalteter Schalter dieselbe Klasse, so sehen beide gleich aus.
 */
export function Subheader({ status, tabs = [], toggles = [], actions, sticky = false }: SubheaderProps) {
  const tabsRef = useRef<HTMLDivElement>(null)
  const { pathname } = useLocation()

  // Den aktiven Tab in die Mitte der Leiste schieben (nur waagerecht, die Seite bleibt stehen)
  useEffect(() => {
    const strip = tabsRef.current
    const activeTab = strip?.querySelector<HTMLElement>('.subheader-tab.active')
    if (!strip || !activeTab) {
      return
    }
    const offset = activeTab.getBoundingClientRect().left - strip.getBoundingClientRect().left
    strip.scrollLeft += offset - (strip.clientWidth - activeTab.offsetWidth) / 2
  }, [pathname])

  return (
    <nav className={sticky ? 'subheader sticky-top' : 'subheader'}>
      <span className="subheader-status">{status}</span>
      {(tabs.length > 0 || toggles.length > 0) && (
        // Passen nicht alle Tabs hin (z. B. auf dem Handy), wischt man die Leiste nach links und rechts
        <div ref={tabsRef} className="subheader-tabs">
          {tabs.map((tab) => (
            <NavLink key={tab.to} className="subheader-tab" to={tab.to}>
              {tab.icon && <Icon name={tab.icon} />}
              {tab.symbol && (
                <span style={{ color: tab.color }}>
                  <PackIcon symbol={tab.symbol} />
                </span>
              )}{' '}
              {tab.label}
            </NavLink>
          ))}
          {toggles.map((toggle) => (
            <span key={toggle.key} className="subheader-toggle">
              {toggle.separatorBefore && <span className="subheader-separator" />}
              <Hoverlay text={toggle.hint ?? null}>
                <button
                  type="button"
                  className={toggle.active ? 'subheader-tab active' : 'subheader-tab'}
                  style={
                    toggle.active && toggle.color !== undefined
                      ? { backgroundColor: toggle.color, color: toggle.textColor }
                      : undefined
                  }
                  aria-pressed={toggle.active}
                  disabled={toggle.disabled}
                  onClick={toggle.onClick}
                >
                  {toggle.icon && <Icon name={toggle.icon} />}
                  {toggle.symbol && <PackIcon symbol={toggle.symbol} />} {toggle.label}
                </button>
              </Hoverlay>
            </span>
          ))}
        </div>
      )}
      {actions !== undefined && <span className="subheader-actions">{actions}</span>}
    </nav>
  )
}
