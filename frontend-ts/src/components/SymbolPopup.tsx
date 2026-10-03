import { useDeferredValue, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Hoverlay } from './Hoverlay.tsx'
import { PackIcon } from './PackIcon.tsx'
import { SYMBOL_PACKS, type PackSymbol, type SymbolPack } from './PackSymbol.ts'
import { Popup } from './Popup.tsx'

interface SymbolPopupProps {
  open: boolean
  /** das gerade gewählte Symbol, wird hervorgehoben */
  value: PackSymbol | null
  onSelect: (symbol: PackSymbol) => void
  onClose: () => void
}

/** Alle Symbole aller Pakete zum Durchscrollen. Das Suchfeld blendet alles aus, was nicht passt. */
export function SymbolPopup({ open, value, onSelect, onClose }: SymbolPopupProps) {
  const { t } = useTranslation()
  const [catalog, setCatalog] = useState<Record<SymbolPack, string[]> | null>(null)
  const [packFilter, setPackFilter] = useState<SymbolPack | 'all'>('all')
  const [search, setSearch] = useState('')
  // Tippen bleibt flüssig, das Raster mit tausenden Symbolen zieht kurz danach nach
  const deferredSearch = useDeferredValue(search)

  // Die Namensliste ist groß und kommt erst, wenn das Popup das erste Mal aufgeht
  useEffect(() => {
    if (!open || catalog !== null) {
      return
    }
    let active = true
    import('./symbolCatalog.ts').then((module) => {
      if (active) {
        setCatalog(module.SYMBOL_CATALOG)
      }
    })
    return () => {
      active = false
    }
  }, [open, catalog])

  const searchText = deferredSearch.trim().toLowerCase()
  const symbols: PackSymbol[] = []
  if (catalog !== null) {
    for (const { pack } of SYMBOL_PACKS) {
      if (packFilter !== 'all' && packFilter !== pack) {
        continue
      }
      for (const id of catalog[pack]) {
        if (id.includes(searchText)) {
          symbols.push({ pack, id })
        }
      }
    }
  }

  const footer =
    catalog === null ? (
      <span>{t('common.symbolsLoading')}</span>
    ) : (
      <span>{t('common.symbolsCount', { count: symbols.length })}</span>
    )

  return (
    <Popup open={open} title={t('common.chooseSymbol')} footer={footer} onClose={onClose}>
      <div className="symbol-popup-search">
        <select
          className="form-select w-auto"
          value={packFilter}
          onChange={(event) => setPackFilter(event.target.value as SymbolPack | 'all')}
        >
          <option value="all">{t('common.allPacks')}</option>
          {SYMBOL_PACKS.map(({ pack, label }) => (
            <option key={pack} value={pack}>
              {label}
            </option>
          ))}
        </select>
        <input
          className="form-control"
          placeholder={t('common.symbolSearchPlaceholder')}
          autoFocus
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>
      <div className="symbol-popup-grid">
        {symbols.map((symbol) => (
          <Hoverlay key={`${symbol.pack}:${symbol.id}`} text={`${symbol.pack}: ${symbol.id}`}>
            <button
              type="button"
              className={
                value?.pack === symbol.pack && value.id === symbol.id ? 'symbol-popup-item active' : 'symbol-popup-item'
              }
              aria-label={`${symbol.pack}: ${symbol.id}`}
              onClick={() => onSelect(symbol)}
            >
              <PackIcon symbol={symbol} />
            </button>
          </Hoverlay>
        ))}
      </div>
    </Popup>
  )
}
