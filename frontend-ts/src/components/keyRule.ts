/*
 * Die Key-Regel, gleich wie im Backend (HelperInputs.isValidKey):
 * nur A–Z, 0–9 und _, beginnt und endet mit einem Großbuchstaben, nie zwei _ hintereinander, höchstens 200 Zeichen.
 * Beispiele: ADMIN, FILE_READ, LEVEL_2_USER
 */

export const KEY_MAX_LENGTH = 200

/** Für das pattern-Attribut von input */
export const KEY_PATTERN = '(?!.*__)[A-Z]([A-Z0-9_]*[A-Z])?'

export const KEY_RULE_TEXT = 'nur A–Z, 0–9 und _, beginnt und endet mit einem Großbuchstaben, nie zwei _ hintereinander'

const VALID_KEY = new RegExp(`^${KEY_PATTERN}$`)

export function isValidKey(keyText: string): boolean {
  return keyText.length <= KEY_MAX_LENGTH && VALID_KEY.test(keyText)
}

/**
 * Macht aus einer Eingabe, was ein Key enthalten darf, schon während des Tippens:
 * Großbuchstaben, Umlaute ausgeschrieben (ä → AE), Leerzeichen und - werden zu _, alles andere fällt weg,
 * kein doppeltes _, vorne keine Ziffer und kein _.
 * Am Ende darf beim Tippen noch ein _ oder eine Ziffer stehen (FILE_ → FILE_READ), das fängt erst isValidKey ab.
 */
export function toKeyCharacters(text: string): string {
  return text
    .toUpperCase()
    .replace(/Ä/g, 'AE')
    .replace(/Ö/g, 'OE')
    .replace(/Ü/g, 'UE')
    .replace(/[\s-]/g, '_')
    .replace(/[^A-Z0-9_]/g, '')
    .replace(/_{2,}/g, '_')
    .replace(/^[0-9_]+/, '')
    .slice(0, KEY_MAX_LENGTH)
}
