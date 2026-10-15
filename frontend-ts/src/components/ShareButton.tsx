import { Hoverlay } from './Hoverlay.tsx'
import { Icon } from './Icon.tsx'

/** Freigeben, z. B. das Popup für den Abholschein. Aussehen im Theme, immer in Violetttönen. */
export function ShareButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <Hoverlay text={label}>
      <button type="button" className="share-button" aria-label={label} onClick={onClick}>
        <Icon name="share" />
      </button>
    </Hoverlay>
  )
}
