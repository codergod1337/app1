/**
 * Der Name eines Listenelements im JSON-Baum, statt seiner Position (User 2026-10-10: nie table[2]). Eigene Datei,
 * weil der Baum ihn beim Zeichnen braucht und das Mapping beim Anspringen einer Adresse, die denselben Namen bilden muss.
 */

export function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/**
 * Die Schlüssel, die ein Listenelement benennen, in dieser Reihenfolge: erst am Element selbst, dann in seinen fields,
 * denn die Tabellenzeilen der ABAS-Antworten tragen ihre id dort
 */
const IDENTITY_KEYS = ['name', 'id', 'key', 'rel']

function identityText(candidate: unknown): string | null {
  if (typeof candidate === 'string' && candidate !== '') {
    return candidate
  }
  if (typeof candidate === 'number') {
    return String(candidate)
  }
  return null
}

/**
 * Der Name eines Listenelements: name=nummer, id=(610762,2,3), rel=self. Null, wenn nichts das Element benennt, dann
 * bleibt nur die Position.
 */
export function elementIdentity(element: unknown): string | null {
  if (!isPlainObject(element)) {
    return null
  }
  for (const key of IDENTITY_KEYS) {
    const text = identityText(element[key])
    if (text !== null) {
      return `${key}=${text}`
    }
  }
  const fields = element.fields
  if (isPlainObject(fields)) {
    for (const key of IDENTITY_KEYS) {
      const field = fields[key]
      const text = identityText(isPlainObject(field) ? field.value : field)
      if (text !== null) {
        return `${key}=${text}`
      }
    }
  }
  return null
}

/** Der Schlüssel eines Listenelements, wie der Baum ihn zeigt: sein Name, sonst seine Position */
export function elementKeyInTree(element: unknown, index: number): string {
  return elementIdentity(element) ?? String(index)
}
