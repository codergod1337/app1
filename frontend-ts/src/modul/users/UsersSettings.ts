import { request } from '../../api/client.ts'

/**
 * Eine Anzeigeeinstellung des angemeldeten Users (UsersSettings.java): ein Schlüssel-Wert-Paar. Es gibt keine
 * Vorgabe-Zeilen, fehlt eine Einstellung, gilt der Standard des Frontends. Gelesen und geschrieben über den
 * UsersSettingsProvider (useUsersSettings), nicht direkt.
 */
export interface UsersSettings {
  id: number
  usersGuid: string
  /** normale Key-Regel, z. B. LANGUAGE */
  key: string
  value: string | null
}

/** Die Sprache der Oberfläche, der Code aus LANGUAGES, gesetzt über die Flagge im Header */
export const USERS_SETTING_LANGUAGE = 'LANGUAGE'

// ===== Endpunkte des UsersSettingsController: immer die eigenen, die guid kommt aus dem Token =====

const USERS_SETTINGS_PATH = '/api/rest/v1/userssettings'

/** Alle eigenen Einstellungen, nach dem Login */
export function getOwnUsersSettings() {
  return request<UsersSettings[]>('GET', USERS_SETTINGS_PATH)
}

/** Einen eigenen Wert setzen, die Zeile entsteht beim ersten Setzen */
export function changeOwnUsersSettings(key: string, value: string) {
  return request<UsersSettings>('PUT', USERS_SETTINGS_PATH, { key, value })
}

/** Einen eigenen Wert entfernen, danach gilt wieder der Standard des Frontends */
export function deleteOwnUsersSettings(key: string) {
  return request<void>('DELETE', USERS_SETTINGS_PATH, { key })
}
