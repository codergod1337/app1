import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import type { CSSProperties, ReactNode } from 'react'
import { Icon } from './Icon.tsx'

/**
 * Macht ein Element in einer SortableArea verschiebbar. Das Element bekommt ref und style, den Griff (dragHandle)
 * setzt es dorthin, wo er hingehört. Gezogen wird nur am Griff: mit Maus, Finger oder Tastatur (Leertaste, Pfeile).
 */
export function useSortableItem(key: string): {
  ref: (element: HTMLElement | null) => void
  style: CSSProperties
  isDragging: boolean
  dragHandle: ReactNode
} {
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } = useSortable({
    id: key,
  })

  return {
    ref: setNodeRef,
    style: {
      transform: CSS.Translate.toString(transform),
      transition,
      position: 'relative',
      zIndex: isDragging ? 1 : undefined,
    },
    isDragging,
    dragHandle: (
      <button
        type="button"
        ref={setActivatorNodeRef}
        className="drag-handle"
        aria-label="verschieben"
        // Der Griff löst nicht zusätzlich einen Klick auf das Element aus (z. B. das Detail-Popup einer Tabellenzeile)
        onClick={(event) => event.stopPropagation()}
        {...attributes}
        {...listeners}
      >
        <Icon name="drag" />
      </button>
    ),
  }
}
