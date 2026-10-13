import { type ReactNode, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { elementKeyInTree } from '../../components/jsonElementIdentity.ts'
import { JsonTree, type JsonTreeMark } from '../../components/JsonTree.tsx'
import { showToast } from '../../components/toastStore.ts'
import { concretePathOf } from './jsonPath.ts'

/** Was mit einem Knoten im Entwurf passiert ist: gemappt, kennzeichnend, ignoriert, oder noch nichts */
export type MappingDecision = 'mapped' | 'condition' | 'ignored'

interface MappingJsonTreeBoxProps {
  /** das geparste JSON, egal woher und wie groß */
  value: unknown
  /** die Überschrift der Box, ohne Angabe „Baum“ */
  title?: ReactNode
  /** meldet jeden Klick auf einen Schlüssel nach außen, mit Pfad und Wert: benötigt, also ein Eintrag im Entwurf */
  onSelect?: (path: string, value: unknown) => void
  /** der Knopf „Ignorieren“ je Knoten; fehlt er, gibt es keinen */
  onIgnore?: (path: string, value: unknown) => void
  /** die Entscheidung des Entwurfs je Knoten: gemappt und kennzeichnend werden farbig, ignoriert mit dem Schalter ausgeblendet */
  decisionOf?: (path: string) => MappingDecision | null
  /** eine Adresse anspringen, z. B. die Quelle eines Eintrags; nonce erzwingt es auch beim selben Pfad noch einmal */
  reveal?: { path: string; nonce: number } | null
}

/** Ein gewählter Knoten: sein Pfad und sein Wert */
interface SelectedNode {
  path: string
  value: unknown
}

/**
 * Die Box mit dem JSON als Baum: Suche über Schlüssel und Werte im Kopf, der Pfad des angeklickten Knotens zum
 * Kopieren im Fuß. Mit Entwurf dazu: gemappte Knoten grün, kennzeichnende blau, Ignorierte mit dem Schalter
 * ausgeblendet, je Knoten der Knopf „Ignorieren“, und ein Klick auf einen Wert im Blatt springt hierher (User
 * 2026-10-10). Geteilt vom JSON-Tab und vom ABAS-Tab. Ein neues JSON heißt eine neue Instanz: Der Aufrufer setzt key,
 * dann starten Suche und Auswahl leer.
 */
export function MappingJsonTreeBox({ value, title, onSelect, onIgnore, decisionOf, reveal = null }: MappingJsonTreeBoxProps) {
  const { t } = useTranslation()
  const [filter, setFilter] = useState('')
  const [selected, setSelected] = useState<SelectedNode | null>(null)
  const [hideIgnored, setHideIgnored] = useState(true)

  // die Adresse eines Eintrags ist allgemein (table[*]); der Baum kennt das erste passende Element beim Namen
  const concretePath = reveal === null ? null : concretePathOf(value, reveal.path, elementKeyInTree)
  const concreteReveal = reveal === null || concretePath === null ? null : { path: concretePath, nonce: reveal.nonce }

  // jedes Anspringen (je nonce einmal) wählt den Knoten und räumt die Suche weg, denn mit Suche klappt der Baum nur Treffer auf
  const [appliedRevealNonce, setAppliedRevealNonce] = useState<number | null>(null)
  if (concreteReveal !== null && concreteReveal.nonce !== appliedRevealNonce) {
    setAppliedRevealNonce(concreteReveal.nonce)
    setSelected({ path: concreteReveal.path, value: undefined })
    setFilter('')
  }

  function select(path: string, nodeValue: unknown) {
    setSelected({ path, value: nodeValue })
    onSelect?.(path, nodeValue)
  }

  function copyPath() {
    if (selected === null) {
      return
    }
    navigator.clipboard
      .writeText(selected.path)
      .then(() => showToast({ kind: 'info', title: t('mapping.jsonView.pathCopied'), text: selected.path }))
      .catch(() => showToast({ kind: 'error', title: t('mapping.jsonView.pathNotCopied') }))
  }

  const treeActions = (
    <span className="d-inline-flex align-items-center gap-3">
      {decisionOf !== undefined && (
        <label className="form-check form-check-inline mb-0 text-nowrap">
          <input className="form-check-input" type="checkbox" checked={hideIgnored} onChange={(event) => setHideIgnored(event.target.checked)} />
          <span className="form-check-label">{t('mapping.draft.hideIgnored')}</span>
        </label>
      )}
      <input
        type="search"
        className="form-control form-control-sm"
        value={filter}
        placeholder={t('mapping.jsonView.searchPlaceholder')}
        aria-label={t('mapping.jsonView.searchPlaceholder')}
        onChange={(event) => setFilter(event.target.value)}
      />
    </span>
  )

  let treeFooter
  if (selected === null) {
    treeFooter = <span>{t('mapping.jsonView.selectHint')}</span>
  } else {
    treeFooter = (
      <span className="d-inline-flex align-items-center gap-2 text-break">
        <code>{selected.path}</code>
        <Hoverlay text={t('mapping.jsonView.copyPath')}>
          <button type="button" className="button-other" aria-label={t('mapping.jsonView.copyPath')} onClick={copyPath}>
            <Icon name="copy" />
          </button>
        </Hoverlay>
      </span>
    )
  }

  const ignoreLabel = t('mapping.draft.ignore')
  const nodeActions =
    onIgnore === undefined
      ? undefined
      : (path: string, nodeValue: unknown) => (
          <Hoverlay text={ignoreLabel}>
            <button type="button" className="btn btn-link btn-sm p-0 border-0 lh-1 text-body-secondary" aria-label={ignoreLabel} onClick={() => onIgnore(path, nodeValue)}>
              <Icon name="private" />
            </button>
          </Hoverlay>
        )

  const markPath =
    decisionOf === undefined
      ? undefined
      : (path: string): JsonTreeMark | null => {
          const decision = decisionOf(path)
          return decision === 'mapped' || decision === 'condition' ? decision : null
        }

  return (
    <ContentBox title={title ?? t('mapping.jsonView.treeTitle')} actions={treeActions} footer={treeFooter}>
      <JsonTree
        value={value}
        expandedDepth={2}
        filter={filter}
        onSelect={select}
        selectedPath={selected?.path ?? null}
        hiddenPath={decisionOf !== undefined && hideIgnored ? (path) => decisionOf(path) === 'ignored' : undefined}
        markPath={markPath}
        nodeActions={nodeActions}
        reveal={concreteReveal}
      />
    </ContentBox>
  )
}
