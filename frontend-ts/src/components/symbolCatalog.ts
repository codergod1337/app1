/*
 * Alle Symbolnamen je Paket, für die Auswahl. Groß (über 1 MB Text), deshalb nur per
 * import('./symbolCatalog.ts') laden: Vite legt die Datei dann in ein eigenes Stück, das erst bei Bedarf kommt.
 */
import bootstrapIconsJson from 'bootstrap-icons/font/bootstrap-icons.json?raw'
import lucideCodepointsJson from 'lucide-static/font/codepoints.json?raw'
import tablerIconsCss from '@tabler/icons-webfont/dist/tabler-icons.css?raw'
import type { SymbolPack } from './PackSymbol.ts'

export const SYMBOL_CATALOG: Record<SymbolPack, string[]> = {
  bootstrap: Object.keys(JSON.parse(bootstrapIconsJson) as Record<string, number>),
  // Tabler liefert keine Namensliste, die Namen stehen nur im CSS: .ti-<name>:before
  tabler: [...tablerIconsCss.matchAll(/\.ti-([a-z0-9-]+):before/g)].map((match) => match[1]),
  lucide: Object.keys(JSON.parse(lucideCodepointsJson) as Record<string, number>),
}
