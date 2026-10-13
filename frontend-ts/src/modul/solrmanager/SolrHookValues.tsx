import type { LanguageCode } from '../../branding/languages.ts'
import type { OriginState } from '../../components/Origin.ts'
import type { SearchFilter } from '../search/Search.ts'
import type { SolrHook } from './SolrHook.ts'
import { SolrHookChip } from './SolrHookChip.tsx'
import { SolrHookValueBadge } from './SolrHookValueBadge.tsx'

interface SolrHookValuesProps {
  solrHook: SolrHook
  /** die Werte des Hooks am Dokument, alle Hooks sind multiValued */
  values: readonly string[]
  /** Sprache des Anzeigenamens, ohne Angabe die Standardsprache */
  language?: LanguageCode
  /** die Herkunft für eine Mappe als Ziel */
  origin?: OriginState
  /** nur in der Suche: ein Wert mit dem Ziel Suche wird dort ein weiterer Filter */
  onSearchFilter?: (filter: SearchFilter) => void
  /** weitere Klassen je Badge, z. B. search-hit-hook-value in den Trefferlisten */
  badgeClassName?: string
}

/**
 * Ein Hook am Dokument, die zweite Darstellung neben dem Filter-Chip: vorn der Chip des Hooks (nur der Name), dahinter
 * jeder Wert als Badge (SolrHookValueBadge). Nach HookFelder der Vorlage im Nur-Lesen-Modus. Wohin ein Wert führt, sagt
 * das Ziel am Hook: eine Mappe oder die Suche mit dem Wert als Filter.
 */
export function SolrHookValues({ solrHook, values, language, origin, onSearchFilter, badgeClassName }: SolrHookValuesProps) {
  return (
    <span className="solr-hook-values">
      <SolrHookChip solrHook={solrHook} language={language} />
      {values.map((value) => (
        <SolrHookValueBadge
          key={value}
          solrHook={solrHook}
          value={value}
          origin={origin}
          onSearchFilter={onSearchFilter}
          language={language}
          className={badgeClassName}
        />
      ))}
    </span>
  )
}
