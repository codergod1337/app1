import { useEffect, useRef, type MouseEvent, type PointerEvent, type ReactNode } from 'react'
import { usePanScroll } from './usePanScroll.ts'

/** Abstand in Pixeln, in dem beim schnellen Wischen zwischen zwei Mauspositionen nachgesehen wird */
const PAINT_STEP = 6

interface MatrixProps<R, C> {
  rows: R[]
  columns: C[]
  rowKey: (row: R) => string
  columnKey: (column: C) => string
  /** links, bleibt beim seitlichen Scrollen stehen */
  rowHeader: (row: R) => ReactNode
  /** oben, senkrecht gedreht, bleibt beim Scrollen nach unten stehen */
  columnHeader: (column: C) => ReactNode
  /** Beschriftung der Ecke: was in den Zeilen steht, z. B. User */
  rowsLabel: string
  /** Beschriftung der Ecke: was in den Spalten steht, z. B. AccessRoleCollection */
  columnsLabel: string
  /** zusätzlich oben links in der Ecke, z. B. die Auswahl des Pinsels */
  cornerContent?: ReactNode
  /** gesetzt: ×, sonst ·. Für Zeilen mit eigenem Inhalt (cellContent) egal. */
  isMarked: (row: R, column: C) => boolean
  /** eigener Inhalt einer Zelle, z. B. ein Zähler in einer Gruppenzeile. undefined: × oder · nach isMarked. */
  cellContent?: (row: R, column: C) => ReactNode | undefined
  /** eigene Klasse einer Zeile, z. B. für Gruppenzeilen */
  rowClassName?: (row: R) => string | undefined
  /** eigene Klasse einer Zelle, z. B. matrix-cell-pending, solange sie nicht gespeichert ist */
  cellClassName?: (row: R, column: C) => string | undefined
  /** für Screenreader, z. B. „ARC_LAGER für Paul umschalten“ */
  cellLabel: (row: R, column: C) => string
  /** ein Klick: mit der Maus Drücken und Loslassen in derselben Zelle, oder Tastatur, oder Finger */
  onCellClick: (row: R, column: C) => void
  /**
   * Malen: Wer die linke Maustaste gedrückt hält und dabei die Startzelle verlässt, malt. Für jede überstrichene Zelle
   * einmal, die Startzelle eingeschlossen. Fehlt es, gibt es nur Klicks.
   */
  onCellPaint?: (row: R, column: C) => void
  /** nach dem Loslassen eines Malstrichs, z. B. zum Speichern */
  onPaintEnd?: () => void
  /** sperrt alle Zellen, z. B. während gespeichert wird */
  disabled?: boolean
}

/** Eine Zelle über ihre Stelle in rows und columns */
interface CellIndex {
  rowIndex: number
  columnIndex: number
}

/** Ein laufender Malstrich mit der Maus */
interface PaintStroke {
  start: CellIndex
  lastX: number
  lastY: number
  /** erst wahr, sobald die Startzelle verlassen wurde. Bis dahin ist es ein Klick. */
  painting: boolean
  painted: Set<string>
}

/**
 * Die Matrix für alle Zuordnungen „Zeile gegen Spalte“, steht ohne ContentBox direkt im main (siehe Admin).
 * Kopfzeile und erste Spalte bleiben stehen, die Ecke sagt diagonal geteilt, was gegen was steht. Jede Zelle ist
 * ganz der Knopf, beim Zeigen färben sich Zeile und Spalte leicht mit (Fadenkreuz).
 * Gescrollt wird in sich: Mausrad, Finger oder mit gedrückter rechter Maustaste. Mit onCellPaint lässt sich mit
 * gedrückter linker Maustaste über die Zellen malen, ohne dass etwas stockt: der Aufrufer sammelt und speichert erst
 * beim Loslassen.
 */
export function Matrix<R, C>({
  rows,
  columns,
  rowKey,
  columnKey,
  rowHeader,
  columnHeader,
  rowsLabel,
  columnsLabel,
  cornerContent,
  isMarked,
  cellContent,
  rowClassName,
  cellClassName,
  cellLabel,
  onCellClick,
  onCellPaint,
  onPaintEnd,
  disabled = false,
}: MatrixProps<R, C>) {
  const { ref: scrollRef, handlers: panHandlers } = usePanScroll<HTMLDivElement>()
  const strokeRef = useRef<PaintStroke | null>(null)
  const lastPointerTypeRef = useRef<string>('mouse')
  // Die Handler am window gelten für einen ganzen Malstrich: Sie lesen die jeweils neuesten Werte hieraus
  const latest = useRef({ rows, columns, onCellClick, onCellPaint, onPaintEnd })
  useEffect(() => {
    latest.current = { rows, columns, onCellClick, onCellPaint, onPaintEnd }
  })

  /** Die Zelle unter einem Punkt des Bildschirms, null außerhalb der Matrix */
  function cellAt(x: number, y: number): CellIndex | null {
    const cell = document.elementFromPoint(x, y)?.closest<HTMLElement>('[data-matrix-cell]')
    if (!cell || !scrollRef.current?.contains(cell)) {
      return null
    }
    return { rowIndex: Number(cell.dataset.rowIndex), columnIndex: Number(cell.dataset.columnIndex) }
  }

  function paint(stroke: PaintStroke, cell: CellIndex) {
    const id = `${cell.rowIndex}:${cell.columnIndex}`
    if (stroke.painted.has(id)) {
      return
    }
    stroke.painted.add(id)
    const { rows: currentRows, columns: currentColumns, onCellPaint: paintCell } = latest.current
    const row = currentRows[cell.rowIndex]
    const column = currentColumns[cell.columnIndex]
    if (row !== undefined && column !== undefined) {
      paintCell?.(row, column)
    }
  }

  /** Alle Zellen auf der Strecke seit der letzten Position, damit beim schnellen Wischen keine übersprungen wird */
  function paintTo(stroke: PaintStroke, x: number, y: number) {
    const steps = Math.max(1, Math.ceil(Math.hypot(x - stroke.lastX, y - stroke.lastY) / PAINT_STEP))
    for (let step = 1; step <= steps; step++) {
      const cell = cellAt(
        stroke.lastX + ((x - stroke.lastX) * step) / steps,
        stroke.lastY + ((y - stroke.lastY) * step) / steps,
      )
      if (cell === null) {
        continue
      }
      if (!stroke.painting) {
        if (cell.rowIndex === stroke.start.rowIndex && cell.columnIndex === stroke.start.columnIndex) {
          continue
        }
        // Startzelle verlassen: ab jetzt wird gemalt, die Startzelle zuerst
        stroke.painting = true
        scrollRef.current?.classList.add('painting')
        paint(stroke, stroke.start)
      }
      paint(stroke, cell)
    }
    stroke.lastX = x
    stroke.lastY = y
  }

  function movePaint(event: globalThis.PointerEvent) {
    const stroke = strokeRef.current
    if (!stroke) {
      return
    }
    if ((event.buttons & 1) === 0) {
      endPaint(true)
      return
    }
    paintTo(stroke, event.clientX, event.clientY)
  }

  function upPaint() {
    endPaint(true)
  }

  function cancelPaint() {
    endPaint(false)
  }

  /** Ende eines Strichs. Wurde nicht gemalt und regulär losgelassen, war es ein Klick auf die Startzelle. */
  function endPaint(released: boolean) {
    const stroke = strokeRef.current
    window.removeEventListener('pointermove', movePaint)
    window.removeEventListener('pointerup', upPaint)
    window.removeEventListener('pointercancel', cancelPaint)
    strokeRef.current = null
    scrollRef.current?.classList.remove('painting')
    if (!stroke) {
      return
    }
    const { rows: currentRows, columns: currentColumns, onCellClick: clickCell, onPaintEnd: paintEnd } = latest.current
    if (stroke.painting) {
      paintEnd?.()
      return
    }
    const row = currentRows[stroke.start.rowIndex]
    const column = currentColumns[stroke.start.columnIndex]
    if (released && row !== undefined && column !== undefined) {
      clickCell(row, column)
    }
  }

  function startPaint(event: PointerEvent<HTMLDivElement>) {
    lastPointerTypeRef.current = event.pointerType
    // nur mit der linken Maustaste oder einem Stift, der Finger scrollt
    if (!onCellPaint || disabled || event.button !== 0 || event.pointerType === 'touch') {
      return
    }
    const cell = (event.target as HTMLElement).closest<HTMLElement>('[data-matrix-cell]')
    if (!cell) {
      return
    }
    // sonst markiert der Browser beim Ziehen Text
    event.preventDefault()
    strokeRef.current = {
      start: { rowIndex: Number(cell.dataset.rowIndex), columnIndex: Number(cell.dataset.columnIndex) },
      lastX: event.clientX,
      lastY: event.clientY,
      painting: false,
      painted: new Set(),
    }
    window.addEventListener('pointermove', movePaint)
    window.addEventListener('pointerup', upPaint)
    window.addEventListener('pointercancel', cancelPaint)
  }

  /** Der Klick des Knopfes. Beim Malen mit der Maus erledigt das schon endPaint, sonst käme er doppelt. */
  function clickCell(event: MouseEvent<HTMLButtonElement>, row: R, column: C) {
    // detail 0: per Tastatur ausgelöst
    const handledByStroke = onCellPaint !== undefined && event.detail !== 0 && lastPointerTypeRef.current !== 'touch'
    if (!handledByStroke) {
      onCellClick(row, column)
    }
  }

  /** Der eigene Inhalt, sonst × oder · */
  function content(row: R, column: C) {
    const ownContent = cellContent?.(row, column)
    if (ownContent !== undefined) {
      return ownContent
    }
    return isMarked(row, column) ? <span className="matrix-mark">×</span> : <span className="matrix-empty">·</span>
  }

  return (
    <div
      ref={scrollRef}
      className="matrix-scroll"
      {...panHandlers}
      onPointerDown={(event) => {
        panHandlers.onPointerDown(event)
        startPaint(event)
      }}
    >
      <table className="matrix">
        <thead>
          <tr>
            <th className="matrix-corner">
              {cornerContent !== undefined && <div className="matrix-corner-tools">{cornerContent}</div>}
              <span className="matrix-corner-rows">{rowsLabel}</span>
              <span className="matrix-corner-columns">{columnsLabel}</span>
            </th>
            {columns.map((column) => (
              <th key={columnKey(column)} className="matrix-column-head">
                <span className="matrix-column-head-content">{columnHeader(column)}</span>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, rowIndex) => (
            <tr key={rowKey(row)} className={rowClassName?.(row)}>
              <th scope="row" className="matrix-row-head">
                {rowHeader(row)}
              </th>
              {columns.map((column, columnIndex) => (
                <td
                  key={columnKey(column)}
                  className={['matrix-cell', cellClassName?.(row, column)].filter(Boolean).join(' ')}
                  data-matrix-cell=""
                  data-row-index={rowIndex}
                  data-column-index={columnIndex}
                >
                  <button
                    type="button"
                    className="matrix-cell-button"
                    disabled={disabled}
                    aria-label={cellLabel(row, column)}
                    aria-pressed={isMarked(row, column)}
                    onClick={(event) => clickCell(event, row, column)}
                  >
                    {content(row, column)}
                  </button>
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
