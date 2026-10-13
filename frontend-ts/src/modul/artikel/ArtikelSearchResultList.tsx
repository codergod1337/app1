import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { AbasOpenButton } from '../../components/AbasOpenButton.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import type { SearchResultListProps } from '../search/searchCores.ts'
import { SearchHitReasons } from '../search/SearchHitReasons.tsx'
import { SolrHookValues } from '../solrmanager/SolrHookValues.tsx'
import {
  ARTIKEL_CORE_KEY,
  ARTIKEL_NAME_FIELD_NAME,
  ARTIKEL_PREVIEW_FIELD_NAME,
  ARTIKEL_SEARCH_TERM_FIELD_NAME,
  ARTIKEL_STERNZEIT_HOOK_NAME,
  ARTIKEL_SYNC_STATUS_FIELD_NAME,
  artikelFieldLabel,
  artikelMasterNumberOf,
  artikelPath,
  artikelValueOf,
  artikelValuesOf,
} from './Artikel.ts'

/** Die Fakten einer Trefferbox, nach den Stammdaten der Übersicht */
const FACT_FIELD_NAMES: readonly string[] = [ARTIKEL_SEARCH_TERM_FIELD_NAME, 'zmart']

/**
 * Die Treffer des Kerns ARTIKEL in der Suche: je Treffer das Vorschaubild oder das Artikelsymbol, der Name als Weg in
 * die Mappe mit der Nummer daneben, Suchbegriff und Artikelart in einer Zeile, Master oder Slave als Badge, die Hooks
 * mit ihren Werten, darunter der Treffergrund. Ein Klick auf einen Hook-Wert loggt ihn als Filter ein, hat sein Hook
 * eine Mappe als Ziel, öffnet er die Mappe (SolrHookValueBadge). Keine
 * Aktionen: Im Spiegelkern schreibt kein Mensch.
 */
export function ArtikelSearchResultList({ hits, origin, onFilter }: SearchResultListProps) {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const navigate = useNavigate()
  const { solrHooks, solrFields } = useMasterData()

  return (
    <div className="search-hits">
      {hits.map((hit) => {
        const document = hit.fieldValues
        const id = typeof document.id === 'string' ? document.id : ''
        const name = artikelValueOf(document, ARTIKEL_NAME_FIELD_NAME) || id
        const preview = document[ARTIKEL_PREVIEW_FIELD_NAME]
        const masterNumber = artikelMasterNumberOf(document)
        const syncStatus = artikelValueOf(document, ARTIKEL_SYNC_STATUS_FIELD_NAME)
        const facts = FACT_FIELD_NAMES.map((fieldName) => ({ fieldName, label: artikelFieldLabel(solrFields, fieldName, undefined, language), value: artikelValueOf(document, fieldName) })).filter(
          (fact) => fact.value !== '',
        )
        const hooksWithValues = (solrHooks ?? []).filter((hook) => artikelValuesOf(document[hook.key]).length > 0)

        return (
          <div key={id} className="search-hit">
            <span className="search-hit-symbol">
              {typeof preview === 'string' && preview !== '' ? (
                <img className="search-hit-preview" src={`data:image/png;base64,${preview}`} alt={name} />
              ) : (
                <Icon name="artikel" />
              )}
            </span>

            <div className="search-hit-body">
              <div className="search-hit-head">
                <button type="button" className="search-hit-name" onClick={() => navigate(artikelPath(id), { state: origin })}>
                  {name}
                </button>
                <code className="small">{id}</code>
                <AbasOpenButton compact sternzeit={artikelValueOf(document, ARTIKEL_STERNZEIT_HOOK_NAME)} hint={t('artikel.status', { artikelNummer: id })} />
                {masterNumber !== '' && (
                  <span className={masterNumber === id ? 'badge text-bg-warning' : 'badge text-bg-secondary'}>
                    {masterNumber === id ? t('artikel.search.master') : t('artikel.search.slaveOf', { masterNumber })}
                  </span>
                )}
                {syncStatus !== '' && syncStatus !== 'OK' && <span className="badge text-bg-danger">{t('artikel.overview.syncStatus', { status: syncStatus })}</span>}
              </div>

              {facts.length > 0 && (
                <div className="search-hit-facts">
                  {facts.map((fact) => (
                    <span key={fact.fieldName} className="search-hit-fact">
                      <span className="text-muted">{fact.label}:</span> {fact.value}
                    </span>
                  ))}
                </div>
              )}

              {hooksWithValues.length > 0 && (
                <div className="search-hit-hooks">
                  {hooksWithValues.map((hook) => (
                    <SolrHookValues
                      key={hook.key}
                      solrHook={hook}
                      values={artikelValuesOf(document[hook.key])}
                      language={language}
                      origin={origin}
                      onSearchFilter={onFilter}
                      badgeClassName="search-hit-hook-value"
                    />
                  ))}
                </div>
              )}

              <SearchHitReasons reasons={hit.reasons} coreKey={ARTIKEL_CORE_KEY} language={language} />
            </div>
          </div>
        )
      })}
    </div>
  )
}
