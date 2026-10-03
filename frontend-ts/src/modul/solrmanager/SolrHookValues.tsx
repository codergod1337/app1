import { Link } from 'react-router'
import type { LanguageCode } from '../../branding/languages.ts'
import { solrHookValuePath, type SolrHook } from './SolrHook.ts'
import { SolrHookChip } from './SolrHookChip.tsx'

interface SolrHookValuesProps {
  solrHook: SolrHook
  /** die Werte des Hooks am Dokument, alle Hooks sind multiValued */
  values: readonly string[]
  /** Sprache des Anzeigenamens, ohne Angabe die Standardsprache */
  language?: LanguageCode
}

/**
 * Ein Hook am Dokument, die zweite Darstellung neben dem Filter-Chip: vorn der Chip des Hooks (nur der Name), dahinter
 * jeder Wert als Badge. Nach HookFelder der Vorlage im Nur-Lesen-Modus. Eine hauseigene Artikelnummer ist ein Link in
 * ihre Artikelmappe (solrHookValuePath), alle anderen Werte stehen als Text, bis die Suche den Weg mit gesetztem Filter
 * anbietet.
 */
export function SolrHookValues({ solrHook, values, language }: SolrHookValuesProps) {
  return (
    <span className="solr-hook-values">
      <SolrHookChip solrHook={solrHook} language={language} />
      {values.map((value) => {
        const path = solrHookValuePath(solrHook.key, value)
        return path !== null ? (
          <Link key={value} to={path} className="solr-hook-value-badge solr-hook-value-badge-link">
            {value}
          </Link>
        ) : (
          <span key={value} className="solr-hook-value-badge">
            {value}
          </span>
        )
      })}
    </span>
  )
}
