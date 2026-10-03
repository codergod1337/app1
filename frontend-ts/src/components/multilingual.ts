/*
 * Mehrsprachige Texte werden als JSON gespeichert, das Sprachkürzel ist der Schlüssel:
 * {"de": "Verwalter", "en": "Administrator"}
 */
import { DEFAULT_LANGUAGE, LANGUAGES, type LanguageCode } from '../branding/languages.ts'

export type MultilingualText = Partial<Record<LanguageCode, string>>

/** Liest das JSON. Einfacher Text von früher (kein JSON) gilt als Text der Standardsprache. */
export function parseMultilingual(value: string | null): MultilingualText {
  if (value === null || value === '') {
    return {}
  }
  try {
    const parsed: unknown = JSON.parse(value)
    if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
      const text: MultilingualText = {}
      for (const { code } of LANGUAGES) {
        const entry = (parsed as Record<string, unknown>)[code]
        if (typeof entry === 'string' && entry !== '') {
          text[code] = entry
        }
      }
      return text
    }
  } catch {
    // kein JSON: siehe unten
  }
  return { [DEFAULT_LANGUAGE]: value }
}

/** Schreibt das JSON, leere Sprachen fallen weg. Ganz leer: null. */
export function stringifyMultilingual(text: MultilingualText): string | null {
  const filled = Object.fromEntries(Object.entries(text).filter(([, entry]) => entry !== undefined && entry !== ''))
  return Object.keys(filled).length > 0 ? JSON.stringify(filled) : null
}

/**
 * Die Sprache der Oberfläche, gesetzt vom UsersSettingsProvider (Flagge im Header, UsersSetting LANGUAGE). translate
 * ohne Sprache nimmt sie. Ein Modul-Wert statt eines Contexts, damit auch Spalten und Helfer außerhalb von Komponenten
 * übersetzen; bei einem Wechsel baut der Provider alles darunter neu auf. In Komponenten: useLanguage().
 */
let currentUiLanguage: LanguageCode = DEFAULT_LANGUAGE

export function setCurrentLanguage(language: LanguageCode) {
  currentUiLanguage = language
}

export function currentLanguage(): LanguageCode {
  return currentUiLanguage
}

/** Der Text in der gewünschten Sprache, ohne Angabe der Oberfläche. Fehlt er, die Standardsprache, fehlt auch die, der erste vorhandene. */
export function translate(value: string | null, language: LanguageCode = currentUiLanguage): string {
  const text = parseMultilingual(value)
  return text[language] ?? text[DEFAULT_LANGUAGE] ?? Object.values(text)[0] ?? ''
}
