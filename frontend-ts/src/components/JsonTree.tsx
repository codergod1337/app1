import { type ReactNode, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Icon } from './Icon.tsx'
import { elementKeyInTree, isPlainObject } from './jsonElementIdentity.ts'

interface JsonTreeProps {
  /** das geparste JSON, egal wie groß */
  value: unknown
  /** Name der Wurzel in der Anzeige und am Anfang jedes Pfads, ohne Angabe „$“ */
  rootName?: string
  /** bis zu dieser Tiefe sind die Knoten am Anfang aufgeklappt, 0 heißt nur die Wurzel; ohne Angabe 1 */
  expandedDepth?: number
  /** Suchtext: Es bleiben nur Knoten, deren Schlüssel oder Wert ihn enthält, samt ihrem Weg von der Wurzel */
  filter?: string
  /** Klick auf einen Schlüssel: der Pfad des Knotens und sein Wert */
  onSelect?: (path: string, value: unknown) => void
  /** der Pfad, der gerade gewählt ist, wird hervorgehoben */
  selectedPath?: string | null
  /** true für Knoten, die nicht gezeigt werden, samt allem darunter */
  hiddenPath?: (path: string) => boolean
  /** eine Markierung je Knoten: der Schlüssel bekommt eine Farbe, z. B. gemappt grün, kennzeichnend blau; null heißt keine */
  markPath?: (path: string) => JsonTreeMark | null
  /** Knöpfe rechts in der Zeile eines Knotens, z. B. „Ignorieren“ */
  nodeActions?: (path: string, value: unknown) => ReactNode
  /** diesen Pfad anspringen: alle Knoten darüber klappen auf, der Knoten rollt in die Mitte; nonce erzwingt es auch beim selben Pfad noch einmal */
  reveal?: { path: string; nonce: number } | null
}

/** Die Markierungen, die ein Baum zeigen kann; das Aussehen steht hier, die Bedeutung beim Aufrufer */
export type JsonTreeMark = 'mapped' | 'condition'

const MARK_CLASS_NAMES: Record<JsonTreeMark, string> = {
  mapped: 'bg-success-subtle',
  condition: 'bg-info-subtle',
}

/** So viele Zeichen eines Texts stehen in der Zeile, der Rest wird mit … gekürzt */
const TEXT_PREVIEW_LENGTH = 120

/** Höchstens so viele Felder stehen in der Zusammenfassung eines zugeklappten Objekts */
const SUMMARY_FIELDS = 3

function isContainer(value: unknown): boolean {
  return Array.isArray(value) || isPlainObject(value)
}

/**
 * Die Kinder eines Knotens: der gezeigte Schlüssel (bei Listen der Name des Elements aus jsonElementIdentity, sonst
 * seine Position), der Wert, ob es ein Listenelement ist, und ein eindeutiger Schlüssel für React
 */
function childrenOf(value: unknown): { key: string; value: unknown; index: boolean; reactKey: string }[] {
  if (Array.isArray(value)) {
    return value.map((element, index) => ({
      key: elementKeyInTree(element, index),
      value: element,
      index: true,
      reactKey: String(index),
    }))
  }
  if (isPlainObject(value)) {
    return Object.entries(value).map(([key, element]) => ({ key, value: element, index: false, reactKey: key }))
  }
  return []
}

/** Der Pfad eines Kindes: Listenelement in eckigen Klammern, Schlüssel mit Punkt, z. B. content.data.table[id=(610762,2,3)].fields.elem.text */
function childPath(parentPath: string, key: string, index: boolean): string {
  return index ? `${parentPath}[${key}]` : `${parentPath}.${key}`
}

function primitiveText(value: unknown): string {
  if (value === null) {
    return 'null'
  }
  if (typeof value === 'string') {
    const oneLine = value.replace(/\s+/g, ' ')
    return `"${oneLine.length > TEXT_PREVIEW_LENGTH ? `${oneLine.slice(0, TEXT_PREVIEW_LENGTH)}…` : oneLine}"`
  }
  return String(value)
}

function primitiveClass(value: unknown): string {
  if (value === null) {
    return 'text-body-secondary'
  }
  if (typeof value === 'string') {
    return 'text-success'
  }
  if (typeof value === 'number') {
    return 'text-primary'
  }
  if (typeof value === 'boolean') {
    return 'text-warning-emphasis'
  }
  return ''
}

/** Trifft der Suchtext den Schlüssel oder einen einfachen Wert? */
function matches(key: string, value: unknown, needle: string): boolean {
  if (key.toLowerCase().includes(needle)) {
    return true
  }
  if (isContainer(value)) {
    return false
  }
  return primitiveText(value).toLowerCase().includes(needle)
}

/**
 * Der Suchlauf über den ganzen Baum, einmal je Suchtext: die Pfade der Treffer und die Pfade aller Knoten, die auf dem
 * Weg zu einem Treffer liegen und deshalb aufgeklappt gezeigt werden.
 */
function collectMatches(
  rootName: string,
  value: unknown,
  needle: string,
): { hits: Set<string>; onPath: Set<string> } {
  const hits = new Set<string>()
  const onPath = new Set<string>()
  function walk(key: string, node: unknown, path: string): boolean {
    let hit = matches(key, node, needle)
    if (hit) {
      hits.add(path)
    }
    let childHit = false
    for (const child of childrenOf(node)) {
      if (walk(child.key, child.value, childPath(path, child.key, child.index))) {
        childHit = true
      }
    }
    if (childHit) {
      onPath.add(path)
      hit = true
    }
    return hit
  }
  walk(rootName, value, rootName)
  return { hits, onPath }
}

interface JsonTreeNodeProps {
  name: string
  value: unknown
  path: string
  depth: number
  expandedDepth: number
  search: { hits: Set<string>; onPath: Set<string> } | null
  onSelect?: (path: string, value: unknown) => void
  selectedPath: string | null
  hiddenPath?: (path: string) => boolean
  markPath?: (path: string) => JsonTreeMark | null
  nodeActions?: (path: string, value: unknown) => ReactNode
  reveal?: { path: string; nonce: number } | null
}

/** Ein Knoten mit seiner Zeile und, aufgeklappt, seinen Kindern. Kinder werden erst gezeichnet, wenn sie sichtbar sind. */
function JsonTreeNode({ name, value, path, depth, expandedDepth, search, onSelect, selectedPath, hiddenPath, markPath, nodeActions, reveal }: JsonTreeNodeProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(depth < expandedDepth)
  const row = useRef<HTMLDivElement>(null)
  const container = isContainer(value)
  const children = container ? childrenOf(value) : []

  // angesprungen: der Knoten selbst oder einer auf dem Weg dorthin
  const revealed = reveal ?? null
  const revealTarget = revealed !== null && revealed.path === path
  const onRevealPath = revealed !== null && (revealTarget || revealed.path.startsWith(`${path}.`) || revealed.path.startsWith(`${path}[`))

  // jedes Anspringen klappt den Weg einmal auf (je nonce einmal), danach klappt der User wieder frei; Kinder, die erst
  // dadurch gezeichnet werden, öffnen sich beim ersten Zeichnen genauso
  const [appliedRevealNonce, setAppliedRevealNonce] = useState<number | null>(null)
  if (revealed !== null && onRevealPath && revealed.nonce !== appliedRevealNonce) {
    setAppliedRevealNonce(revealed.nonce)
    if (!open) {
      setOpen(true)
    }
  }

  // mit Suche: aufgeklappt, was auf dem Weg zu einem Treffer liegt; ohne Suche: wie der User geklappt hat
  const expanded = search ? search.onPath.has(path) : open
  const hit = search?.hits.has(path) ?? false
  const mark = markPath?.(path) ?? null

  const revealNonce = revealed?.nonce ?? null
  useEffect(() => {
    if (revealTarget) {
      row.current?.scrollIntoView({ block: 'center', behavior: 'smooth' })
    }
  }, [revealTarget, revealNonce])

  let summary
  if (Array.isArray(value)) {
    summary = <span className="text-body-secondary">{t('common.jsonTree.array', { count: value.length })}</span>
  } else if (isPlainObject(value)) {
    const preview = Object.entries(value)
      .filter(([, element]) => !isContainer(element))
      .slice(0, SUMMARY_FIELDS)
      .map(([key, element]) => `${key}: ${primitiveText(element)}`)
      .join(', ')
    summary = (
      <span className="text-body-secondary">
        {t('common.jsonTree.object', { count: Object.keys(value).length })}
        {preview !== '' && !expanded && <> · {preview}</>}
      </span>
    )
  } else {
    summary = <span className={primitiveClass(value)}>{primitiveText(value)}</span>
  }

  const keyClass = [
    'json-tree-key',
    path === selectedPath ? 'fw-bold text-decoration-underline' : '',
    hit ? 'bg-warning-subtle' : '',
    mark !== null && !hit ? MARK_CLASS_NAMES[mark] : '',
  ]
    .join(' ')
    .trim()

  return (
    <li>
      <div ref={row} className="d-flex align-items-baseline gap-1">
        {container ? (
          <button
            type="button"
            className="btn btn-link btn-sm p-0 border-0 lh-1"
            aria-label={expanded ? t('common.jsonTree.collapse') : t('common.jsonTree.expand')}
            onClick={() => setOpen(!expanded)}
          >
            <Icon name={expanded ? 'expand' : 'collapsed'} />
          </button>
        ) : (
          <span className="d-inline-block" style={{ width: '1.25rem' }} aria-hidden="true" />
        )}
        {onSelect ? (
          <button type="button" className={`btn btn-link btn-sm p-0 border-0 ${keyClass}`} onClick={() => onSelect(path, value)}>
            {name}
          </button>
        ) : (
          <span className={keyClass}>{name}</span>
        )}
        <span>:</span>
        {summary}
        {nodeActions?.(path, value)}
      </div>
      {container && expanded && children.length > 0 && (
        <ul className="list-unstyled ps-4 mb-0">
          {children.map((child) => {
            const childNodePath = childPath(path, child.key, child.index)
            // mit Suche fallen Kinder weg, die weder Treffer sind noch zu einem führen; ausgeblendete immer
            if (search && !search.hits.has(childNodePath) && !search.onPath.has(childNodePath)) {
              return null
            }
            if (hiddenPath?.(childNodePath)) {
              return null
            }
            return (
              <JsonTreeNode
                key={child.reactKey}
                name={child.key}
                value={child.value}
                path={childNodePath}
                depth={depth + 1}
                expandedDepth={expandedDepth}
                search={search}
                onSelect={onSelect}
                selectedPath={selectedPath}
                hiddenPath={hiddenPath}
                markPath={markPath}
                nodeActions={nodeActions}
                reveal={reveal}
              />
            )
          })}
        </ul>
      )}
    </li>
  )
}

/**
 * Ein JSON als aufklappbarer Baum, für Antworten jeder Größe: Nur sichtbare Knoten werden gezeichnet, zugeklappte
 * Objekte zeigen ihre ersten Felder als Zusammenfassung, ein Suchtext lässt nur die Treffer samt Weg stehen. Jeder
 * Knoten hat einen Pfad über Namen, nie über Positionen: content.data.head.fields.nummer.value, ein Listenelement über
 * das, was es benennt, content.data.table[id=(610762,2,3)].fields.elem.text; ein Klick auf den Schlüssel meldet ihn,
 * daraus wird die Adresse fürs Mapping. Ohne Fachwissen: kennt weder ABAS noch Solr.
 */
export function JsonTree({
  value,
  rootName = '$',
  expandedDepth = 1,
  filter,
  onSelect,
  selectedPath = null,
  hiddenPath,
  markPath,
  nodeActions,
  reveal = null,
}: JsonTreeProps) {
  const needle = (filter ?? '').trim().toLowerCase()
  const search = useMemo(() => (needle === '' ? null : collectMatches(rootName, value, needle)), [rootName, value, needle])
  const { t } = useTranslation()

  if (search !== null && search.hits.size === 0) {
    return <p className="mb-0 text-body-secondary">{t('common.noMatches')}</p>
  }

  return (
    <ul className="list-unstyled mb-0 font-monospace small text-break">
      <JsonTreeNode
        name={rootName}
        value={value}
        path={rootName}
        depth={0}
        expandedDepth={expandedDepth}
        search={search}
        onSelect={onSelect}
        selectedPath={selectedPath}
        hiddenPath={hiddenPath}
        markPath={markPath}
        nodeActions={nodeActions}
        reveal={reveal}
      />
    </ul>
  )
}
