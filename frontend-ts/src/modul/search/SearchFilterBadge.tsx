import type { KeyboardEvent, ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import type { LanguageCode } from '../../branding/languages.ts'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import type { OriginState } from '../../components/Origin.ts'
import { ARTIKEL_CORE_KEY } from '../artikel/Artikel.ts'
import { DMS_CORE_KEY, FILE_SUB_CLASS_FIELD_NAME } from '../dms/Dms.ts'
import { KONTAKTE_CORE_KEY } from '../kontakte/Kontakte.ts'
import { solrCoreColorStyle, solrCoreOfFieldName, solrCoreOfKey, type SolrCore } from '../solrmanager/SolrCore.ts'
import { solrFieldDisplayNameOf } from '../solrmanager/SolrField.ts'
import { solrHookValueLink, type SolrHook } from '../solrmanager/SolrHook.ts'

interface SearchFilterBadgeProps {
  fieldName: string
  value: string
  /** woran man den Wert erkennt, z. B. der Firmenname zur Kundennummer */
  additionText?: string | null
  /** ein Vorschaubild als Base64-PNG ohne Präfix */
  previewBase64Png?: string | null
  /** set: der gesetzte Filter, schmal, Zusatz und Bild im Hoverlay; offer: das Angebot, Zusatz und Bild sofort sichtbar */
  variant: 'set' | 'offer'
  /** das Angebot, das Tab einloggt, sichtbar betont */
  active?: boolean
  /** ein Angebot übernehmen */
  onPick?: () => void
  /** einen gesetzten Filter entfernen */
  onRemove?: () => void
  /** die Herkunft für eine Mappe als Ziel des Werts */
  origin?: OriginState
  language: LanguageCode
}

/**
 * Zu welchem Kern ein Filter gehört, nur für seine Farbe, nach kernZuFeld der Vorlage, aber aus den Stammdaten: Ein
 * Hook gehört dem Kern, in dessen Mappe er führt (Artikelmappe ARTIKEL, Kunden- und Lieferantenmappe KONTAKTE), ein
 * Hook mit der Suche als Ziel und die Dateiart dem DMS, ein normales Feld seinem Kern. Gefiltert wird trotzdem in jedem
 * Kern, die Farbe entscheidet nichts.
 */
function solrCoreOfFilter(
  fieldName: string,
  solrHook: SolrHook | undefined,
  solrCores: readonly SolrCore[] | null,
  solrFields: Parameters<typeof solrCoreOfFieldName>[1],
): SolrCore | null {
  if (solrHook !== undefined) {
    switch (solrHook.valueBadgeLinkTarget) {
      case 'ARTIKELMAPPE':
        return solrCoreOfKey(solrCores, ARTIKEL_CORE_KEY)
      case 'KUNDENMAPPE':
      case 'LIEFERANTENMAPPE':
        return solrCoreOfKey(solrCores, KONTAKTE_CORE_KEY)
      default:
        return solrCoreOfKey(solrCores, DMS_CORE_KEY)
    }
  }
  if (fieldName === FILE_SUB_CLASS_FIELD_NAME) {
    return solrCoreOfKey(solrCores, DMS_CORE_KEY)
  }
  return solrCoreOfFieldName(solrCores, solrFields, fieldName)
}

/**
 * Die eine Darstellung eines Filters der Suche, nach der FilterBadge der Vorlage (User 2026-10-13): als gesetzter
 * Filter über der Eingabe und als Angebot. Ein Filter ist ein Dreiklang aus Kern, Feld und Wert, dazu, woran man ihn
 * wiedererkennt (Zusatz, Bild). Der Kern färbt die Badge. Führt der Hook in eine Mappe, ist der Wert ein Link dorthin;
 * der Rest der Badge setzt beim Angebot den Filter. Ein Angebot ohne Zusatz und Bild bleibt schmal: Eine leere zweite
 * Zeile behauptete eine Information, die es nicht gibt. Das Aussehen steht in basic.css (search-filter-badge).
 */
export function SearchFilterBadge({ fieldName, value, additionText = null, previewBase64Png = null, variant, active = false, onPick, onRemove, origin, language }: SearchFilterBadgeProps) {
  const { t } = useTranslation()
  const { solrHooks, solrCores, solrFields } = useMasterData()
  const solrHook = solrHooks?.find((candidate) => candidate.key === fieldName)
  const solrCore = solrCoreOfFilter(fieldName, solrHook, solrCores, solrFields)
  const coreLabel = solrCore ? translate(solrCore.displayName, language) || solrCore.key : ''
  const fieldLabel = solrHook
    ? translate(solrHook.displayName, language) || solrHook.key
    : (solrCore ? solrFieldDisplayNameOf(solrFields ?? [], solrCore.key, fieldName, language) : null) ?? fieldName
  const link = solrHook !== undefined && solrHook.valueBadgeLinkTarget !== 'SEARCH' ? solrHookValueLink(solrHook, value) : null
  const hasExtras = (additionText !== null && additionText !== '') || (previewBase64Png !== null && previewBase64Png !== '')
  const wide = variant === 'offer' && hasExtras
  const preview = previewBase64Png ? <img className="search-filter-badge-preview" src={`data:image/png;base64,${previewBase64Png}`} alt="" /> : null

  const content = (
    <>
      {wide && preview}
      <span className="search-filter-badge-content">
        <span className="search-filter-badge-line">
          {coreLabel !== '' && <span className="search-filter-badge-core">{coreLabel}</span>}
          <span className="search-filter-badge-field">{fieldLabel}</span>
          {link !== null ? (
            // nur der Wert ist der Link, sein Klick setzt keinen Filter
            <Link to={link.to} state={link.state ?? origin} className="search-filter-badge-value search-filter-badge-value-link" onClick={(event) => event.stopPropagation()}>
              {value}
            </Link>
          ) : (
            <span className="search-filter-badge-value">{value}</span>
          )}
        </span>
        {wide && additionText && <span className="search-filter-badge-addition">{additionText}</span>}
      </span>
    </>
  )

  // der Hinweis nur beim schmalen Filter: Beim dicken Angebot steht alles schon da
  const hint: ReactNode =
    !wide && (hasExtras || link !== null) ? (
      <span className="search-filter-badge-hint">
        {preview}
        <span className="d-flex flex-column">
          <span className="small">
            {fieldLabel}: {value}
          </span>
          {additionText && <span>{additionText}</span>}
          {link !== null && <span className="small">{t(`search.filters.linkHint.${solrHook?.valueBadgeLinkTarget ?? 'SEARCH'}`)}</span>}
        </span>
      </span>
    ) : (
      ''
    )

  const classes = ['search-filter-badge', variant === 'offer' ? 'search-filter-badge-offer' : '', wide ? 'search-filter-badge-wide' : '', active ? 'search-filter-badge-active' : '']
    .filter((entry) => entry !== '')
    .join(' ')
  const style = solrCoreColorStyle(solrCore)

  function keyDown(event: KeyboardEvent<HTMLSpanElement>) {
    if ((event.key === 'Enter' || event.key === ' ') && onPick) {
      event.preventDefault()
      onPick()
    }
  }

  let badge
  if (onPick && !onRemove && link !== null) {
    // ein Link in einem button ist kein gültiges HTML: das Angebot wird ein span mit der Rolle eines Knopfs
    badge = (
      <span role="button" tabIndex={0} className={classes} style={style} onClick={onPick} onKeyDown={keyDown} aria-label={t('search.filters.addValue', { field: fieldLabel, value })}>
        {content}
      </span>
    )
  } else if (onPick && !onRemove) {
    badge = (
      <button type="button" className={classes} style={style} onClick={onPick} aria-label={t('search.filters.addValue', { field: fieldLabel, value })}>
        {content}
      </button>
    )
  } else {
    badge = (
      <span className={classes} style={style}>
        {content}
        {onRemove && <DeleteButton label={t('search.filters.remove', { field: fieldLabel, value })} onClick={onRemove} />}
      </span>
    )
  }

  return hint === '' ? badge : <Hoverlay text={hint}>{badge}</Hoverlay>
}
