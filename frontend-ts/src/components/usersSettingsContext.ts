import { createContext, useContext } from 'react'

/** Die eigenen Anzeigeeinstellungen für alle Module: useUsersSettings() */
export interface UsersSettingsState {
  /** true, sobald die eigenen Einstellungen nach dem Login geladen sind. Ohne Anmeldung immer false. */
  loaded: boolean
  /** Der Wert zu einem key, oder null: dann gilt der Standard des Frontends. Ohne Anmeldung immer null. */
  settingValue: (key: string) => string | null
  /** Setzt einen eigenen Wert, sofort sichtbar, dann gespeichert (PUT) */
  changeSetting: (key: string, value: string) => Promise<void>
  /** Entfernt einen eigenen Wert (DELETE), zurück auf den Standard */
  deleteSetting: (key: string) => Promise<void>
}

export const UsersSettingsContext = createContext<UsersSettingsState | null>(null)

/** Die eigenen Einstellungen. Nur innerhalb des UsersSettingsProvider. */
export function useUsersSettings(): UsersSettingsState {
  const usersSettingsState = useContext(UsersSettingsContext)
  if (usersSettingsState === null) {
    throw new Error('useUsersSettings nur innerhalb des UsersSettingsProvider')
  }
  return usersSettingsState
}
