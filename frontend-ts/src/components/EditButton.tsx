import { Hoverlay } from './Hoverlay.tsx'
import { Icon } from './Icon.tsx'

/** Bleistift zum Bearbeiten, z. B. in der Aktionsspalte einer Tabelle. Aussehen im Theme, immer in Gelb- und Brauntönen. */
export function EditButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <Hoverlay text={label}>
      <button type="button" className="edit-button" aria-label={label} onClick={onClick}>
        <Icon name="edit" />
      </button>
    </Hoverlay>
  )
}
