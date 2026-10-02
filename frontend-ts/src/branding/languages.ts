/**
 * Die aktivierten Sprachen, die erste ist der Standard. Für eine Firma mit anderen Sprachen wird nur diese Liste
 * angepasst. flag ist der Ländercode für flag-icons, countryName der Name des Landes in dessen Sprache.
 */
export const LANGUAGES = [
  { code: 'de', flag: 'de', countryName: 'Deutschland' },
  { code: 'en', flag: 'us', countryName: 'United States' },
  { code: 'bg', flag: 'bg', countryName: 'България' },
] as const

export type LanguageCode = (typeof LANGUAGES)[number]['code']

export const DEFAULT_LANGUAGE: LanguageCode = LANGUAGES[0].code
