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
import { AllSolrCoresAdminList } from './AllSolrCoresAdminList.tsx'
import type { EditSolrCoreState } from './EditSolrCoreTab.tsx'
import { changeSolrCorePositions, deleteSolrCore, type SolrCore } from './SolrCore.ts'

/** Beim Verlassen dieses Tabs mitgeben, damit der header „zurück zu Kerne“ anbietet. Das label ist ein Schlüssel des Katalogs. */
const ORIGIN_STATE: OriginState = { origin: { path: '/solrmanager/cores', label: 'solrmanager.tabs.cores' } }

/** Tab „Kerne“ des SolrManagers: alle Kerne anlegen und pflegen. Jeder Kern bekommt daneben seinen eigenen Tab. */
export function SolrCoresTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { solrCores, errorMessage, reloadMasterData } = useMasterData()
  // Nach Drag and Drop sofort die neue Reihenfolge zeigen, ohne auf das Neuladen zu warten
  const [reorderedSolrCores, setReorderedSolrCores] = useState<SolrCore[] | null>(null)
  const [positionErrorMessage, setPositionErrorMessage] = useState<string | null>(null)
  // Sicherheitsabfrage vor dem Löschen: null, solange keine offen ist
  const [solrCoreToDelete, setSolrCoreToDelete] = useState<SolrCore | null>(null)

  const shownSolrCores = reorderedSolrCores ?? solrCores

  /**
   * Positionen neu durchzählen (1, 2, 3 …) und alle auf einmal speichern, danach die Stammdaten für alle Module neu
   * laden. Klappt das nicht: nur neu laden.
   */
  function reorderSolrCores(newOrder: SolrCore[]) {
    const positions = positionsByKey(newOrder, (solrCore) => solrCore.key)
    setReorderedSolrCores(newOrder.map((solrCore) => ({ ...solrCore, listingPosition: positions[solrCore.key] })))
    setPositionErrorMessage(null)
    changeSolrCorePositions(positions)
      .then((result) => setReorderedSolrCores(result.data))
      .catch((error: unknown) => {
        setPositionErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setReorderedSolrCores(null)
      })
      .finally(reloadMasterData)
  }

  let footer
  if (errorMessage !== null || positionErrorMessage !== null) {
    footer = <span className="text-danger">{errorMessage ?? positionErrorMessage}</span>
  } else if (shownSolrCores === null) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else {
    footer = <span>{t('solrmanager.cores.count', { count: shownSolrCores.length })}</span>
  }

  const newSolrCoreButton = (
    <NewEntityButton entity="SolrCore" onClick={() => navigate('/solrmanager/cores/new', { state: ORIGIN_STATE })} />
  )

  function editSolrCore(solrCore: SolrCore) {
    const state: EditSolrCoreState = { ...ORIGIN_STATE, solrCoreKey: solrCore.key }
    navigate('/solrmanager/cores/edit', { state })
  }

  return (
    <>
      <ContentBox title={t('solrmanager.cores.title')} actions={newSolrCoreButton} footer={footer}>
        {shownSolrCores !== null && (
          <AllSolrCoresAdminList
            solrCores={shownSolrCores}
            onEdit={editSolrCore}
            onDelete={setSolrCoreToDelete}
            onReorder={reorderSolrCores}
          />
        )}
      </ContentBox>
      <DeletePopup
        open={solrCoreToDelete !== null}
        title={t('solrmanager.cores.deleteTitle')}
        onConfirm={() => deleteSolrCore(solrCoreToDelete?.key ?? '')}
        onDeleted={() => {
          setReorderedSolrCores(null)
          reloadMasterData()
        }}
        onClose={() => setSolrCoreToDelete(null)}
      >
        <Trans
          i18nKey="solrmanager.cores.deleteQuestion"
          values={{ key: solrCoreToDelete?.key ?? '' }}
          components={{ strong: <strong /> }}
        />
      </DeletePopup>
    </>
  )
}
