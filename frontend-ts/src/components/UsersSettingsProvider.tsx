import { Fragment, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { applyUiLanguage } from '../branding/i18n.ts'
import { DEFAULT_LANGUAGE, LANGUAGES, type LanguageCode } from '../branding/languages.ts'
import { useSession } from '../modul/login/sessionContext.ts'
import {
  changeOwnUsersSettings,
  deleteOwnUsersSettings,
  getOwnUsersSettings,
  USERS_SETTING_LANGUAGE,
} from '../modul/users/UsersSettings.ts'
import { LanguageContext, type LanguageState } from './languageContext.ts'
import { setCurrentLanguage } from './multilingual.ts'
import { UsersSettingsContext, type UsersSettingsState } from './usersSettingsContext.ts'

/** Die Sprachwahl eines Gasts lebt in sessionStorage: der Besuchszeitraum, nichts Dauerhaftes. Bewusst kein localStorage. */
const GUEST_LANGUAGE_STORAGE_KEY = 'app1-language'

function isLanguageCode(value: string | null): value is LanguageCode {
  return LANGUAGES.some((candidate) => candidate.code === value)
}

function storedGuestLanguage(): LanguageCode {
  try {
    const value = sessionStorage.getItem(GUEST_LANGUAGE_STORAGE_KEY)
    return isLanguageCode(value) ? value : DEFAULT_LANGUAGE
  } catch {
    return DEFAULT_LANGUAGE
  }
}

/** Die eigenen Einstellungen samt der guid, für die sie geladen wurden: nach Abmelden oder Userwechsel gelten sie nicht */
interface LoadedSettings {
  usersGuid: string
  settings: Map<string, string>
}

/**
 * Hält die eigenen Anzeigeeinstellungen (useUsersSettings) und darauf die Sprache der Oberfläche (useLanguage), nach dem
 * Muster der Vorlage: Nach Login oder Start mit Sitzung werden die eigenen Zeilen geladen, der Endpunkt ist angemeldet,
 * ohne Anmeldung gibt es keine. Fehlt eine Einstellung, gilt der Standard des Frontends.
 *
 * Sprache in drei Stufen: UsersSetting LANGUAGE (angemeldet), sonst die Wahl des Gasts in sessionStorage, sonst die
 * Standardsprache. Hat ein Angemeldeter noch kein Setting, gilt seine Gast-Wahl weiter. Ein Wechsel setzt die Sprache
 * sofort und speichert sie angemeldet nebenbei; scheitert das Speichern, gilt sie trotzdem für die Sitzung.
 *
 * translate ohne Sprache nimmt die Sprache von hier (setCurrentLanguage), die festen Texte folgen über i18n
 * (applyUiLanguage). Damit auch alles, was translate ohne Sprache ruft, umschaltet, baut ein Sprachwechsel alles
 * unterhalb neu auf (key am Fragment). Sitzung und Stammdaten liegen darüber und bleiben.
 */
export function UsersSettingsProvider({ children }: { children: ReactNode }) {
  const { sessionInfo } = useSession()
  const usersGuid = sessionInfo?.loggedIn === true ? sessionInfo.usersGuid : null
  const [loadedSettings, setLoadedSettings] = useState<LoadedSettings | null>(null)
  const [guestLanguage, setGuestLanguage] = useState<LanguageCode>(storedGuestLanguage)
  const settings = loadedSettings !== null && loadedSettings.usersGuid === usersGuid ? loadedSettings.settings : null

  // Nach Login oder Start mit Sitzung die eigenen Zeilen holen. Verschwindet die Sitzung vorher, wird die Antwort verworfen.
  useEffect(() => {
    if (usersGuid === null) {
      return undefined
    }
    let active = true
    getOwnUsersSettings()
      .then(({ data }) => {
        if (active) {
          setLoadedSettings({ usersGuid, settings: new Map(data.map((row) => [row.key, row.value ?? ''])) })
        }
      })
      .catch(() => {
        // ohne Einstellungen gelten die Standards des Frontends
        if (active) {
          setLoadedSettings({ usersGuid, settings: new Map() })
        }
      })
    return () => {
      active = false
    }
  }, [usersGuid])

  const settingValue = useCallback((key: string) => settings?.get(key) ?? null, [settings])

  /** Erst lokal, damit es sofort gilt, dann speichern */
  const changeSetting = useCallback(
    async (key: string, value: string) => {
      if (usersGuid === null) {
        return
      }
      setLoadedSettings((current) => {
        const next = new Map(current !== null && current.usersGuid === usersGuid ? current.settings : [])
        next.set(key, value)
        return { usersGuid, settings: next }
      })
      await changeOwnUsersSettings(key, value)
    },
    [usersGuid],
  )

  const deleteSetting = useCallback(
    async (key: string) => {
      if (usersGuid === null) {
        return
      }
      setLoadedSettings((current) => {
        const next = new Map(current !== null && current.usersGuid === usersGuid ? current.settings : [])
        next.delete(key)
        return { usersGuid, settings: next }
      })
      await deleteOwnUsersSettings(key)
    },
    [usersGuid],
  )

  const storedLanguage = settingValue(USERS_SETTING_LANGUAGE)
  const language: LanguageCode = usersGuid !== null && isLanguageCode(storedLanguage) ? storedLanguage : guestLanguage

  const setLanguage = useCallback(
    (code: LanguageCode) => {
      setGuestLanguage(code)
      try {
        sessionStorage.setItem(GUEST_LANGUAGE_STORAGE_KEY, code)
      } catch {
        // ohne sessionStorage gilt die Wahl eben nur bis zum Neuladen
      }
      changeSetting(USERS_SETTING_LANGUAGE, code).catch(() => {
        // Speichern gescheitert: die Sprache gilt trotzdem für die Sitzung
      })
    },
    [changeSetting],
  )

  // Für translate ohne Sprache und die festen Texte (i18n), vor dem Zeichnen der Kinder. Idempotent, deshalb im Render erlaubt.
  setCurrentLanguage(language)
  applyUiLanguage(language)

  const usersSettingsState = useMemo<UsersSettingsState>(
    () => ({ loaded: settings !== null, settingValue, changeSetting, deleteSetting }),
    [settings, settingValue, changeSetting, deleteSetting],
  )
  const languageState = useMemo<LanguageState>(() => ({ language, setLanguage }), [language, setLanguage])

  return (
    <UsersSettingsContext.Provider value={usersSettingsState}>
      <LanguageContext.Provider value={languageState}>
        <Fragment key={language}>{children}</Fragment>
      </LanguageContext.Provider>
    </UsersSettingsContext.Provider>
  )
}
