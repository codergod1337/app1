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

/**
 * Die Fundstellen mehrerer Wörter im Text als mark, für den Treffergrund der Suche: jedes Wort klein, leere fallen
 * weg, Wörter, die sich überschneiden, markieren zusammen eine Stelle. Ohne Wörter der Text unverändert.
 */
export function highlightWords(text: string, needles: readonly string[]): ReactNode {
  const words = needles.map((needle) => needle.toLowerCase()).filter((needle) => needle !== '')
  if (words.length === 0 || text === '') {
    return text
  }
  const lower = text.toLowerCase()
  const marked = new Array<boolean>(text.length).fill(false)
  for (const word of words) {
    let found = lower.indexOf(word)
    while (found >= 0) {
      marked.fill(true, found, found + word.length)
      found = lower.indexOf(word, found + word.length)
    }
  }
  const pieces: ReactNode[] = []
  let start = 0
  while (start < text.length) {
    let end = start
    while (end < text.length && marked[end] === marked[start]) {
      end += 1
    }
    const piece = text.slice(start, end)
    pieces.push(marked[start] ? <mark key={start}>{piece}</mark> : piece)
    start = end
  }
  return pieces
}
