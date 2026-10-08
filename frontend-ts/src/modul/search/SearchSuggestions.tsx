import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { solrCoreColorStyle, solrCoreOfKey } from '../solrmanager/SolrCore.ts'
import { SolrHookChip } from '../solrmanager/SolrHookChip.tsx'
import type { SearchCoreFilterOffer, SearchFilterOffer } from './Search.ts'

interface SearchSuggestionsProps {
  /** die Angebote aller angewählten Kerne, ohne die schon gesetzten Filter, ohne Doppelte */
  offers: readonly SearchCoreFilterOffer[]
  language: LanguageCode
  onPick: (offer: SearchFilterOffer) => void
}

/**
 * Die Filterangebote der Vervollständigung über der Eingabe, nach der Vorlage: je Angebot ein Chip, Hooks als
 * Hook-Chip, andere Felder als gestrichelte Pille mit Vorschaubild und Zusatz, wenn der Kern welche mitgibt. Klick
 * loggt das Angebot als Filter ein, Tab im Suchfeld das erste. Die kompakten Treffer der Vervollständigung zeigt die
 * Seite nicht noch einmal, die Suche darunter läuft ohnehin mit.
 */
export function SearchSuggestions({ offers, language, onPick }: SearchSuggestionsProps) {
  const { t } = useTranslation()
  const { solrHooks, fileSubClasses, solrCores } = useMasterData()

  if (offers.length === 0) {
    return null
  }

  return (
    <div className="search-offers">
      <span className="small text-muted">{t('search.offers.title')}</span>
      {offers.map(({ coreKey, offer }) => {
        const key = `${coreKey}:${offer.fieldName}=${offer.value}`
        const hook = solrHooks?.find((candidate) => candidate.key === offer.fieldName)
        if (hook) {
          return <SolrHookChip key={key} solrHook={hook} value={offer.value} language={language} onClick={() => onPick(offer)} />
        }
        const fileSubClass = offer.label === null ? undefined : fileSubClasses?.find((candidate) => candidate.key === offer.label)
        const extra = fileSubClass ? translate(fileSubClass.displayName, language) || fileSubClass.key : offer.label
        // ein Feld des Kerns: die Pille trägt die Farbe des Kerns, aus dem das Angebot kommt
        const solrCore = solrCoreOfKey(solrCores, coreKey)
        return (
          <button
            key={key}
            type="button"
            className={solrCore ? 'search-filter-chip search-filter-chip-core search-offer' : 'search-filter-chip search-offer'}
            style={solrCore ? solrCoreColorStyle(solrCore) : undefined}
            aria-label={t('search.filters.addValue', { field: offer.fieldName, value: offer.value })}
            onClick={() => onPick(offer)}
          >
            {offer.imagePreviewBase64Png !== null && (
              <img className="search-offer-preview" src={`data:image/png;base64,${offer.imagePreviewBase64Png}`} alt="" />
            )}
            <span className="search-filter-chip-field">{offer.fieldName}</span>
            <span>{offer.value}</span>
            {extra !== null && extra !== '' && <span className="search-offer-extra">({extra})</span>}
          </button>
        )
      })}
      <span className="small text-muted">{t('search.offers.tabHint')}</span>
    </div>
  )
}
