import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import type { LanguageCode } from '../../branding/languages.ts'
import { translate } from '../../components/multilingual.ts'
import type { OriginState } from '../../components/Origin.ts'
import type { SearchFilter } from '../search/Search.ts'
import { solrHookValueLink, type SolrHook } from './SolrHook.ts'

interface SolrHookValueBadgeProps {
  solrHook: SolrHook
  value: string
  /**
   * Nur in der Suche: Ein Wert mit dem Ziel Suche wird dort ein weiterer Filter, statt eine neue Suche zu öffnen. Ein
   * Wert mit einer Mappe als Ziel öffnet auch in der Suche die Mappe (User 2026-10-13: „immer“).
   */
  onSearchFilter?: (filter: SearchFilter) => void
  /** die Herkunft für eine Mappe, damit Schließen und Zurück dorthin führen */
  origin?: OriginState
  /** Sprache des Anzeigenamens im Hinweis für Screenreader */
  language?: LanguageCode
  /** weitere Klassen, z. B. search-hit-hook-value in den Trefferlisten */
  className?: string
}

/**
 * Ein Wert eines Hooks als Badge, überall dasselbe (User 2026-10-13): Wohin er führt, sagt das Ziel am Hook
 * (valueBadgeLinkTarget aus den Stammdaten, solrHookValueLink), eine Artikel-, Kunden- oder Lieferantenmappe oder die
 * Suche mit dem Wert als Filter. Jede Stelle, die Hook-Werte zeigt, nutzt dieses Badge, damit dieselbe Nummer überall
 * dasselbe tut.
 */
export function SolrHookValueBadge({ solrHook, value, onSearchFilter, origin, language, className = '' }: SolrHookValueBadgeProps) {
  const { t } = useTranslation()
  const classes = `solr-hook-value-badge solr-hook-value-badge-link ${className}`.trim()

  if (solrHook.valueBadgeLinkTarget === 'SEARCH' && onSearchFilter !== undefined) {
    return (
      <button
        type="button"
        className={classes}
        aria-label={t('search.filters.addValue', { field: translate(solrHook.displayName, language) || solrHook.key, value })}
        onClick={() => onSearchFilter({ fieldName: solrHook.key, value })}
      >
        {value}
      </button>
    )
  }

  const link = solrHookValueLink(solrHook, value)
  // die Suche bekommt ihre Vorbelegung, eine Mappe die Herkunft
  return (
    <Link to={link.to} state={link.state ?? origin} className={classes}>
      {value}
    </Link>
  )
}
