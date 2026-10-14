import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { AbasOpenButton } from '../../components/AbasOpenButton.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import type { SearchResultListProps } from '../search/searchCores.ts'
import { SearchHitReasons } from '../search/SearchHitReasons.tsx'
import { SolrHookValueBadge } from '../solrmanager/SolrHookValueBadge.tsx'
import { SolrHookValues } from '../solrmanager/SolrHookValues.tsx'
import {
  KONTAKTE_COMPANY_NAME_OF_CONTACT_PERSON_FIELD_NAME,
  KONTAKTE_CONTACT_KIND_COMPANY,
  KONTAKTE_CONTACT_KIND_FIELD_NAME,
  KONTAKTE_CORE_KEY,
  KONTAKTE_NAME_FIELD_NAME,
  KONTAKTE_SYNC_STATUS_FIELD_NAME,
  kontakteFieldLabel,
  kontakteMappePathOf,
  kontakteValueOf,
  kontakteValuesOf,
} from './Kontakte.ts'

/**
 * Die Fakten einer Trefferzeile in dieser Reihenfolge, nach KontakteTreffer der Vorlage: die Kunden- und
 * Lieferantennummer immer zuerst, denn an den Nummern erkennt das Haus einen Kontakt. Die beiden sind Hooks, ihre Werte
 * sind Badges mit dem Ziel ihres Hooks und stehen deshalb nicht noch einmal unter den Hooks. Danach am Ansprechpartner
 * immer der Name seiner Firma (User-Vorgabe der Vorlage), den das Backend zur Lesezeit mitgibt.
 */
const FACT_FIELD_NAMES: readonly string[] = ['kundenNummer', 'lieferantenNummer', KONTAKTE_COMPANY_NAME_OF_CONTACT_PERSON_FIELD_NAME, 'nort', 'funktion', 'tele', 'email']

/**
 * Die Treffer des Kerns KONTAKTE in der Suche, nach KontakteTreffer der Vorlage: je Treffer das Symbol für Firma oder
 * Ansprechpartner, der Name als Weg in die Kundenmappe (ohne Kundennummer in die Lieferantenmappe; ein Ansprechpartner
 * in die Mappe seiner Firma), der ABAS-Knopf mit der Sternzeit des 19ers, darunter die Fakten, die übrigen Hooks mit
 * ihren Werten und der Treffergrund. Ein Hook-Wert loggt sich als Filter ein oder öffnet die Mappe seines Hooks
 * (SolrHookValueBadge). Keine Aktionen: Im Spiegelkern schreibt kein Mensch.
 */
export function KontakteSearchResultList({ hits, origin, onFilter }: SearchResultListProps) {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const navigate = useNavigate()
  const { solrHooks, solrFields } = useMasterData()

  return (
    <div className="search-hits">
      {hits.map((hit) => {
        const document = hit.fieldValues
        const id = typeof document.id === 'string' ? document.id : ''
        const name = kontakteValueOf(document, KONTAKTE_NAME_FIELD_NAME) || id
        const isCompany = kontakteValueOf(document, KONTAKTE_CONTACT_KIND_FIELD_NAME) === KONTAKTE_CONTACT_KIND_COMPANY
        const kindLabel = isCompany ? t('kontakte.search.company') : t('kontakte.search.person')
        const mappePath = kontakteMappePathOf(document)
        const syncStatus = kontakteValueOf(document, KONTAKTE_SYNC_STATUS_FIELD_NAME)
        const facts = FACT_FIELD_NAMES.map((fieldName) => ({ fieldName, values: kontakteValuesOf(document[fieldName]) })).filter((fact) => fact.values.length > 0)
        const hooksWithValues = (solrHooks ?? []).filter((hook) => !FACT_FIELD_NAMES.includes(hook.key) && kontakteValuesOf(document[hook.key]).length > 0)

        return (
          <div key={id} className="search-hit">
            <span className="search-hit-symbol">
              <Hoverlay text={kindLabel}>
                <Icon name={isCompany ? 'kontaktFirma' : 'kontaktPerson'} />
              </Hoverlay>
            </span>

            <div className="search-hit-body">
              <div className="search-hit-head">
                {mappePath !== null ? (
                  <button type="button" className="search-hit-name" onClick={() => navigate(mappePath, { state: origin })}>
                    {name}
                  </button>
                ) : (
                  <span className="search-hit-name">{name}</span>
                )}
                <AbasOpenButton compact sternzeit={id} hint={t('kontakte.search.abasHint', { kind: kindLabel, name })} />
                {syncStatus !== '' && syncStatus !== 'OK' && <span className="badge text-bg-danger">{t('kontakte.search.syncStatus', { status: syncStatus })}</span>}
              </div>

              {facts.length > 0 && (
                <div className="search-hit-facts">
                  {facts.map((fact) => {
                    const hook = (solrHooks ?? []).find((candidate) => candidate.key === fact.fieldName)
                    return (
                      <span key={fact.fieldName} className="search-hit-fact">
                        <span className="text-muted">
                          {fact.fieldName === KONTAKTE_COMPANY_NAME_OF_CONTACT_PERSON_FIELD_NAME
                            ? t('kontakte.search.company')
                            : kontakteFieldLabel(solrFields, solrHooks, fact.fieldName, language)}
                          :
                        </span>{' '}
                        {hook
                          ? fact.values.map((value) => (
                              <SolrHookValueBadge
                                key={value}
                                solrHook={hook}
                                value={value}
                                origin={origin}
                                onSearchFilter={onFilter}
                                language={language}
                                className="search-hit-hook-value"
                              />
                            ))
                          : fact.values.join(', ')}
                      </span>
                    )
                  })}
                </div>
              )}

              {hooksWithValues.length > 0 && (
                <div className="search-hit-hooks">
                  {hooksWithValues.map((hook) => (
                    <SolrHookValues
                      key={hook.key}
                      solrHook={hook}
                      values={kontakteValuesOf(document[hook.key])}
                      language={language}
                      origin={origin}
                      onSearchFilter={onFilter}
                      badgeClassName="search-hit-hook-value"
                    />
                  ))}
                </div>
              )}

              <SearchHitReasons reasons={hit.reasons} coreKey={KONTAKTE_CORE_KEY} language={language} />
            </div>
          </div>
        )
      })}
    </div>
  )
}
