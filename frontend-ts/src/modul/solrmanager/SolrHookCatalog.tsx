import type { ReactNode } from 'react'
import type { LanguageCode } from '../../branding/languages.ts'
import { solrHookGroupLabel, type SolrHook, type SolrHookGroupWithHooks } from './SolrHook.ts'
import { SolrHookChip } from './SolrHookChip.tsx'

interface SolrHookCatalogProps {
  /** die Hooks nach Gruppe (groupSolrHooks), in der Reihenfolge der Gruppen */
  groups: SolrHookGroupWithHooks[]
  language: LanguageCode
  /** Suchtext, klein geschrieben: seine Fundstellen im Feldnamen werden markiert */
  needle?: string
  /** fehlt: nur anzeigen. Sonst ist jede Zeile ein Knopf, der den Hook wählt */
  onPick?: (solrHook: SolrHook) => void
  /** Text, wenn keine Gruppe etwas enthält */
  emptyText: string
}

/** Die Fundstellen des Suchtexts im Feldnamen als mark, wie im Treffergrund der Suche der Vorlage */
function highlight(text: string, needle: string): ReactNode {
  if (needle === '') {
    return text
  }
  const lower = text.toLowerCase()
  const pieces: ReactNode[] = []
  let start = 0
  let found = lower.indexOf(needle)
  while (found >= 0) {
    if (found > start) {
      pieces.push(text.slice(start, found))
    }
    pieces.push(<mark key={found}>{text.slice(found, found + needle.length)}</mark>)
    start = found + needle.length
    found = lower.indexOf(needle, start)
  }
  pieces.push(text.slice(start))
  return pieces
}

/**
 * Der Hook-Katalog nach der HookAuswahl der Vorlage: je Gruppe eine Überschrift, darunter eine Zeile je Hook mit dem
 * Chip und daneben dem Feldnamen. Dieselbe Liste in der Hook-Auswahl (dort jede Zeile ein Knopf, mit Suche) und im
 * Kern-Tab (nur anzeigen, in einer Hoverlay-Box). Wer die Auswahl kennt, findet sich im Kern-Tab sofort zurecht, und
 * ein neuer Hook taucht automatisch in beiden auf.
 */
export function SolrHookCatalog({ groups, language, needle = '', onPick, emptyText }: SolrHookCatalogProps) {
  return (
    <div className="solr-hook-catalog">
      {groups.length === 0 && <span className="solr-hook-catalog-empty">{emptyText}</span>}
      {groups.map((group) => (
        <div key={group.solrHookGroup?.key ?? ''}>
          {(groups.length > 1 || group.solrHookGroup !== null) && (
            <span className="solr-hook-catalog-group">{solrHookGroupLabel(group.solrHookGroup, language)}</span>
          )}
          {group.solrHooks.map((solrHook) => {
            const content = (
              <>
                <SolrHookChip solrHook={solrHook} language={language} />
                <span className="solr-hook-catalog-key">{highlight(solrHook.key, needle)}</span>
              </>
            )
            return onPick ? (
              <button
                key={solrHook.key}
                type="button"
                className="solr-hook-catalog-entry"
                onClick={() => onPick(solrHook)}
              >
                {content}
              </button>
            ) : (
              <div key={solrHook.key} className="solr-hook-catalog-entry">
                {content}
              </div>
            )
          })}
        </div>
      ))}
    </div>
  )
}
