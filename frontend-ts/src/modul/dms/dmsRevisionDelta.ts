import { SOLR_VERSION_FIELD_NAME } from './Dms.ts'

/**
 * Das Feld-Delta einer Revision, die eine Tabelle je aufgeklappter Revision (nach dmsDelta.js der Vorlage, Zuschnitt
 * User 2026-10-08: nur vorher und nachher):
 *
 *   Feld | vorher | nachher
 *
 * vorher → nachher ist die Änderung der Zeile, Stand N → Stand N+1; Zeitpunkt und User der Zeile gehören zu genau
 * diesem Schritt. Bei der jüngsten Revision ist der Stand nach der Änderung der Iststand selbst. Reine Funktionen,
 * kein React.
 */

/**
 * Felder, die im Delta nichts sagen: der Zähler (das ist die Nummer der Zeile), der Stempel der Handlung, den die
 * Zeile selbst schon als Zeitpunkt und User zeigt, und Solrs _version_, das im ZIP nie steht (User 2026-10-08).
 */
const WITHOUT_STATEMENT: ReadonlySet<string> = new Set([
  'fileIdRevisionNo',
  'lastChangedAt',
  'changingUsers',
  SOLR_VERSION_FIELD_NAME,
])

/** So viele Zeichen zeigt eine Zelle höchstens: Volltext und Vorschaubild wären sonst Bildschirmseiten */
const MAX_CELL_CHARACTERS = 160

/** Ein reiner ISO-Zeitstempel, nichts dahinter: nur solche Werte gelten als Zeitpunkt */
const ISO_TIMESTAMP = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?(Z|[+-]\d{2}:?\d{2})?$/

export interface DmsRevisionDeltaRow {
  fieldName: string
  before: string
  after: string
}

export interface DmsRevisionDelta {
  /** jedes Feld, das diese Änderung geändert hat, nach Feldname sortiert */
  rows: DmsRevisionDeltaRow[]
  /** ob das Zurückladen dieses Stands den heutigen überhaupt ändert, gerechnet auf den Rohwerten */
  restoreChangesSomething: boolean
}

/**
 * Die Zeilen der Revisions-Tabelle samt der Antwort, ob das Zurückladen etwas ändert. In die Tabelle kommt jedes Feld,
 * das zwischen vorher und nachher verschieden ist. restoreChangesSomething vergleicht vorher mit heute, auf den
 * Rohwerten: Listen als Menge, denn zwei Listen mit gleichen Werten in anderer Reihenfolge sind derselbe Stand.
 *
 * @param before der Stand dieser Revision (das Dokument aus dem ZIP N)
 * @param after  der Stand nach der Änderung der Zeile (ZIP N+1, oder der Iststand, wenn es keine nächste gibt)
 * @param today  der aktuelle Stand (das geladene Dokument), nur für restoreChangesSomething
 */
export function dmsRevisionDelta(
  before: Record<string, unknown>,
  after: Record<string, unknown>,
  today: Record<string, unknown>,
): DmsRevisionDelta {
  const fieldNames = new Set([...Object.keys(before), ...Object.keys(after), ...Object.keys(today)])
  const rows: DmsRevisionDeltaRow[] = []
  let restoreChangesSomething = false
  for (const fieldName of fieldNames) {
    if (WITHOUT_STATEMENT.has(fieldName)) {
      continue
    }
    const valueBefore = before[fieldName]
    if (!sameValue(valueBefore, today[fieldName])) {
      restoreChangesSomething = true
    }
    const valueAfter = after[fieldName]
    if (!sameValue(valueBefore, valueAfter)) {
      rows.push({ fieldName, before: readable(valueBefore), after: readable(valueAfter) })
    }
  }
  rows.sort((a, b) => a.fieldName.localeCompare(b.fieldName))
  return { rows, restoreChangesSomething }
}

/** Werte-Gleichheit: Listen als Menge, alles andere über den Vergleichswert */
function sameValue(a: unknown, b: unknown): boolean {
  if (a === b) {
    return true
  }
  if (a === null || a === undefined || b === null || b === undefined) {
    return isEmpty(a) && isEmpty(b)
  }
  if (Array.isArray(a) || Array.isArray(b)) {
    const setA = new Set(asList(a).map(comparable))
    const setB = new Set(asList(b).map(comparable))
    if (setA.size !== setB.size) {
      return false
    }
    for (const entry of setA) {
      if (!setB.has(entry)) {
        return false
      }
    }
    return true
  }
  return comparable(a) === comparable(b)
}

/** null, undefined und die leere Liste sind derselbe Zustand: Feld nicht gesetzt */
function isEmpty(value: unknown): boolean {
  return value === null || value === undefined || (Array.isArray(value) && value.length === 0)
}

/**
 * Zeitstempel werden als Zeitpunkt verglichen, nicht als Schreibweise: Das ZIP trägt Instant.toString(), der Iststand
 * kommt durch Jackson, derselbe Augenblick kann mit oder ohne Nachkommastellen geschrieben sein.
 */
function comparable(value: unknown): string {
  const text = String(value)
  if (ISO_TIMESTAMP.test(text)) {
    const time = Date.parse(text)
    if (!Number.isNaN(time)) {
      return `time:${time}`
    }
  }
  return text
}

function asList(value: unknown): unknown[] {
  if (value === null || value === undefined) {
    return []
  }
  return Array.isArray(value) ? value : [value]
}

/** Anzeigewert: Listen mit Komma, leer als Strich, lange Texte gekürzt */
function readable(value: unknown): string {
  if (isEmpty(value)) {
    return '—'
  }
  const text = Array.isArray(value) ? value.map(String).join(', ') : String(value)
  return text.length > MAX_CELL_CHARACTERS ? `${text.slice(0, MAX_CELL_CHARACTERS)}…` : text
}
