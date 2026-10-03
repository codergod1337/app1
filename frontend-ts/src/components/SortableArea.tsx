import {
  closestCenter,
  DndContext,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core'
import {
  arrayMove,
  horizontalListSortingStrategy,
  rectSortingStrategy,
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import type { ReactNode } from 'react'

/** Wie die Elemente liegen: untereinander, nebeneinander oder als Raster */
export type SortableLayout = 'vertical' | 'horizontal' | 'grid'

const STRATEGIES = {
  vertical: verticalListSortingStrategy,
  horizontal: horizontalListSortingStrategy,
  grid: rectSortingStrategy,
}

interface SortableAreaProps<T> {
  items: T[]
  itemKey: (item: T) => string
  layout: SortableLayout
  /** nach dem Loslassen: die Elemente in neuer Reihenfolge */
  onReorder: (items: T[]) => void
  /**
   * Elemente, die an ihrem Platz bleiben, z. B. id ganz oben in einer Feldtabelle. Sie lassen sich nicht ziehen und
   * nichts lässt sich auf sie ziehen; die anderen tauschen nur die übrigen Plätze untereinander. Solche Elemente
   * rufen useSortableItem nicht auf.
   */
  fixed?: (item: T) => boolean
  children: ReactNode
}

/**
 * Der Bereich, in dem Elemente per Drag and Drop verschoben werden. Zeichnet selbst nichts: die Elemente darin
 * nutzen useSortableItem. Grundlage für SortableList und die Table.
 */
export function SortableArea<T>({ items, itemKey, layout, onReorder, fixed, children }: SortableAreaProps<T>) {
  const sensors = useSensors(
    // erst ab 4 px Bewegung ziehen, ein einfacher Klick oder Tipp bleibt ein Klick
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )
  const isFixed = (item: T) => fixed?.(item) ?? false
  const movable = items.filter((item) => !isFixed(item))
  const keys = movable.map(itemKey)

  function drop(event: DragEndEvent) {
    const { active, over } = event
    if (over === null || active.id === over.id) {
      return
    }
    const moved = arrayMove(movable, keys.indexOf(String(active.id)), keys.indexOf(String(over.id)))
    // Feste Elemente bleiben, wo sie sind, die verschiebbaren füllen die übrigen Plätze in neuer Reihenfolge
    let next = 0
    onReorder(
      items.map((item) => {
        if (isFixed(item)) {
          return item
        }
        const movedItem = moved[next]
        next += 1
        return movedItem
      }),
    )
  }

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={drop}>
      <SortableContext items={keys} strategy={STRATEGIES[layout]}>
        {children}
      </SortableContext>
    </DndContext>
  )
}
