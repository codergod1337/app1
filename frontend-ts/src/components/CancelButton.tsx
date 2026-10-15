import { useTranslation } from 'react-i18next'
import { Icon } from './Icon.tsx'

interface CancelButtonProps {
  onClick: () => void
  disabled?: boolean
  /** ohne Angabe „Abbrechen“ aus dem Sprachkatalog, im Popup „Schließen“ */
  label?: string
}

/**
 * Abbrechen: immer derselbe Knopf mit ✕, damit man ihn überall sofort erkennt (User 2026-10-14). In einer Maske zum Tun
 * steht er im Paar mit dem Bestätigen (ConfirmCancelButtons), im Popup oben als Schließen. Aussehen im Theme,
 * button-cancel, anthrazit wie das „zurück zu …“ im Header.
 */
export function CancelButton({ onClick, disabled = false, label }: CancelButtonProps) {
  const { t } = useTranslation()
  return (
    <button type="button" className="button-cancel" disabled={disabled} onClick={onClick}>
      <Icon name="close" /> {label ?? t('common.cancel')}
    </button>
  )
}
