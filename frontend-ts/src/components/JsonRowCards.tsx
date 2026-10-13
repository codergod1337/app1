import type { ReactNode } from 'react'
import { jsonRowValueText } from './jsonRows.ts'
import { type JsonRowsColumn, JsonRowsTable } from './JsonRowsTable.tsx'

interface JsonRowCardsProps {
  /** die Zeilen, je Zeile ein Objekt; Werte dürfen Objekte und Listen von Objekten sein (Unterantworten eines Subfetchs je Zeile) */
  rows: readonly Record<string, unknown>[]
  /** die Beschriftung eines Schlüssels, als Spalte, Zeile oder Abschnitt */
  labelOf: (key: string) => string
  /** einen Einzelwert anders zeichnen, z. B. eine Sternzeit mit Knopf; ohne Angabe der Wert als Text */
  renderValue?: (key: string, value: unknown) => ReactNode
  emptyText: string
}

/** Tiefer verschachtelt zeigt nichts mehr Karten, sondern den JSON-Text: Schutz gegen endlose Verschachtelung */
const MAX_DEPTH = 4

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isObjectList(value: unknown): value is Record<string, unknown>[] {
  return Array.isArray(value) && value.length > 0 && value.every(isPlainObject)
}

/** Ein Wert, der nicht in eine Zelle passt: ein Objekt oder eine Liste von Objekten */
export function isNestedJsonValue(value: unknown): boolean {
  return isPlainObject(value) || isObjectList(value)
}

/** Eine Zeile, die irgendwo ein Unterobjekt trägt, gehört in eine Karte statt in eine Tabellenzeile */
export function jsonRowsHaveNestedValues(rows: readonly Record<string, unknown>[]): boolean {
  return rows.some((row) => Object.values(row).some(isNestedJsonValue))
}

/**
 * Zeilen-Objekte als Karten statt als Tabelle, ohne Fachwissen: für Tabellen, deren Zeilen Unterobjekte tragen, weil
 * ein Subfetch je Zeile seine Antwort hineingehängt hat (User 2026-10-13, Prüfeinstellung → Prüfplan → Merkmale).
 * Je Zeile eine Karte: oben die Einzelwerte der Zeile, darunter je Unterobjekt ein Abschnitt mit dessen Einzelwerten
 * und Tabellen; eine Untertabelle, deren Zeilen selbst Unterobjekte tragen, wird wieder zu Karten. Mehrzeilige Texte
 * behalten ihre Zeilenumbrüche.
 */
export function JsonRowCards({ rows, labelOf, renderValue, emptyText }: JsonRowCardsProps) {
  if (rows.length === 0) {
    return <p className="mb-0 text-muted">{emptyText}</p>
  }
  return (
    <div className="d-flex flex-column gap-2">
      {rows.map((row, index) => (
        <div key={index} className="border rounded p-2">
          {renderEntries(Object.entries(row), 0, labelOf, renderValue, emptyText)}
        </div>
      ))}
    </div>
  )
}

/** Ein Einzelwert als Text, Listen von Einzelwerten mit Komma; Zeilenumbrüche bleiben stehen */
function scalarText(value: unknown): string {
  if (Array.isArray(value)) {
    return value
      .map((element) => jsonRowValueText(element))
      .filter((text) => text !== '')
      .join(', ')
  }
  return jsonRowValueText(value)
}

function renderScalar(key: string, value: unknown, renderValue?: (key: string, value: unknown) => ReactNode): ReactNode {
  if (renderValue !== undefined && !Array.isArray(value)) {
    return renderValue(key, value)
  }
  const text = scalarText(value)
  return text.includes('\n') ? <span style={{ whiteSpace: 'pre-wrap' }}>{text}</span> : text
}

/**
 * Die Einträge eines Objekts: erst die Einzelwerte als Liste, dann je Liste von Objekten eine Tabelle oder Karten, dann
 * je Unterobjekt ein Abschnitt mit demselben Aufbau
 */
function renderEntries(
  entries: [string, unknown][],
  depth: number,
  labelOf: (key: string) => string,
  renderValue: ((key: string, value: unknown) => ReactNode) | undefined,
  emptyText: string,
): ReactNode {
  const scalars = entries.filter(([, value]) => !isNestedJsonValue(value) && scalarText(value) !== '')
  const lists = entries.filter(([, value]) => isObjectList(value))
  const objects = entries.filter(([, value]) => isPlainObject(value))
  return (
    <>
      {scalars.length > 0 && (
        <dl className="row mb-1 small">
          {scalars.map(([key, value]) => (
            <div key={key} className="col-12 col-md-6 d-flex gap-2">
              <dt className="text-muted fw-normal text-nowrap">{labelOf(key)}</dt>
              <dd className="mb-0 text-break">{renderScalar(key, value, renderValue)}</dd>
            </div>
          ))}
        </dl>
      )}
      {lists.map(([key, value]) => (
        <div key={key} className="mt-2">
          <div className="small fw-semibold mb-1">{labelOf(key)}</div>
          {renderObjectList(value as Record<string, unknown>[], depth, labelOf, renderValue, emptyText)}
        </div>
      ))}
      {objects.map(([key, value]) => (
        <div key={key} className="mt-2 ps-2 border-start">
          <div className="small fw-semibold mb-1">{labelOf(key)}</div>
          {depth >= MAX_DEPTH ? (
            <code className="small text-break">{JSON.stringify(value)}</code>
          ) : (
            renderEntries(Object.entries(value as Record<string, unknown>), depth + 1, labelOf, renderValue, emptyText)
          )}
        </div>
      ))}
    </>
  )
}

/** Eine Liste von Objekten: Karten, wenn eine Zeile Unterobjekte trägt, sonst die zentrale Tabelle */
function renderObjectList(
  rows: Record<string, unknown>[],
  depth: number,
  labelOf: (key: string) => string,
  renderValue: ((key: string, value: unknown) => ReactNode) | undefined,
  emptyText: string,
): ReactNode {
  if (jsonRowsHaveNestedValues(rows) && depth < MAX_DEPTH) {
    return (
      <div className="d-flex flex-column gap-2">
        {rows.map((row, index) => (
          <div key={index} className="border rounded p-2">
            {renderEntries(Object.entries(row), depth + 1, labelOf, renderValue, emptyText)}
          </div>
        ))}
      </div>
    )
  }
  const columnKeys: string[] = []
  for (const row of rows) {
    for (const key of Object.keys(row)) {
      if (!columnKeys.includes(key)) {
        columnKeys.push(key)
      }
    }
  }
  const columns: JsonRowsColumn[] = columnKeys.map((key, index) => ({ key, label: labelOf(key), unimportant: index > 2 }))
  return <JsonRowsTable rows={rows} columns={columns} emptyText={emptyText} renderCell={(_row, key, value) => renderScalar(key, value, renderValue)} />
}
