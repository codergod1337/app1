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
  children: ReactNode
}

/**
 * Der Bereich, in dem Elemente per Drag and Drop verschoben werden. Zeichnet selbst nichts: die Elemente darin
 * nutzen useSortableItem. Grundlage für SortableList und die Table.
 */
export function SortableArea<T>({ items, itemKey, layout, onReorder, children }: SortableAreaProps<T>) {
  const sensors = useSensors(
    // erst ab 4 px Bewegung ziehen, ein einfacher Klick oder Tipp bleibt ein Klick
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )
  const keys = items.map(itemKey)

  function drop(event: DragEndEvent) {
    const { active, over } = event
    if (over === null || active.id === over.id) {
      return
    }
    onReorder(arrayMove(items, keys.indexOf(String(active.id)), keys.indexOf(String(over.id))))
  }

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={drop}>
      <SortableContext items={keys} strategy={STRATEGIES[layout]}>
        {children}
      </SortableContext>
    </DndContext>
  )
}
