import { Fragment, type ReactNode } from 'react'
import type { LanguageCode } from '../../branding/languages.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { solrFieldDisplayNameOf } from '../solrmanager/SolrField.ts'
import type { SearchHitReason } from './Search.ts'

/** Dieselben zwei Zeichen wie DmsSearchHitReason.MARK_OPEN und MARK_CLOSE, aus dem Zeichencode gebaut */
const MARK_OPEN = String.fromCharCode(2)
const MARK_CLOSE = String.fromCharCode(3)

interface SearchHitReasonsProps {
  reasons: readonly SearchHitReason[]
  /** der Kern des Treffers, für die Anzeigenamen seiner Felder */
  coreKey: string
  /** Sprache der Anzeigenamen */
  language: LanguageCode
}

/**
 * Warum ist das ein Treffer, die Zeile unter jedem Treffer, nach dem Treffergrund der Vorlage: je Feld sein Name und
 * der Wert mit den Fundstellen als mark. Hook und Feld stehen mit ihrem Anzeigenamen (User 2026-10-07), ohne einen mit
 * dem technischen Namen. Beim Volltext nur der Feldname, selbst markiert, denn der Text kommt nie mit. Sie gehört
 * keinem Kern, jede Trefferzeile stellt ihren Grund damit dar.
 *
 * Kein HTML aus fremden Dokumenten: Das Backend markiert mit zwei Steuerzeichen, hier wird daran zerteilt und jedes
 * Stück als Text gerendert. Eine Marke ohne Ende wird als gewöhnlicher Text ausgegeben statt den Rest zu verschlucken.
 */
export function SearchHitReasons({ reasons, coreKey, language }: SearchHitReasonsProps) {
  const { solrHooks, solrFields } = useMasterData()

  if (reasons.length === 0) {
    return null
  }

  function labelOf(fieldName: string): string {
    const hook = solrHooks?.find((candidate) => candidate.key === fieldName)
    if (hook) {
      return translate(hook.displayName, language) || hook.key
    }
    return solrFieldDisplayNameOf(solrFields ?? [], coreKey, fieldName, language) ?? fieldName
  }

  return (
    <span className="search-hit-reasons">
      {reasons.map((reason) => (
        <span key={reason.fieldName} className="search-hit-reason">
          {reason.snippet === null ? (
            <mark className="search-hit-reason-field">{labelOf(reason.fieldName)}</mark>
          ) : (
            <>
              <span className="search-hit-reason-field">{labelOf(reason.fieldName)}</span>
              <span className="search-hit-reason-snippet">{marked(reason.snippet)}</span>
            </>
          )}
        </span>
      ))}
    </span>
  )
}

function marked(snippet: string): ReactNode[] {
  const pieces: ReactNode[] = []
  let rest = snippet
  let number = 0
  while (rest.length > 0) {
    const start = rest.indexOf(MARK_OPEN)
    if (start < 0) {
      pieces.push(<Fragment key={number++}>{rest}</Fragment>)
      break
    }
    if (start > 0) {
      pieces.push(<Fragment key={number++}>{rest.slice(0, start)}</Fragment>)
    }
    const end = rest.indexOf(MARK_CLOSE, start)
    if (end < 0) {
      pieces.push(<Fragment key={number++}>{rest.slice(start + 1)}</Fragment>)
      break
    }
    pieces.push(<mark key={number++}>{rest.slice(start + 1, end)}</mark>)
    rest = rest.slice(end + 1)
  }
  return pieces
}
