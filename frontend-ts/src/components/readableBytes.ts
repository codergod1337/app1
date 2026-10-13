/**
 * Bytes lesbar: 1,2 MB statt 1234567. Für jede Stelle, die eine Größe zeigt (Infobox, Trefferzeile, Fortschritt eines
 * Downloads). Was keine Zahl ist, wird leer. Seit 2026-10-13 hier statt im DMS, weil auch allgemeine Komponenten es brauchen.
 */
export function readableBytes(value: unknown): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return ''
  }
  const units = ['B', 'kB', 'MB', 'GB']
  let amount = value
  let unit = 0
  while (amount >= 1000 && unit < units.length - 1) {
    amount /= 1000
    unit += 1
  }
  return `${unit === 0 ? amount : amount.toFixed(1)} ${units[unit]}`
}
