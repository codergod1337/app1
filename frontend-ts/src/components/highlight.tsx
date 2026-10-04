import type { ReactNode } from 'react'

/**
 * Die Fundstellen des Suchtexts im Text als mark, wie im Treffergrund der Suche der Vorlage. needle klein geschrieben,
 * leer: der Text unverändert. Dieselbe Markierung in der Hook-Auswahl und in der Dateiart-Auswahl.
 */
export function highlight(text: string, needle: string): ReactNode {
  if (needle === '') {
    return text
  }
  const lower = text.toLowerCase()
  const pieces: ReactNode[] = []
  let start = 0
  let found = lower.indexOf(needle)
  while (found >= 0) {
    if (found > start) {
      pieces.push(text.slice(start, found))
    }
    pieces.push(<mark key={found}>{text.slice(found, found + needle.length)}</mark>)
    start = found + needle.length
    found = lower.indexOf(needle, start)
  }
  pieces.push(text.slice(start))
  return pieces
}
