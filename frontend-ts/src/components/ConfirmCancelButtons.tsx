import { CancelButton } from './CancelButton.tsx'
import { ConfirmButton } from './ConfirmButton.tsx'

interface ConfirmCancelButtonsProps {
  onCancel: () => void
  confirmLabel: string
  /** die id des Formulars, das Bestätigen abschickt */
  confirmForm?: string
  /** ohne Formular: was Bestätigen tut */
  onConfirm?: () => void
  /** nur Bestätigen gesperrt, etwa solange etwas fehlt */
  disabled?: boolean
  /** die Aktion läuft: Bestätigen mit Spinner, beide gesperrt */
  busy?: boolean
}

/**
 * Das Knopfpaar jeder Maske zum Tun (Bearbeiten, Anlegen, Hochladen, Archivieren), immer Abbrechen links und
 * Bestätigen rechts, rechtsbündig. Die Maske setzt es in den Kopf UND den Footer der ContentBox, damit man oben wie
 * unten nicht scrollen muss (User 2026-10-14); Popups nur in den Footer. Lese-Seiten haben kein Paar, nur das
 * „zurück zu …“ im Header.
 */
export function ConfirmCancelButtons({
  onCancel,
  confirmLabel,
  confirmForm,
  onConfirm,
  disabled = false,
  busy = false,
}: ConfirmCancelButtonsProps) {
  return (
    <span className="ms-auto d-flex gap-2">
      <CancelButton onClick={onCancel} disabled={busy} />
      <ConfirmButton label={confirmLabel} form={confirmForm} onClick={onConfirm} disabled={disabled} busy={busy} />
    </span>
  )
}
