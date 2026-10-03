import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Hoverlay } from './Hoverlay.tsx'
import { Icon } from './Icon.tsx'
import { Popup } from './Popup.tsx'
import { SortableArea } from './SortableArea.tsx'
import { useIsMobile } from './useIsMobile.ts'
import { useSortableItem } from './useSortableItem.tsx'

export interface TableColumn<T> {
  header: string
  /** Erklärung der Spalte im Hoverlay am Spaltenkopf, auch im Detail-Popup. Fehlt sie, steht der Kopf allein. */
  headerHint?: ReactNode
  cell: (row: T) => ReactNode
  /** unwichtig: in der mobilen Auflösung ausgeblendet, im Detail-Popup aber zu sehen */
  unimportant?: boolean
  /**
   * Feste Breite, z. B. 9rem. Sobald eine Spalte eine hat, rechnet die Tabelle mit festem Layout: Spalten ohne Breite
   * teilen sich den Rest. So stehen die Spalten mehrerer Tabellen untereinander genau übereinander.
   */
  width?: string
}

interface TableProps<T> {
  columns: TableColumn<T>[]
  rows: T[]
  rowKey: (row: T) => string
  /** Inhalt der Aktionsspalte ganz rechts, z. B. EditButton und DeleteButton */
  actions?: (row: T) => ReactNode
  /** Titel des Detail-Popups, z. B. die E-Mail des Users */
  detailTitle: (row: T) => ReactNode
  /** Text, wenn es keine Zeilen gibt */
  emptyText: string
  /** Macht die Zeilen per Drag and Drop verschiebbar (Griff ganz links). Bekommt die Zeilen in neuer Reihenfolge. */
  onReorder?: (rows: T[]) => void
  /**
   * Nur mit onReorder: Zeilen, die an ihrem Platz bleiben, z. B. id ganz oben oder die leere Zeile für einen neuen
   * Eintrag ganz unten. Sie bekommen keinen Griff, nur den Platz dafür, damit die Spalten weiter übereinander stehen.
   */
  fixedRow?: (row: T) => boolean
  /** Breite der Aktionsspalte beim festen Layout (siehe TableColumn.width). Standard: Platz für zwei Knöpfe. */
  actionsWidth?: string
}

/** Der Platz des Griffs in einer festen Zeile: gleiche Breite, unsichtbar */
const FIXED_ROW_HANDLE = (
  <span className="drag-handle drag-handle-fixed" aria-hidden="true">
    <Icon name="drag" />
  </span>
)

/** Was jede Zeile zum Zeichnen braucht */
interface RowProps<T> {
  row: T
  columns: TableColumn<T>[]
  actions?: (row: T) => ReactNode
  /** nur in der mobilen Auflösung: Tipp auf die Zeile öffnet die Details */
  onOpenDetails?: () => void
}

/**
 * Die Zellen einer Zeile. Mit dragHandle sitzt der Griff in der ersten Spalte: Griff linksbündig, Inhalt
 * rechtsbündig, die Spalte so schmal wie möglich.
 */
function RowCells<T>({ row, columns, actions, dragHandle }: RowProps<T> & { dragHandle?: ReactNode }) {
  return (
    <>
      {columns.map((column, index) =>
        dragHandle && index === 0 ? (
          <td key={column.header} className="drag-cell">
            <span className="drag-cell-inner">
              {dragHandle}
              <span>{column.cell(row)}</span>
            </span>
          </td>
        ) : (
          <td key={column.header}>{column.cell(row)}</td>
        ),
      )}
      {actions && (
        // Aktionen öffnen nicht zusätzlich das Detail-Popup
        <td className="actions-cell" onClick={(event) => event.stopPropagation()}>
          {actions(row)}
        </td>
      )}
    </>
  )
}

/** Zeile ohne Griff. In einer verschiebbaren Tabelle ist das eine feste Zeile, dann mit dem Platz für den Griff. */
function StaticRow<T>(props: RowProps<T> & { dragHandle?: ReactNode }) {
  return (
    <tr className={props.onOpenDetails ? 'details-trigger' : undefined} onClick={props.onOpenDetails}>
      <RowCells {...props} />
    </tr>
  )
}

/**
 * Zeile mit Griff in der ersten Spalte, daran lässt sie sich mit Maus, Finger oder Tastatur (Leertaste, Pfeile)
 * verschieben.
 */
function SortableRow<T>({ id, ...props }: RowProps<T> & { id: string }) {
  const { ref, style, isDragging, dragHandle } = useSortableItem(id)

  return (
    <tr
      ref={ref}
      style={style}
      className={[props.onOpenDetails ? 'details-trigger' : '', isDragging ? 'dragging' : ''].join(' ').trim() || undefined}
      onClick={props.onOpenDetails}
    >
      <RowCells {...props} dragHandle={dragHandle} />
    </tr>
  )
}

/**
 * Die Tabelle für alle Listen: Kopfzeile, Zeile A und Zeile B im Wechsel, ganz rechts die Aktionen.
 * In der mobilen Auflösung fallen unwichtige Spalten weg, ein Tipp auf die Zeile zeigt dann alle Spalten im Popup.
 * Mit onReorder lassen sich die Zeilen per Drag and Drop verschieben, feste Zeilen (fixedRow) bleiben dabei stehen.
 */
export function Table<T>({
  columns,
  rows,
  rowKey,
  actions,
  detailTitle,
  emptyText,
  onReorder,
  fixedRow,
  actionsWidth = '6rem',
}: TableProps<T>) {
  const { t } = useTranslation()
  const isMobile = useIsMobile()
  const [detailRow, setDetailRow] = useState<T | null>(null)
  const visibleColumns = isMobile ? columns.filter((column) => !column.unimportant) : columns
  const columnCount = visibleColumns.length + (actions ? 1 : 0)
  const fixedLayout = columns.some((column) => column.width !== undefined)

  const table = (
    <table className={`table table-striped align-middle mb-0 data-table${fixedLayout ? ' data-table-fixed' : ''}`}>
      {fixedLayout && (
        <colgroup>
          {visibleColumns.map((column) => (
            <col key={column.header} style={column.width !== undefined ? { width: column.width } : undefined} />
          ))}
          {actions && <col style={{ width: actionsWidth }} />}
        </colgroup>
      )}
      <thead>
        <tr>
          {/* Mit onReorder sitzt der Griff in der ersten sichtbaren Spalte */}
          {visibleColumns.map((column, index) => (
            <th key={column.header} className={onReorder && index === 0 ? 'drag-cell' : undefined}>
              {column.headerHint !== undefined ? (
                <Hoverlay text={column.headerHint}>
                  <span className="table-header-hinted">{column.header}</span>
                </Hoverlay>
              ) : (
                column.header
              )}
            </th>
          ))}
          {actions && <th className="actions-cell">{t('common.actions')}</th>}
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => {
          const rowProps: RowProps<T> = {
            row,
            columns: visibleColumns,
            actions,
            onOpenDetails: isMobile ? () => setDetailRow(row) : undefined,
          }
          if (onReorder && !fixedRow?.(row)) {
            return <SortableRow key={rowKey(row)} id={rowKey(row)} {...rowProps} />
          }
          return <StaticRow key={rowKey(row)} {...rowProps} dragHandle={onReorder ? FIXED_ROW_HANDLE : undefined} />
        })}
        {rows.length === 0 && (
          <tr>
            <td colSpan={columnCount}>{emptyText}</td>
          </tr>
        )}
      </tbody>
    </table>
  )

  return (
    <>
      {onReorder ? (
        <SortableArea items={rows} itemKey={rowKey} layout="vertical" onReorder={onReorder} fixed={fixedRow}>
          {table}
        </SortableArea>
      ) : (
        table
      )}
      <Popup
        open={detailRow !== null}
        title={detailRow !== null ? detailTitle(detailRow) : ''}
        footer={
          detailRow !== null && actions ? (
            // Nach einer Aktion (z. B. Löschen mit eigener Sicherheitsabfrage) schließt das Detail-Popup
            <span className="ms-auto" onClick={() => setDetailRow(null)}>
              {actions(detailRow)}
            </span>
          ) : undefined
        }
        onClose={() => setDetailRow(null)}
      >
        {detailRow !== null && (
          <dl className="details-list">
            {columns.map((column) => (
              <div key={column.header}>
                <dt>
                  {column.headerHint !== undefined ? (
                    <Hoverlay text={column.headerHint}>
                      <span className="table-header-hinted">{column.header}</span>
                    </Hoverlay>
                  ) : (
                    column.header
                  )}
                </dt>
                <dd>{column.cell(detailRow)}</dd>
              </div>
            ))}
          </dl>
        )}
      </Popup>
    </>
  )
}
