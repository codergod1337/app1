import type { LanguageCode } from '../../branding/languages.ts'
import { Hoverlay } from '../../components/Hoverlay.tsx'
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
  /**
   * Nur der Chip mit der Zahl der Werte, die Badges erst im Hoverlay beim Darüberfahren (User 2026-10-14, die
   * Trefferlisten der Suche): Das Hoverlay baut seinen Inhalt erst beim Öffnen, so stehen bei hundert Treffern nicht
   * tausende Badges im Dokument. Es ist interaktiv, ein Klick auf eine Badge wirkt wie sonst.
   */
  valuesInHoverlay?: boolean
}

/**
 * Ein Hook am Dokument, die zweite Darstellung neben dem Filter-Chip: vorn der Chip des Hooks (nur der Name), dahinter
 * jeder Wert als Badge (SolrHookValueBadge). Nach HookFelder der Vorlage im Nur-Lesen-Modus. Wohin ein Wert führt, sagt
 * das Ziel am Hook: eine Mappe oder die Suche mit dem Wert als Filter. Mit valuesInHoverlay stehen die Badges erst im
 * Hoverlay über dem Chip.
 */
export function SolrHookValues({
  solrHook,
  values,
  language,
  origin,
  onSearchFilter,
  badgeClassName,
  valuesInHoverlay = false,
}: SolrHookValuesProps) {
  const badges = values.map((value) => (
    <SolrHookValueBadge
      key={value}
      solrHook={solrHook}
      value={value}
      origin={origin}
      onSearchFilter={onSearchFilter}
      language={language}
      className={badgeClassName}
    />
  ))

  if (valuesInHoverlay) {
    return (
      <Hoverlay interactive text={<span className="hoverlay-badges">{badges}</span>}>
        <span className="solr-hook-values solr-hook-values-collapsed" tabIndex={0}>
          <SolrHookChip solrHook={solrHook} language={language} />
          <span className="solr-hook-values-count">{values.length}</span>
        </span>
      </Hoverlay>
    )
  }

  return (
    <span className="solr-hook-values">
      <SolrHookChip solrHook={solrHook} language={language} />
      {badges}
    </span>
  )
}
