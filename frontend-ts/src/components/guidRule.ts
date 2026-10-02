/*
 * Die guid-Regel, gleich wie im Backend (HelperInputs.isValidGuid):
 * 32 Hex-Zeichen im Format 8-4-4-4-12, hier immer klein. Beispiel: 3f2b8c1e-9a4d-4e7b-8c2f-1d5e6a7b8c9d
 */

export const GUID_RULE_TEXT = 'Format 8-4-4-4-12, nur 0–9 und a–f'

/** Hex-Zeichen einer guid, ohne Bindestriche */
export const GUID_HEX_LENGTH = 32

/** Nach so vielen Hex-Zeichen steht ein Bindestrich */
const DASH_POSITIONS = [8, 12, 16, 20]

const VALID_GUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

/** Volle guid 8-4-4-4-12, nur Kleinbuchstaben */
export function isValidGuid(guidText: string): boolean {
  return VALID_GUID.test(guidText)
}

/** Nur die Hex-Zeichen einer Eingabe: A–F wird a–f, alles andere fällt weg, höchstens 32 Zeichen */
export function toGuidHex(text: string): string {
  return text
    .toLowerCase()
    .replace(/[^0-9a-f]/g, '')
    .slice(0, GUID_HEX_LENGTH)
}

/**
 * Setzt die Bindestriche nach 8, 12, 16 und 20 Zeichen, aber nur, wenn danach noch etwas kommt.
 * So bleibt man beim Löschen nie an einem Bindestrich hängen.
 */
export function formatGuid(guidHex: string): string {
  let guidText = ''
  for (let index = 0; index < guidHex.length; index++) {
    if (DASH_POSITIONS.includes(index)) {
      guidText += '-'
    }
    guidText += guidHex[index]
  }
  return guidText
}

/**
 * true, wenn ein eingefügter Text nur aus Hex-Zeichen, Bindestrichen, Leerzeichen und {} besteht und nicht mehr
 * als 32 Hex-Zeichen hat. Alles andere ist keine guid und wird gar nicht erst eingefügt.
 */
export function isGuidPaste(pastedText: string): boolean {
  return (
    /^[0-9a-fA-F\s{}-]*$/.test(pastedText) && pastedText.replace(/[^0-9a-fA-F]/g, '').length <= GUID_HEX_LENGTH
  )
}
