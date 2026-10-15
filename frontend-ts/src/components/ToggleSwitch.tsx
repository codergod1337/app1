interface ToggleSwitchProps {
  id: string
  checked: boolean
  onChange: (checked: boolean) => void
  /** was neben dem Schalter steht, wenn er an ist */
  labelOn: string
  /** was neben dem Schalter steht, wenn er aus ist */
  labelOff: string
  disabled?: boolean
}

/**
 * Ein Kippschalter (Bootstrap form-switch) mit einer Beschriftung je Stellung (User 2026-10-14): Neben dem Schalter
 * steht immer, was gerade gilt, nicht nur ein Name. Ohne Fachlogik, das Verhalten bestimmt der Aufrufer.
 */
export function ToggleSwitch({ id, checked, onChange, labelOn, labelOff, disabled = false }: ToggleSwitchProps) {
  return (
    <div className="form-check form-switch mb-0">
      <input
        id={id}
        className="form-check-input"
        type="checkbox"
        role="switch"
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
      />
      <label className="form-check-label" htmlFor={id}>
        {checked ? labelOn : labelOff}
      </label>
    </div>
  )
}
