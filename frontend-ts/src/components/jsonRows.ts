/** Ein Wert eines Zeilen-Objekts als Text; nichts bleibt leer, ein Objekt wird JSON */
export function jsonRowValueText(value: unknown): string {
  if (value === null || value === undefined) {
    return ''
  }
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}

/** Die Zeilen eines Tabellenfeldes: jeder Wert ein JSON-Text eines Objekts, unlesbare und andere fallen weg */
export function jsonRowsOf(values: readonly string[]): Record<string, unknown>[] {
  const rows: Record<string, unknown>[] = []
  for (const text of values) {
    try {
      const parsed: unknown = JSON.parse(text)
      if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
        rows.push(parsed as Record<string, unknown>)
      }
    } catch {
      // kein JSON: die Zeile ist nicht lesbar und fehlt
    }
  }
  return rows
}
