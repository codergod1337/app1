import type { ReactNode } from 'react'
import { SortableArea, type SortableLayout } from './SortableArea.tsx'
import { useSortableItem } from './useSortableItem.tsx'

interface SortableListProps<T> {
  items: T[]
  itemKey: (item: T) => string
  /** untereinander (Standard), nebeneinander oder als Raster */
  layout?: SortableLayout
  /** nach dem Loslassen: die Elemente in neuer Reihenfolge, z. B. für positionsByKey */
  onReorder: (items: T[]) => void
  /** zeichnet ein Element, dragHandle ist der Griff zum Verschieben */
  renderItem: (item: T, dragHandle: ReactNode) => ReactNode
}

function SortableListItem<T>({ item, itemKey, renderItem }: { item: T } & Pick<SortableListProps<T>, 'itemKey' | 'renderItem'>) {
  const { ref, style, isDragging, dragHandle } = useSortableItem(itemKey(item))
  return (
    <div ref={ref} style={style} className={isDragging ? 'sortable-item dragging' : 'sortable-item'}>
      {renderItem(item, dragHandle)}
    </div>
  )
}

/**
 * Beliebige Elemente per Drag and Drop verschieben, z. B. Kacheln, Badges oder Karten. Für Tabellenzeilen hat die
 * Table dasselbe über onReorder eingebaut.
 */
export function SortableList<T>({ items, itemKey, layout = 'vertical', onReorder, renderItem }: SortableListProps<T>) {
  return (
    <SortableArea items={items} itemKey={itemKey} layout={layout} onReorder={onReorder}>
      <div className={`sortable-list sortable-list-${layout}`}>
        {items.map((item) => (
          <SortableListItem key={itemKey(item)} item={item} itemKey={itemKey} renderItem={renderItem} />
        ))}
      </div>
    </SortableArea>
  )
}
