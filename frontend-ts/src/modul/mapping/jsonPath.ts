import { isRecord } from './ResponseMapping.ts'

/**
 * Die Adress-Grammatik des Mappings, im Frontend zum Zeigen der Werte, später im Backend in Java dasselbe:
 * $.content.data.head.fields.name.value, Listen über [*] alle, [feld] wo das Feld gefüllt ist, [feld=wert] ein Wert.
 * Ein Feld eines Elements ist bei ABAS fields.<feld> mit value und text, sonst das Element selbst; verglichen wird
 * ohne Klammern und ohne Groß und Klein, wie in der Vorlage. Nie eine Position.
 */

interface PathSegment {
  name: string
  /** null heißt kein Filter; sonst der Text in den eckigen Klammern */
  filter: string | null
}

/** $.content.data.table[artikel].fields wird zu content, data, table[artikel], fields; das $ fällt weg */
export function pathSegmentsOf(path: string): PathSegment[] {
  const segments: PathSegment[] = []
  let name = ''
  let filter: string | null = null
  let inBracket = false
  function push() {
    if (name !== '' || filter !== null) {
      segments.push({ name, filter })
    }
    name = ''
    filter = null
  }
  for (const character of path.trim()) {
    if (inBracket) {
      if (character === ']') {
        inBracket = false
      } else {
        filter = (filter ?? '') + character
      }
      continue
    }
    if (character === '[') {
      inBracket = true
      filter = ''
      continue
    }
    if (character === '.') {
      push()
      continue
    }
    name += character
  }
  push()
  return segments.filter((segment) => !(segment.name === '$' && segment.filter === null))
}

/** Das Feld eines Elements: bei ABAS fields.<feld> mit value und text, sonst der Wert direkt */
function fieldOf(element: unknown, field: string): { value: unknown; text: unknown } | null {
  if (!isRecord(element)) {
    return null
  }
  const fields = element.fields
  if (isRecord(fields) && field in fields) {
    const found = fields[field]
    return isRecord(found) ? { value: found.value, text: found.text } : { value: found, text: undefined }
  }
  if (field in element) {
    return { value: element[field], text: undefined }
  }
  return null
}

/** Vergleichsform: ohne Klammern, getrimmt, klein; nichts ist der leere Text */
function comparable(value: unknown): string {
  if (value === null || value === undefined) {
    return ''
  }
  return String(value).replace(/^\(/, '').replace(/\)$/, '').trim().toLowerCase()
}

export function filterMatches(element: unknown, filter: string): boolean {
  const trimmed = filter.trim()
  if (trimmed === '' || trimmed === '*') {
    return true
  }
  const equals = trimmed.indexOf('=')
  const field = fieldOf(element, (equals < 0 ? trimmed : trimmed.slice(0, equals)).trim())
  if (field === null) {
    return false
  }
  if (equals < 0) {
    return comparable(field.value) !== '' || comparable(field.text) !== ''
  }
  const wanted = comparable(trimmed.slice(equals + 1))
  return comparable(field.value) === wanted || comparable(field.text) === wanted
}

/** Die Elemente einer Liste; eine Map (die Map-Form von ABAS) liefert ihre Werte */
function elementsOf(node: unknown): unknown[] {
  if (Array.isArray(node)) {
    return node
  }
  return isRecord(node) ? Object.values(node) : []
}

function evaluateSegments(nodes: unknown[], segments: PathSegment[]): unknown[] {
  let current = nodes
  for (const segment of segments) {
    const next: unknown[] = []
    for (const node of current) {
      let child: unknown
      if (segment.name === '') {
        child = node
      } else if (isRecord(node)) {
        child = node[segment.name]
      } else {
        child = undefined
      }
      if (child === undefined) {
        continue
      }
      if (segment.filter === null) {
        next.push(child)
      } else {
        for (const element of elementsOf(child)) {
          if (filterMatches(element, segment.filter)) {
            next.push(element)
          }
        }
      }
    }
    current = next
  }
  return current
}

/** Alle Treffer einer Adresse im JSON; ohne Liste darin höchstens einer */
export function evaluatePath(json: unknown, path: string): unknown[] {
  return evaluateSegments([json], pathSegmentsOf(path))
}

/**
 * Die Werte eines Eintrags: Ohne Liste ein Wert oder keiner. Mit Liste je Element einer, null bei einer Lücke, damit
 * die Listen eines Mappings zeilengleich bleiben; ohne elementPath die Elemente selbst.
 */
export function evaluateLine(json: unknown, sourcePath: string, elementPath: string | null): { list: boolean; values: unknown[] } {
  const sourceSegments = pathSegmentsOf(sourcePath)
  const sources = evaluateSegments([json], sourceSegments)
  const list = sourceSegments.some((segment) => segment.filter !== null)
  if (elementPath === null || elementPath.trim() === '') {
    return list ? { list: true, values: sources } : { list: false, values: sources.slice(0, 1) }
  }
  const elementSegments = pathSegmentsOf(elementPath)
  return { list: true, values: sources.map((element) => evaluateSegments([element], elementSegments)[0] ?? null) }
}

/**
 * Der Pfad im Baum zu einer Adresse: wie der Baum ihn schreibt, mit dem ersten passenden Element statt [*] oder einem
 * Filter, damit sich die Stelle im Baum aufklappen und anspringen lässt. Eine Liste schreibt der Baum als
 * [name des Elements], elementKeyOf liefert diesen Namen; die Map-Form schreibt er als .schlüssel. null, wenn die
 * Adresse im JSON ins Leere führt.
 */
export function concretePathOf(json: unknown, path: string, elementKeyOf: (element: unknown, index: number) => string): string | null {
  let node: unknown = json
  let built = '$'
  for (const segment of pathSegmentsOf(path)) {
    let child: unknown
    if (segment.name === '') {
      child = node
    } else if (isRecord(node)) {
      child = node[segment.name]
      built += `.${segment.name}`
    } else {
      return null
    }
    if (child === undefined) {
      return null
    }
    if (segment.filter !== null) {
      const filter = segment.filter
      if (Array.isArray(child)) {
        const index = child.findIndex((element) => filterMatches(element, filter))
        if (index < 0) {
          return null
        }
        built += `[${elementKeyOf(child[index], index)}]`
        child = child[index]
      } else if (isRecord(child)) {
        const entry = Object.entries(child).find(([, element]) => filterMatches(element, filter))
        if (entry === undefined) {
          return null
        }
        built += `.${entry[0]}`
        child = entry[1]
      } else {
        return null
      }
    }
    node = child
  }
  return built
}

/**
 * Die Zeilen eines Tabellen-Eintrags, wie das Backend sie bildet: je Element der Liste an sourcePath ein Objekt mit den
 * Spalten, jede Spalte der erste Treffer ihrer Adresse im Element, fehlende null; Zeilen ohne einen einzigen Wert fallen weg
 */
export function evaluateTableRows(json: unknown, sourcePath: string, columns: readonly { name: string; elementPath: string }[]): Record<string, unknown>[] {
  const rows: Record<string, unknown>[] = []
  for (const element of evaluateLine(json, sourcePath, null).values) {
    const row: Record<string, unknown> = {}
    let any = false
    for (const column of columns) {
      const value = evaluatePath(element, column.elementPath)[0] ?? null
      if (value !== null && String(value) !== '') {
        any = true
      }
      row[column.name] = value
    }
    if (any) {
      rows.push(row)
    }
  }
  return rows
}

/** Ein Wert als kurzer Text für das Tabellenblatt */
export function valueText(value: unknown): string {
  if (value === null || value === undefined) {
    return '∅'
  }
  if (typeof value === 'object') {
    const json = JSON.stringify(value)
    return json.length > 60 ? `${json.slice(0, 60)}…` : json
  }
  return String(value)
}
