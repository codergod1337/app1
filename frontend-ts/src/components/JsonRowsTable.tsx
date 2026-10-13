import type { ReactNode } from 'react'
import { jsonRowValueText } from './jsonRows.ts'
import { Table, type TableColumn } from './Table.tsx'

/** Eine Spalte: der Schlüssel im Zeilen-Objekt, die Beschriftung, ob sie am Handy wegfällt */
export interface JsonRowsColumn {
  key: string
  label: string
  unimportant?: boolean
  width?: string
}

interface JsonRowsTableProps {
  /** die Zeilen, je Zeile ein Objekt; der Aufrufer hat sie aus den JSON-Texten eines Feldes gelesen */
  rows: readonly Record<string, unknown>[]
  columns: readonly JsonRowsColumn[]
  /** eine Zelle anders zeichnen, z. B. einen Wert als Link; ohne Angabe der Wert als Text */
  renderCell?: (row: Record<string, unknown>, key: string, value: unknown) => ReactNode
  emptyText: string
  /** der Titel des Detail-Popups am Handy, ohne Angabe die erste Spalte */
  detailTitle?: (row: Record<string, unknown>) => ReactNode
}

/**
 * Eine Tabelle aus Zeilen-Objekten auf der zentralen Table, ohne Fachwissen: für Felder, die eine ganze Tabelle als
 * JSON-Zeilen tragen (ein multiValued-Feld, je Wert eine Zeile), z. B. die Stückliste eines Artikels. Die Spalten und
 * ihre Beschriftung gibt der Aufrufer, eine Zelle kann er selbst zeichnen.
 */
export function JsonRowsTable({ rows, columns, renderCell, emptyText, detailTitle }: JsonRowsTableProps) {
  const tableColumns: TableColumn<{ index: number; row: Record<string, unknown> }>[] = columns.map((column) => ({
    header: column.label,
    cell: ({ row }) => (renderCell !== undefined ? renderCell(row, column.key, row[column.key]) : jsonRowValueText(row[column.key])),
    unimportant: column.unimportant,
    width: column.width,
  }))
  const indexedRows = rows.map((row, index) => ({ index, row }))
  const firstKey = columns[0]?.key
  return (
    <Table
      columns={tableColumns}
      rows={indexedRows}
      rowKey={({ index }) => String(index)}
      detailTitle={({ row }) => (detailTitle !== undefined ? detailTitle(row) : firstKey === undefined ? '' : jsonRowValueText(row[firstKey]))}
      emptyText={emptyText}
    />
  )
}
