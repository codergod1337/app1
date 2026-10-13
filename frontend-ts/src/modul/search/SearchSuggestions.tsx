import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { SolrHookChip } from '../solrmanager/SolrHookChip.tsx'
import type { SearchOffer } from './Search.ts'

interface SearchSuggestionsProps {
  /** die Angebote aus den Gründen der Treffer aller angewählten Kerne, ohne schon gesetzte Filter, ohne Doppelte */
  offers: readonly SearchOffer[]
  language: LanguageCode
  onPick: (offer: SearchOffer) => void
}

/**
 * Die Angebote über der Eingabe (User 2026-10-09): je Hook-Wert, den ein Suchwort in einem Treffer getroffen hat, ein
 * Hook-Chip. Klick loggt ihn als Filter ein und nimmt das Suchwort aus dem Suchtext, Tab im Suchfeld den ersten. Die
 * Angebote stecken schon in der Antwort der Suche (die Gründe), es gibt keinen eigenen Request dafür.
 */
export function SearchSuggestions({ offers, language, onPick }: SearchSuggestionsProps) {
  const { t } = useTranslation()
  const { solrHooks } = useMasterData()

  if (offers.length === 0) {
    return null
  }

  return (
    <div className="search-offers">
      <span className="small text-muted">{t('search.offers.title')}</span>
      {offers.map((offer) => {
        const hook = solrHooks?.find((candidate) => candidate.key === offer.fieldName)
        if (!hook) {
          return null
        }
        return (
          <SolrHookChip
            key={`${offer.coreKey}:${offer.fieldName}=${offer.value}`}
            solrHook={hook}
            value={offer.value}
            language={language}
            onClick={() => onPick(offer)}
          />
        )
      })}
      <span className="small text-muted">{t('search.offers.tabHint')}</span>
    </div>
  )
}
