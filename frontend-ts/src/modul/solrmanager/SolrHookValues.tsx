import { Link } from 'react-router'
import type { LanguageCode } from '../../branding/languages.ts'
import { solrHookValueLink, type SolrHook } from './SolrHook.ts'
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
 * jeder Wert als Badge. Nach HookFelder der Vorlage im Nur-Lesen-Modus. Jeder Wert ist ein Link (solrHookValueLink):
 * eine hauseigene Artikelnummer in ihre Artikelmappe, alles andere in die Suche mit sich selbst als Filter.
 */
export function SolrHookValues({ solrHook, values, language }: SolrHookValuesProps) {
  return (
    <span className="solr-hook-values">
      <SolrHookChip solrHook={solrHook} language={language} />
      {values.map((value) => {
        const link = solrHookValueLink(solrHook.key, value)
        return (
          <Link key={value} to={link.to} state={link.state} className="solr-hook-value-badge solr-hook-value-badge-link">
            {value}
          </Link>
        )
      })}
    </span>
  )
}
