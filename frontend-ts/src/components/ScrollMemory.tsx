import { useCallback, useEffect, useLayoutEffect, useRef } from 'react'
import { useLocation, useParams } from 'react-router'
import { routePattern, scrollSettingKey } from './scrollMemory.ts'
import { useUsersSettings } from './usersSettingsContext.ts'

/** So lange holt der ResizeObserver einen geklemmten Sprung nach, wenn die Seite noch nachlädt */
const RESTORE_WINDOW_MS = 2000

/**
 * Die Scrollhöhe je Seite, nach dem useReiterScroll der Vorlage, aber dauerhaft: Wer in einem Tab weit unten steht,
 * einem Link folgt und zurückkommt, landet wieder an derselben Stelle, auch nach einem Neuladen oder an einem anderen
 * Gerät. Die Seite ist das Routenmuster ohne Parameter (scrollMemory.ts): ein Wert je Tab eines Moduls, nicht je Kern
 * oder Artikelnummer.
 *
 * Gemerkt wird bei jedem Scrollen im Speicher, geschrieben einmal beim Verlassen der Seite als eigene UsersSetting je
 * Muster, nur wenn sich die Höhe geändert hat, beim Schließen des Tabs mit keepalive. Wiederhergestellt wird bei jedem
 * Betreten des Musters, vor dem Malen: aus dem Speicher dieser Sitzung, sonst aus der Einstellung, sonst oben. Lädt
 * der Inhalt nach und ist die Seite noch zu kurz, holt ein ResizeObserver den Sprung nach, längstens zwei Sekunden.
 * Ohne Anmeldung bleibt alles im Speicher der Sitzung.
 *
 * Steht einmal im Layout. Die eigene Wiederherstellung des Browsers beim Zurück ist aus, sie würde gegen diese arbeiten.
 */
export function ScrollMemory() {
  const { pathname } = useLocation()
  const params = useParams()
  const { loaded, settingValue, changeSetting } = useUsersSettings()
  const key = scrollSettingKey(routePattern(pathname, params))

  /** Muster → zuletzt gescrollte Höhe in dieser Sitzung */
  const positions = useRef(new Map<string, number>())
  const currentKey = useRef<string | null>(null)
  /** der noch nicht erreichte Sprung, wenn die Seite beim Herstellen zu kurz war */
  const pending = useRef<{ key: string; top: number; until: number } | null>(null)

  useEffect(() => {
    if ('scrollRestoration' in window.history) {
      window.history.scrollRestoration = 'manual'
    }
  }, [])

  // jedes Scrollen merkt die Höhe unter dem aktuellen Muster, nicht erst beim Wechsel: dann hat der Browser sie womöglich schon geklemmt
  useEffect(() => {
    function remember() {
      if (currentKey.current !== null) {
        positions.current.set(currentKey.current, window.scrollY)
      }
    }
    window.addEventListener('scroll', remember, { passive: true })
    return () => window.removeEventListener('scroll', remember)
  }, [])

  /** Beim Verlassen speichern, nur wenn sich die Höhe gegenüber der Einstellung geändert hat */
  const save = useCallback(
    (leavingKey: string, keepalive: boolean) => {
      const top = positions.current.get(leavingKey)
      if (top === undefined) {
        return
      }
      const value = String(Math.round(top))
      const stored = settingValue(leavingKey)
      if (stored === value || (stored === null && value === '0')) {
        return
      }
      changeSetting(leavingKey, value, keepalive).catch(() => {
        // nicht gespeichert: die Höhe gilt trotzdem für diese Sitzung
      })
    },
    [settingValue, changeSetting],
  )

  /** Die gemerkte Höhe des Musters herstellen: Sitzung vor Einstellung vor oben */
  const restore = useCallback(
    (enteringKey: string | null) => {
      let top = 0
      if (enteringKey !== null) {
        const remembered = positions.current.get(enteringKey)
        const stored = Number(settingValue(enteringKey) ?? 0)
        top = remembered ?? (Number.isFinite(stored) ? stored : 0)
      }
      window.scrollTo({ top, behavior: 'auto' })
      pending.current =
        enteringKey !== null && top > 0 ? { key: enteringKey, top, until: Date.now() + RESTORE_WINDOW_MS } : null
    },
    [settingValue],
  )

  // Musterwechsel: das alte speichern, das neue herstellen, vor dem Malen, damit der Sprung unsichtbar bleibt
  useLayoutEffect(() => {
    if (currentKey.current === key) {
      return
    }
    if (currentKey.current !== null) {
      save(currentKey.current, false)
    }
    currentKey.current = key
    restore(key)
  }, [key, save, restore])

  // Die Einstellungen kommen nach dem Login, oft nach der ersten Seite: dann noch einmal herstellen, wenn noch nicht gescrollt wurde
  useEffect(() => {
    if (loaded && key !== null && !positions.current.has(key)) {
      restore(key)
    }
  }, [loaded, key, restore])

  // Nachladender Inhalt: den geklemmten Sprung nachholen, sobald die Seite hoch genug ist
  useEffect(() => {
    if (typeof ResizeObserver === 'undefined') {
      return undefined
    }
    const observer = new ResizeObserver(() => {
      const open = pending.current
      if (open === null) {
        return
      }
      if (open.key !== currentKey.current || Date.now() > open.until) {
        pending.current = null
        return
      }
      const maximum = document.documentElement.scrollHeight - window.innerHeight
      if (maximum >= open.top) {
        window.scrollTo({ top: open.top, behavior: 'auto' })
        pending.current = null
      }
    })
    observer.observe(document.documentElement)
    return () => observer.disconnect()
  }, [])

  // Tab zu oder weg von der Seite: den letzten Stand noch loswerden
  useEffect(() => {
    function onPageHide() {
      if (currentKey.current !== null) {
        save(currentKey.current, true)
      }
    }
    window.addEventListener('pagehide', onPageHide)
    return () => window.removeEventListener('pagehide', onPageHide)
  }, [save])

  return null
}
