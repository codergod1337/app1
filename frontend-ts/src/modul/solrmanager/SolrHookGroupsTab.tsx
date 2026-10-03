import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { positionsByKey } from '../../components/positions.ts'
import { AllSolrHookGroupsAdminList } from './AllSolrHookGroupsAdminList.tsx'
import type { EditSolrHookGroupState } from './EditSolrHookGroupTab.tsx'
import { changeSolrHookGroupPositions, deleteSolrHookGroup, type SolrHookGroup } from './SolrHookGroup.ts'

/** Beim Verlassen dieses Tabs mitgeben, damit der header „zurück zu Hook-Gruppen“ anbietet. Das label ist ein Schlüssel des Katalogs. */
const ORIGIN_STATE: OriginState = { origin: { path: '/solrmanager/hookgroups', label: 'solrmanager.tabs.hookGroups' } }

/** Tab „Hook-Gruppen“ des SolrManagers: die Gruppen, nach denen jede Anzeige die Hooks ordnet. */
export function SolrHookGroupsTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { solrHookGroups, solrHooks, errorMessage, reloadMasterData } = useMasterData()
  // Nach Drag and Drop sofort die neue Reihenfolge zeigen, ohne auf das Neuladen zu warten
  const [reorderedSolrHookGroups, setReorderedSolrHookGroups] = useState<SolrHookGroup[] | null>(null)
  const [positionErrorMessage, setPositionErrorMessage] = useState<string | null>(null)
  // Sicherheitsabfrage vor dem Löschen: null, solange keine offen ist
  const [solrHookGroupToDelete, setSolrHookGroupToDelete] = useState<SolrHookGroup | null>(null)

  const shownSolrHookGroups = reorderedSolrHookGroups ?? solrHookGroups
  const hookCountByKey: Record<string, number> = {}
  for (const solrHook of solrHooks ?? []) {
    if (solrHook.hookGroupKey !== null) {
      hookCountByKey[solrHook.hookGroupKey] = (hookCountByKey[solrHook.hookGroupKey] ?? 0) + 1
    }
  }

  /** Positionen neu durchzählen (1, 2, 3 …) und alle auf einmal speichern, danach die Stammdaten neu laden. */
  function reorderSolrHookGroups(newOrder: SolrHookGroup[]) {
    const positions = positionsByKey(newOrder, (solrHookGroup) => solrHookGroup.key)
    setReorderedSolrHookGroups(
      newOrder.map((solrHookGroup) => ({ ...solrHookGroup, listingPosition: positions[solrHookGroup.key] })),
    )
    setPositionErrorMessage(null)
    changeSolrHookGroupPositions(positions)
      .then((result) => setReorderedSolrHookGroups(result.data))
      .catch((error: unknown) => {
        setPositionErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setReorderedSolrHookGroups(null)
      })
      .finally(reloadMasterData)
  }

  let footer
  if (errorMessage !== null || positionErrorMessage !== null) {
    footer = <span className="text-danger">{errorMessage ?? positionErrorMessage}</span>
  } else if (shownSolrHookGroups === null) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else {
    footer = <span>{t('solrmanager.hookGroups.count', { count: shownSolrHookGroups.length })}</span>
  }

  const newSolrHookGroupButton = (
    <NewEntityButton
      entity="SolrHookGroup"
      onClick={() => navigate('/solrmanager/hookgroups/new', { state: ORIGIN_STATE })}
    />
  )

  function editSolrHookGroup(solrHookGroup: SolrHookGroup) {
    const state: EditSolrHookGroupState = { ...ORIGIN_STATE, solrHookGroupKey: solrHookGroup.key }
    navigate('/solrmanager/hookgroups/edit', { state })
  }

  return (
    <>
      <ContentBox title={t('solrmanager.hookGroups.title')} actions={newSolrHookGroupButton} footer={footer}>
        <p>{t('solrmanager.hookGroups.intro')}</p>
        {shownSolrHookGroups !== null && (
          <AllSolrHookGroupsAdminList
            solrHookGroups={shownSolrHookGroups}
            hookCountByKey={hookCountByKey}
            onEdit={editSolrHookGroup}
            onDelete={setSolrHookGroupToDelete}
            onReorder={reorderSolrHookGroups}
          />
        )}
      </ContentBox>
      <DeletePopup
        open={solrHookGroupToDelete !== null}
        title={t('solrmanager.hookGroups.deleteTitle')}
        onConfirm={() => deleteSolrHookGroup(solrHookGroupToDelete?.key ?? '')}
        onDeleted={() => {
          setReorderedSolrHookGroups(null)
          reloadMasterData()
        }}
        onClose={() => setSolrHookGroupToDelete(null)}
      >
        <Trans
          i18nKey="solrmanager.hookGroups.deleteQuestion"
          values={{ key: solrHookGroupToDelete?.key ?? '' }}
          components={{ strong: <strong /> }}
        />
      </DeletePopup>
    </>
  )
}
