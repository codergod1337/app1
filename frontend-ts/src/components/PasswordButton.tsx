import { Hoverlay } from './Hoverlay.tsx'
import { Icon } from './Icon.tsx'

/** Schlüssel zum Setzen eines Passworts, z. B. in der Aktionsspalte einer Tabelle. Aussehen komplett im Theme. */
export function PasswordButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <Hoverlay text={label}>
      <button type="button" className="password-button" aria-label={label} onClick={onClick}>
        <Icon name="password" />
      </button>
    </Hoverlay>
  )
}
