import { useState, type ReactNode } from 'react'
import { Popup } from './Popup.tsx'

interface NumberPickerProps {
  id: string
  value: number
  min: number
  max: number
  /** bei jedem Schiebeschritt und jedem Tippen, schon auf min bis max begrenzt */
  onChange: (value: number) => void
  /** beim Schließen des Popups, etwa zum Speichern */
  onClose?: () => void
  /** Titel des Popups */
  title: ReactNode
  /** Beschriftung von Knopf, Regler und Zahlenfeld für Screenreader */
  label: string
  /** die Zahl auf dem Knopf anders zeigen, etwa eine 0 grau mit Hoverlay; ohne: die Zahl selbst */
  renderValue?: (value: number) => ReactNode
  disabled?: boolean
}

/**
 * Eine ganze Zahl in einem Bereich: in der Fläche nur die Zahl als Knopf, Klick öffnet ein Popup mit Schieberegler und
 * Zahlenfeld, beide am selben Wert. Getippte Werte werden auf min bis max begrenzt und gerundet, Enter im Zahlenfeld
 * schließt. Nichts Fachliches: Bereich, Titel und Beschriftung kommen vom Aufrufer.
 */
export function NumberPicker({
  id,
  value,
  min,
  max,
  onChange,
  onClose,
  title,
  label,
  renderValue,
  disabled = false,
}: NumberPickerProps) {
  const [open, setOpen] = useState(false)

  function close() {
    setOpen(false)
    onClose?.()
  }

  function changeTo(raw: string) {
    const number = Number(raw)
    if (Number.isNaN(number)) {
      return
    }
    onChange(Math.min(max, Math.max(min, Math.round(number))))
  }

  return (
    <>
      <button
        type="button"
        id={id}
        // nowrap: drei Ziffern bleiben in einer Zeile, auch in einer schmalen Spalte
        className="button-other text-nowrap"
        aria-label={label}
        disabled={disabled}
        onClick={() => setOpen(true)}
      >
        {renderValue ? renderValue(value) : value}
      </button>
      <Popup open={open} title={title} narrow onClose={close}>
        <div className="d-flex align-items-center gap-3">
          <input
            id={`${id}-range`}
            className="form-range"
            type="range"
            min={min}
            max={max}
            step={1}
            aria-label={label}
            value={value}
            onChange={(event) => changeTo(event.target.value)}
          />
          <input
            id={`${id}-number`}
            className="form-control form-control-sm"
            style={{ width: '5rem' }}
            type="number"
            min={min}
            max={max}
            aria-label={label}
            value={value}
            onChange={(event) => changeTo(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault()
                close()
              }
            }}
          />
        </div>
      </Popup>
    </>
  )
}
