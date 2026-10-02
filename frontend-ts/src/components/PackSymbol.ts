/** Symbol-Pakete, alle frei verwendbar (MIT oder ISC). */
export type SymbolPack = 'bootstrap' | 'tabler' | 'lucide'

export const SYMBOL_PACKS: { pack: SymbolPack; label: string }[] = [
  { pack: 'bootstrap', label: 'Bootstrap Icons' },
  { pack: 'tabler', label: 'Tabler Icons' },
  { pack: 'lucide', label: 'Lucide' },
]

/** Ein Symbol aus einem Paket, z. B. { pack: 'tabler', id: 'shield-lock' }. So wird es auch im Backend gespeichert. */
export interface PackSymbol {
  pack: SymbolPack
  id: string
}
