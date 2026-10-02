import type { PackSymbol } from './PackSymbol.ts'

/** CSS-Klasse der jeweiligen Symbol-Schrift */
function packIconClass(symbol: PackSymbol): string {
  switch (symbol.pack) {
    case 'bootstrap':
      return `bi bi-${symbol.id}`
    case 'tabler':
      return `ti ti-${symbol.id}`
    case 'lucide':
      return `icon-${symbol.id}`
  }
}

/** Ein beliebiges Symbol aus einem der Pakete. Für feste Symbole der Oberfläche gibt es Icon mit Namen. */
export function PackIcon({ symbol }: { symbol: PackSymbol }) {
  return <i className={packIconClass(symbol)} aria-hidden="true" />
}
