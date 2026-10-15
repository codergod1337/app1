import { Hoverlay } from './Hoverlay.tsx'
import { Icon } from './Icon.tsx'

/** Auge zum Ansehen, z. B. eine Datei im Viewer. Aussehen im Theme, immer in Blautönen. */
export function ViewButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <Hoverlay text={label}>
      <button type="button" className="view-button" aria-label={label} onClick={onClick}>
        <Icon name="view" />
      </button>
    </Hoverlay>
  )
}
