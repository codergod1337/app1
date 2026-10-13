interface IntegerInputProps {
  id?: string
  value: number
  min: number
  max: number
  onChange: (value: number) => void
  disabled?: boolean
  /** die Beschriftung für Screenreader, wenn kein Label am Feld hängt */
  ariaLabel?: string
  /** zusätzliche Klassen, z. B. w-auto in einer Kopfzeile */
  className?: string
}

/**
 * Eine ganze Zahl zwischen min und max, ohne Fachlogik: ein Bedienelement für jedes Modul, das eine Tiefe, eine Anzahl
 * oder eine Grenze erfragt. Was außerhalb liegt, wird auf die Grenze gezogen, keine Zahl ändert nichts.
 */
export function IntegerInput({ id, value, min, max, onChange, disabled = false, ariaLabel, className = '' }: IntegerInputProps) {
  return (
    <input
      id={id}
      type="number"
      inputMode="numeric"
      className={`form-control form-control-sm ${className}`.trim()}
      min={min}
      max={max}
      step={1}
      value={value}
      disabled={disabled}
      aria-label={ariaLabel}
      onChange={(event) => {
        const parsed = Number.parseInt(event.target.value, 10)
        if (Number.isNaN(parsed)) {
          return
        }
        onChange(Math.min(max, Math.max(min, parsed)))
      }}
    />
  )
}
