import { useEffect, useRef, type ReactNode } from 'react'
import { NavLink, useLocation } from 'react-router'
import type { IconName } from '../branding/icons.ts'
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

interface SubheaderProps {
  /** erste Zelle: Modulname oder Status */
  status: ReactNode
  /** optional, ohne Tabs nur die Statuszelle */
  tabs?: SubheaderTab[]
  /** bleibt beim Scrollen stehen (Bootstrap sticky-top) */
  sticky?: boolean
}

/** Der subheader aller Module: Statuszelle, dahinter die Tabs. Der aktive Tab bekommt von NavLink die Klasse active. */
export function Subheader({ status, tabs = [], sticky = false }: SubheaderProps) {
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
      {tabs.length > 0 && (
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
        </div>
      )}
    </nav>
  )
}
