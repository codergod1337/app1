import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import type { OriginState } from '../../components/Origin.ts'
import type { SearchOffer } from './Search.ts'
import { SearchFilterBadge } from './SearchFilterBadge.tsx'

interface SearchSuggestionsProps {
  /** die Filter-Angebote aller angewählten Kerne und des Auflösers, ohne schon gesetzte Filter, ohne Doppelte */
  offers: readonly SearchOffer[]
  /** das erste Angebot ist das, was Tab einloggt; nicht, solange unter der Eingabe Vervollständigungen offen sind */
  firstOfferActive: boolean
  /** die Herkunft für eine Mappe als Ziel eines Werts */
  origin: OriginState
  language: LanguageCode
  onPickOffer: (offer: SearchOffer) => void
}

/**
 * Die Filter-Angebote über der Eingabe (User 2026-10-09, nach der Vorlage 2026-10-13) als dicke SearchFilterBadges.
 * Klick oder Tab loggt eines als Filter ein. Die Vervollständigungen des getippten Worts stehen nicht hier, sondern
 * wie in jeder Oberfläche als Liste direkt unter dem Suchfeld (Search.tsx). Alles kommt aus den Antworten der Suche.
 */
export function SearchSuggestions({ offers, firstOfferActive, origin, language, onPickOffer }: SearchSuggestionsProps) {
  const { t } = useTranslation()

  if (offers.length === 0) {
    return null
  }

  return (
    <div className="search-offers">
      <span className="small text-muted">{t('search.offers.title')}</span>
      {offers.map((offer, index) => (
        <SearchFilterBadge
          key={`${offer.fieldName}=${offer.value}`}
          fieldName={offer.fieldName}
          value={offer.value}
          additionText={offer.additionText}
          previewBase64Png={offer.previewBase64Png}
          variant="offer"
          active={firstOfferActive && index === 0}
          onPick={() => onPickOffer(offer)}
          origin={origin}
          language={language}
        />
      ))}
      <span className="small text-muted">{t('search.offers.tabHint')}</span>
    </div>
  )
}
