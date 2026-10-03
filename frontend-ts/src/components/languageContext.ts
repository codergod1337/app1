import { createContext, useContext } from 'react'
import type { LanguageCode } from '../branding/languages.ts'

/** Die Sprache der Oberfläche für alle Module: useLanguage() */
export interface LanguageState {
  /** die gewählte Sprache: das UsersSetting LANGUAGE, ohne Anmeldung die Wahl des Gasts, sonst die Standardsprache */
  language: LanguageCode
  /** wählt die Sprache: sofort für alles, angemeldet dazu gespeichert als UsersSetting */
  setLanguage: (language: LanguageCode) => void
}

export const LanguageContext = createContext<LanguageState | null>(null)

/** Die Sprache der Oberfläche. Nur innerhalb des UsersSettingsProvider. */
export function useLanguage(): LanguageState {
  const languageState = useContext(LanguageContext)
  if (languageState === null) {
    throw new Error('useLanguage nur innerhalb des UsersSettingsProvider')
  }
  return languageState
}
