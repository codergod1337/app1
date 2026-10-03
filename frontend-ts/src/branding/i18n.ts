import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import { DEFAULT_LANGUAGE, LANGUAGES, type LanguageCode } from './languages.ts'
import bg from './locales/bg.json'
import de from './locales/de.json'
import en from './locales/en.json'

/**
 * Die festen Texte der Oberfläche (react-i18next): je Sprache eine Datei in locales/, gleich aufgebaut, verschachtelt
 * nach Modul (common, layout, solrmanager, …). Jeder Text hat einen Schlüssel, der seine Bedeutung benennt, und steht
 * als ganzer Satz mit Platzhaltern ({{key}}) in jeder Sprache, nie wortweise. Mehrzahl über _one und _other.
 *
 * de ist die Quelle. Fehlt eine Übersetzung, erscheint Deutsch, fehlt der Schlüssel ganz, erscheint der Schlüssel:
 * So fällt jede Lücke auf. Das Skript scripts/check-locales.mjs (npm run lint) prüft, dass alle drei Dateien dieselben
 * Schlüssel haben. Die Schlüssel sind typisiert (src/i18next.d.ts), ein Tippfehler fällt beim Kompilieren auf.
 *
 * Datentexte (displayName, description als JSON aus der Datenbank) laufen weiter über translate() in multilingual.ts.
 * In Komponenten: const { t } = useTranslation(); t('solrmanager.cores.title'). Außerhalb von Komponenten: i18n.t.
 */
export const resources = {
  de: { translation: de },
  en: { translation: en },
  bg: { translation: bg },
} as const

void i18n.use(initReactI18next).init({
  resources,
  lng: DEFAULT_LANGUAGE,
  fallbackLng: DEFAULT_LANGUAGE,
  supportedLngs: LANGUAGES.map(({ code }) => code),
  // React maskiert selbst, sonst stünde &amp; im Text
  interpolation: { escapeValue: false },
  returnNull: false,
})

/** Die Sprache der festen Texte umschalten, idempotent. Ruft der UsersSettingsProvider mit der Sprache der Oberfläche. */
export function applyUiLanguage(language: LanguageCode) {
  if (i18n.language !== language) {
    void i18n.changeLanguage(language)
  }
}

/**
 * Ein Text, der ein Schlüssel sein kann, aber nicht muss: z. B. das label eines origin, das noch nicht umgestellte
 * Module als deutschen Text mitgeben. Gibt es den Schlüssel, kommt die Übersetzung, sonst der Text selbst.
 */
export function translateLabel(label: string): string {
  // i18n.t erwartet einen typisierten Schlüssel, geprüft wird er hier mit exists
  return i18n.exists(label) ? i18n.t(label as never) : label
}

export default i18n
