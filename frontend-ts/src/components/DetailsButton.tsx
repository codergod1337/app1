import { Hoverlay } from './Hoverlay.tsx'
import { Icon } from './Icon.tsx'

/** Info zum Öffnen der Details, z. B. die Dokumentseite eines DMS-Dokuments. Aussehen im Theme, immer in Grüntönen. */
export function DetailsButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <Hoverlay text={label}>
      <button type="button" className="details-button" aria-label={label} onClick={onClick}>
        <Icon name="details" />
      </button>
    </Hoverlay>
  )
}
