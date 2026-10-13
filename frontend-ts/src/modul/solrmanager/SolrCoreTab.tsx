import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import type { LanguageCode } from '../../branding/languages.ts'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { LanguageFlags } from '../../components/LanguageFlags.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import { Popup } from '../../components/Popup.tsx'
import { isSolrFieldOfCore, isSolrHookField } from './SolrField.ts'
import { SolrFieldSheet } from './SolrFieldSheet.tsx'
import { SolrSyncStatus } from './SolrSyncStatus.tsx'

/**
 * Der Tab eines Kerns unter /solrmanager/<key> als Matrix-Darstellung (User 2026-10-09): ohne ContentBox über die
 * ganze Breite. Oben eine schmale Leiste mit „Anzeigename (KEY)“, dem Plus für ein neues Feld, den Flaggen für die
 * Sprache der Beschreibungen und den Zählern. Darunter alles, was der Kern hat, wie in einer Tabellenkalkulation
 * (SolrFieldSheet): erst seine Felder, unter einer Trennlinie seine Hooks, die in jedem Kern liegen und je Kern
 * Lesen, Suche und Reihenfolge bekommen. Die Seite scrollt wie jede andere, die Kopfzeile der Tabelle bleibt unter
 * dem Subheader stehen. Der Abgleich mit Solr steht in einem Popup, der Knopf dafür sitzt in der Leiste hinter den
 * Flaggen (User 2026-10-09).
 *
 * Felder, die in jedem Kern liegen (coreKey null, etwa id, cursorDate und userGrantedAccess), erscheinen in jedem
 * Kern-Tab, id auf Platz 1.
 */
export function SolrCoreTab() {
  const { t } = useTranslation()
  const { coreKey = '' } = useParams()
  const { solrCores, solrFields, errorMessage, reloadMasterData } = useMasterData()
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)
  // Das Plus in der Leiste öffnet unten im Sheet die leere Zeile für ein neues Feld
  const [newFieldRowOpen, setNewFieldRowOpen] = useState(false)
  // Das Popup mit dem Abgleich mit Solr
  const [syncOpen, setSyncOpen] = useState(false)
  const solrCore = solrCores?.find((candidate) => candidate.key === coreKey) ?? null
  const coreFields = solrFields?.filter((solrField) => isSolrFieldOfCore(solrField, coreKey)) ?? null

  let status
  if (errorMessage !== null) {
    status = <span className="text-danger">{errorMessage}</span>
  } else if (solrCores === null || coreFields === null) {
    status = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else if (solrCore === null) {
    status = <span className="text-danger">{t('solrmanager.cores.core.notFound', { key: coreKey })}</span>
  } else {
    const hookCount = coreFields.filter((solrField) => isSolrHookField(solrField)).length
    status = (
      <span>{t('solrmanager.cores.core.footer', { fields: coreFields.length - hookCount, hooks: hookCount })}</span>
    )
  }

  return (
    <>
      <div className="solr-field-matrix">
        <div className="matrix-message">
          <strong>
            {solrCore
              ? `${translate(solrCore.displayName) || solrCore.key} (${solrCore.key})`
              : t('solrmanager.cores.core.title', { key: coreKey })}
          </strong>
          <NewEntityButton
            entity="SolrField"
            disabled={solrCore === null || coreFields === null || newFieldRowOpen}
            onClick={() => setNewFieldRowOpen(true)}
          />
          <LanguageFlags value={language} onChange={setLanguage} />
          <button type="button" className="button-other" disabled={solrCore === null} onClick={() => setSyncOpen(true)}>
            <Icon name="refresh" /> {t('solrmanager.sync.title')}
          </button>
          <span className="ms-auto">{status}</span>
        </div>
        {solrCore !== null && coreFields !== null && (
          // key: beim Wechsel des Kerns wird die Tabelle neu aufgebaut und vergisst ihre Entwürfe
          <SolrFieldSheet
            key={coreKey}
            coreKey={coreKey}
            solrFields={coreFields}
            language={language}
            onSaved={reloadMasterData}
            newRowOpen={newFieldRowOpen}
            onNewRowClose={() => setNewFieldRowOpen(false)}
          />
        )}
      </div>
      <Popup open={syncOpen} title={t('solrmanager.sync.title')} onClose={() => setSyncOpen(false)}>
        {/* key: beim Wechsel des Kerns den Bericht des neuen holen */}
        <SolrSyncStatus key={coreKey} coreKey={coreKey} />
      </Popup>
    </>
  )
}
