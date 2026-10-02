/**
 * Zählt die Positionen nach Drag and Drop neu durch (1, 2, 3 …) und liefert sie so, wie die Positions-Endpunkte sie
 * erwarten: jeder key mit seiner neuen Position, z. B. { ADMIN: 1, FILE_READ: 2 }. Alles in einem Request, nie einzeln.
 */
export function positionsByKey<T>(items: T[], itemKey: (item: T) => string): Record<string, number> {
  return Object.fromEntries(items.map((item, index) => [itemKey(item), index + 1]))
}
