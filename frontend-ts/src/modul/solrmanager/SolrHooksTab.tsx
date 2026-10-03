import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { positionsByKey } from '../../components/positions.ts'
import { AllSolrHooksAdminList } from './AllSolrHooksAdminList.tsx'
import type { EditSolrHookState } from './EditSolrHookTab.tsx'
import { changeSolrHookPositions, deleteSolrHook, type SolrHook } from './SolrHook.ts'
import { SolrHookPicker } from './SolrHookPicker.tsx'

/** Beim Verlassen dieses Tabs mitgeben, damit der header „zurück zu Hooks“ anbietet. Das label ist ein Schlüssel des Katalogs. */
const ORIGIN_STATE: OriginState = { origin: { path: '/solrmanager/hooks', label: 'solrmanager.tabs.hooks' } }

/** Tab „Hooks“ des SolrManagers: alle Hooks, die jeder Kern bekommt. */
export function SolrHooksTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { language } = useLanguage()
  const { solrHooks, solrHookGroups, errorMessage, reloadMasterData } = useMasterData()
  // Nach Drag and Drop sofort die neue Reihenfolge zeigen, ohne auf das Neuladen zu warten
  const [reorderedSolrHooks, setReorderedSolrHooks] = useState<SolrHook[] | null>(null)
  const [positionErrorMessage, setPositionErrorMessage] = useState<string | null>(null)
  // Sicherheitsabfrage vor dem Löschen: null, solange keine offen ist
  const [solrHookToDelete, setSolrHookToDelete] = useState<SolrHook | null>(null)

  const shownSolrHooks = reorderedSolrHooks ?? solrHooks

  /**
   * Positionen neu durchzählen (1, 2, 3 …) und alle auf einmal speichern, danach die Stammdaten für alle Module neu
   * laden. Klappt das nicht: nur neu laden.
   */
  function reorderSolrHooks(newOrder: SolrHook[]) {
    const positions = positionsByKey(newOrder, (solrHook) => solrHook.key)
    setReorderedSolrHooks(newOrder.map((solrHook) => ({ ...solrHook, listingPosition: positions[solrHook.key] })))
    setPositionErrorMessage(null)
    changeSolrHookPositions(positions)
      .then((result) => setReorderedSolrHooks(result.data))
      .catch((error: unknown) => {
        setPositionErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setReorderedSolrHooks(null)
      })
      .finally(reloadMasterData)
  }

  let footer
  if (errorMessage !== null || positionErrorMessage !== null) {
    footer = <span className="text-danger">{errorMessage ?? positionErrorMessage}</span>
  } else if (shownSolrHooks === null) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else {
    footer = <span>{t('solrmanager.hooks.count', { count: shownSolrHooks.length })}</span>
  }

  function editSolrHook(solrHook: SolrHook) {
    const state: EditSolrHookState = { ...ORIGIN_STATE, solrHookKey: solrHook.key }
    navigate('/solrmanager/hooks/edit', { state })
  }

  // rechts im Kopf: einen Hook suchen und bearbeiten (bei dieser Zahl schneller als Scrollen), daneben ein neuer Hook
  const headerActions = (
    <span className="d-inline-flex align-items-center gap-2">
      <SolrHookPicker label={t('solrmanager.hooks.search')} language={language} onPick={editSolrHook} />
      <NewEntityButton entity="SolrHook" onClick={() => navigate('/solrmanager/hooks/new', { state: ORIGIN_STATE })} />
    </span>
  )

  return (
    <>
      <ContentBox title={t('solrmanager.hooks.title')} actions={headerActions} footer={footer}>
        {shownSolrHooks !== null && (
          <AllSolrHooksAdminList
            solrHooks={shownSolrHooks}
            solrHookGroups={solrHookGroups ?? []}
            onEdit={editSolrHook}
            onDelete={setSolrHookToDelete}
            onReorder={reorderSolrHooks}
          />
        )}
      </ContentBox>
      <DeletePopup
        open={solrHookToDelete !== null}
        title={t('solrmanager.hooks.deleteTitle')}
        onConfirm={() => deleteSolrHook(solrHookToDelete?.key ?? '')}
        onDeleted={() => {
          setReorderedSolrHooks(null)
          reloadMasterData()
        }}
        onClose={() => setSolrHookToDelete(null)}
      >
        <Trans
          i18nKey="solrmanager.hooks.deleteQuestion"
          values={{ key: solrHookToDelete?.key ?? '' }}
          components={{ strong: <strong /> }}
        />
      </DeletePopup>
    </>
  )
}
