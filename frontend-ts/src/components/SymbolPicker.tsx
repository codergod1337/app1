import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { PackIcon } from './PackIcon.tsx'
import type { PackSymbol } from './PackSymbol.ts'
import { SymbolPopup } from './SymbolPopup.tsx'

interface SymbolPickerProps {
  /** null: kein Symbol */
  value: PackSymbol | null
  onChange: (symbol: PackSymbol | null) => void
  disabled?: boolean
}

/** Zeigt das gewählte Symbol. „Symbol wählen“ öffnet das Popup mit allen Symbolen. */
export function SymbolPicker({ value, onChange, disabled = false }: SymbolPickerProps) {
  const { t } = useTranslation()
  const [popupOpen, setPopupOpen] = useState(false)

  return (
    <div className="d-flex align-items-center gap-2">
      <span className="symbol-picker-current">{value ? <PackIcon symbol={value} /> : '–'}</span>
      <code>{value ? `${value.pack}: ${value.id}` : t('common.noSymbol')}</code>
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-other" disabled={disabled} onClick={() => setPopupOpen(true)}>
          {t('common.chooseSymbol')}
        </button>
        <button type="button" className="button-other" disabled={disabled || value === null} onClick={() => onChange(null)}>
          {t('common.noSymbol')}
        </button>
      </span>
      <SymbolPopup
        open={popupOpen}
        value={value}
        onSelect={(symbol) => {
          onChange(symbol)
          setPopupOpen(false)
        }}
        onClose={() => setPopupOpen(false)}
      />
    </div>
  )
}
