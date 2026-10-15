import { Hoverlay } from './Hoverlay.tsx'
import { Icon } from './Icon.tsx'

/** Neue Fassung einchecken, z. B. neue Bytes zu einem DMS-Dokument. Aussehen im Theme, immer in Grautönen. */
export function NewVersionButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <Hoverlay text={label}>
      <button type="button" className="new-version-button" aria-label={label} onClick={onClick}>
        <Icon name="newVersion" />
      </button>
    </Hoverlay>
  )
}
