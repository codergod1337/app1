import type { ReactNode } from 'react'
import { Hoverlay } from './Hoverlay.tsx'
import { Icon } from './Icon.tsx'

interface DeleteButtonProps {
  label: string
  onClick: () => void
  disabled?: boolean
  /** optionaler Text neben dem Mülleimer, z. B. „Löschen“ in einer Sicherheitsabfrage */
  children?: ReactNode
}

/** Mülleimer zum Löschen. Aussehen im Theme, immer in Rottönen. */
export function DeleteButton({ label, onClick, disabled = false, children }: DeleteButtonProps) {
  return (
    <Hoverlay text={children ? null : label}>
      <button type="button" className="delete-button" aria-label={label} disabled={disabled} onClick={onClick}>
        <Icon name="delete" />
        {children}
      </button>
    </Hoverlay>
  )
}
