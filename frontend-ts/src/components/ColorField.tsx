import { Hoverlay } from './Hoverlay.tsx'

/** Farben zum Anklicken, darunter Gold, Silber und Bronze für Badges. */
const PALETTE = [
  '#000000',
  '#ffffff',
  '#64748b',
  '#1e293b',
  '#ef4444',
  '#f97316',
  '#eab308',
  '#22c55e',
  '#14b8a6',
  '#0ea5e9',
  '#6366f1',
  '#a855f7',
  '#ec4899',
  '#d4af37',
  '#c0c0c0',
  '#cd7f32',
]

interface ColorFieldProps {
  id: string
  label: string
  /** immer Hex (#rrggbb), null: Standard */
  value: string | null
  onChange: (color: string | null) => void
  disabled?: boolean
}

/** Farbe wählen: Farbwähler (liefert immer Hex), Palette zum Anklicken und zurück auf den Standard. */
export function ColorField({ id, label, value, onChange, disabled = false }: ColorFieldProps) {
  return (
    <div className="color-field">
      <label className="form-label" htmlFor={id}>
        {label}
      </label>
      <div className="d-flex align-items-center gap-2">
        <input
          id={id}
          className="form-control form-control-color"
          type="color"
          value={value ?? '#000000'}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
        />
        <code>{value ?? 'Standard'}</code>
        <button type="button" className="button-other ms-auto" disabled={disabled || value === null} onClick={() => onChange(null)}>
          Standard
        </button>
      </div>
      <div className="color-field-palette">
        {PALETTE.map((color) => (
          <Hoverlay key={color} text={color}>
            <button
              type="button"
              className={color === value ? 'color-field-swatch active' : 'color-field-swatch'}
              style={{ background: color }}
              aria-label={color}
              disabled={disabled}
              onClick={() => onChange(color)}
            />
          </Hoverlay>
        ))}
      </div>
    </div>
  )
}
