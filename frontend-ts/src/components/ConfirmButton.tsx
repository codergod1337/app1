import { Icon } from './Icon.tsx'

interface ConfirmButtonProps {
  /** was der Knopf tut: Speichern, Anlegen, Hochladen … */
  label: string
  /** die id des Formulars, das er abschickt; dann ist er ein submit und braucht kein onClick */
  form?: string
  onClick?: () => void
  disabled?: boolean
  /** die Aktion läuft: Spinner statt Haken, gesperrt */
  busy?: boolean
}

/**
 * Bestätigen: immer derselbe Knopf mit ✓ (User 2026-10-14). Über form schickt er ein Formular ab, auch von außerhalb,
 * etwa aus dem Kopf der ContentBox. Aussehen im Theme, button-save, grün.
 */
export function ConfirmButton({ label, form, onClick, disabled = false, busy = false }: ConfirmButtonProps) {
  return (
    <button
      type={form !== undefined ? 'submit' : 'button'}
      form={form}
      className="button-save"
      disabled={disabled || busy}
      onClick={onClick}
    >
      {busy ? <span className="spinner-border spinner-border-sm" aria-hidden="true" /> : <Icon name="check" />} {label}
    </button>
  )
}
