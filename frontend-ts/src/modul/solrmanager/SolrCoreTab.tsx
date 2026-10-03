import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import type { LanguageCode } from '../../branding/languages.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { SolrCoreEmblem } from './SolrCoreEmblem.tsx'
import { SolrFieldSheet } from './SolrFieldSheet.tsx'
import { groupSolrHooks } from './SolrHook.ts'
import { SolrHookCatalog } from './SolrHookCatalog.tsx'

/**
 * Der Tab eines Kerns unter /solrmanager/<key>: das Emblem, darunter alle Felder des Kerns wie in einer
 * Tabellenkalkulation (SolrFieldSheet), darunter alle Hooks als Katalog in einer Hoverlay-Box, je Gruppe die Liste,
 * in jedem Kern gleich. Die Flaggen der ContentBox wählen die Sprache der Beschreibungen.
 *
 * Die ID des Dokuments ist immer das Feld id, das mit jedem Kern entsteht. Es gibt hier nichts zu wählen: Was
 * hineinkommt, steht in der Beschreibung des Feldes.
 */
export function SolrCoreTab() {
  const { t } = useTranslation()
  const { coreKey = '' } = useParams()
  const { solrCores, solrHooks, solrHookGroups, solrFields, errorMessage, reloadMasterData } = useMasterData()
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)
  const solrCore = solrCores?.find((candidate) => candidate.key === coreKey) ?? null
  const coreFields = solrFields?.filter((solrField) => solrField.coreKey === coreKey) ?? null

  let footer
  if (errorMessage !== null) {
    footer = <span className="text-danger">{errorMessage}</span>
  } else if (solrCores === null || coreFields === null) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else if (solrCore === null) {
    footer = <span className="text-danger">{t('solrmanager.cores.core.notFound', { key: coreKey })}</span>
  } else {
    footer = (
      <span>{t('solrmanager.cores.core.footer', { fields: coreFields.length, hooks: solrHooks?.length ?? 0 })}</span>
    )
  }

  return (
    <ContentBox
      title={
        solrCore
          ? `${translate(solrCore.displayName) || solrCore.key} (${solrCore.key})`
          : t('solrmanager.cores.core.title', { key: coreKey })
      }
      footer={footer}
      language={{ value: language, onChange: setLanguage }}
    >
      {solrCore !== null && coreFields !== null && (
        <div className="d-flex flex-column gap-3">
          <div>
            <SolrCoreEmblem solrCore={solrCore} language={language} />
          </div>
          <div>
            <h3 className="h6 mb-2">{t('solrmanager.cores.core.fields')}</h3>
            {/* key: beim Wechsel des Kerns wird die Tabelle neu aufgebaut und vergisst ihre Entwürfe */}
            <SolrFieldSheet
              key={coreKey}
              coreKey={coreKey}
              solrFields={coreFields}
              language={language}
              onSaved={reloadMasterData}
            />
          </div>
          <div>
            <h3 className="h6 mb-2">{t('solrmanager.cores.core.hooks')}</h3>
            {solrHooks !== null && solrHooks.length === 0 && <p className="mb-0">{t('solrmanager.cores.core.noHooks')}</p>}
            {solrHooks !== null && solrHooks.length > 0 && (
              // alle Hooks als Katalog in einer Hoverlay-Box: je Gruppe die Liste, dieselbe wie in der Hook-Auswahl
              <div className="hoverlay-box solr-hook-catalog-box">
                <SolrHookCatalog
                  groups={groupSolrHooks(solrHooks, solrHookGroups ?? [])}
                  language={language}
                  emptyText={t('solrmanager.cores.core.noHooks')}
                />
              </div>
            )}
          </div>
        </div>
      )}
    </ContentBox>
  )
}
