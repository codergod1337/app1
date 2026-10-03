import type { LanguageCode } from '../../branding/languages.ts'
import { translate } from '../../components/multilingual.ts'
import { PackIcon } from '../../components/PackIcon.tsx'
import type { SolrCore } from './SolrCore.ts'
import { solrLookClassNames, solrLookStyle } from './SolrLook.ts'

interface SolrCoreEmblemProps {
  solrCore: SolrCore
  /** Sprache des Anzeigenamens, ohne Angabe die Standardsprache */
  language?: LanguageCode
}

/**
 * Das Emblem eines Solr-Kerns: Symbol und Anzeigename auf dem Verlauf, ohne Namen der key. Kein Badge: Kerne gibt es
 * eine Handvoll, Badges hundertfach, deshalb eine eigene Komponente mit eigenen Klassen (solr-core-emblem in
 * basic.css). Farben und Verlauf kommen über solr-look aus dem look des Kerns.
 */
export function SolrCoreEmblem({ solrCore, language }: SolrCoreEmblemProps) {
  const classNames = ['solr-core-emblem', ...solrLookClassNames(solrCore.look)]

  return (
    <span className={classNames.join(' ')} style={solrLookStyle(solrCore.look)}>
      {solrCore.look.symbol && <PackIcon symbol={solrCore.look.symbol} />}
      <span>{translate(solrCore.displayName || null, language) || solrCore.key}</span>
    </span>
  )
}
