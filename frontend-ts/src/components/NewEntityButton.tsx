import { Icon } from './Icon.tsx'

interface NewEntityButtonProps {
  /** Name der Entity, z. B. Users. Aufgeklappt steht dann „new Users()“. */
  entity: string
  onClick?: () => void
  /** 'submit', wenn er ein Formular abschickt */
  type?: 'button' | 'submit'
  disabled?: boolean
}

/**
 * Knopf zum Anlegen einer neuen Entity, steht immer rechts im contentbox-header.
 * Im Ruhezustand ein +, beim Hover klappt er auf und zeigt „new <Entity>()“.
 * Das Aussehen gestaltet das Theme komplett selbst (public/themes/<name>.css), basic.css enthält dafür nichts.
 */
export function NewEntityButton({ entity, onClick, type = 'button', disabled = false }: NewEntityButtonProps) {
  const text = `new ${entity}()`
  return (
    <button type={type} className="new-entity-button" onClick={onClick} disabled={disabled} aria-label={text}>
      <Icon name="add" />
      <span className="new-entity-button-text" aria-hidden="true">
        {text}
      </span>
    </button>
  )
}
