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
import { AllEntityStatesAdminList } from '../entitystate/AllEntityStatesAdminList.tsx'
import { changeEntityStatePositions, deleteEntityState, type EntityState } from '../entitystate/EntityState.ts'
import type { EditEntityStateState } from './EditEntityStateTab.tsx'

/** Beim Verlassen dieses Tabs mitgeben, damit der header „zurück zu Status“ anbietet. Das label ist ein Schlüssel des Katalogs. */
const ORIGIN_STATE: OriginState = { origin: { path: '/admin/entitystates', label: 'admin.tabs.entityStates' } }

/** Tab „Status“ der Administration: die Status aller Entities, die einen brauchen, mit ihrem Bereich. */
export function EntityStatesTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { entityStates, errorMessage, reloadMasterData } = useMasterData()
  // Nach Drag and Drop sofort die neue Reihenfolge zeigen, ohne auf das Neuladen zu warten
  const [reorderedEntityStates, setReorderedEntityStates] = useState<EntityState[] | null>(null)
  const [positionErrorMessage, setPositionErrorMessage] = useState<string | null>(null)
  // Sicherheitsabfrage vor dem Löschen: null, solange keine offen ist
  const [entityStateToDelete, setEntityStateToDelete] = useState<EntityState | null>(null)

  const shownEntityStates = reorderedEntityStates ?? entityStates

  /** Positionen neu durchzählen (1, 2, 3 …) und alle auf einmal speichern, danach die Stammdaten neu laden. */
  function reorderEntityStates(newOrder: EntityState[]) {
    const positions = positionsByKey(newOrder, (entityState) => entityState.key)
    setReorderedEntityStates(
      newOrder.map((entityState) => ({ ...entityState, listingPosition: positions[entityState.key] })),
    )
    setPositionErrorMessage(null)
    changeEntityStatePositions(positions)
      .then((result) => setReorderedEntityStates(result.data))
      .catch((error: unknown) => {
        setPositionErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setReorderedEntityStates(null)
      })
      .finally(reloadMasterData)
  }

  let footer
  if (errorMessage !== null || positionErrorMessage !== null) {
    footer = <span className="text-danger">{errorMessage ?? positionErrorMessage}</span>
  } else if (shownEntityStates === null) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else {
    footer = <span>{t('entitystate.count', { count: shownEntityStates.length })}</span>
  }

  const newEntityStateButton = (
    <NewEntityButton entity="EntityState" onClick={() => navigate('/admin/entitystates/new', { state: ORIGIN_STATE })} />
  )

  function editEntityState(entityState: EntityState) {
    const state: EditEntityStateState = { ...ORIGIN_STATE, entityStateKey: entityState.key }
    navigate('/admin/entitystates/edit', { state })
  }

  return (
    <>
      <ContentBox title={t('entitystate.title')} actions={newEntityStateButton} footer={footer}>
        <p>{t('entitystate.intro')}</p>
        {shownEntityStates !== null && (
          <AllEntityStatesAdminList
            entityStates={shownEntityStates}
            onEdit={editEntityState}
            onDelete={setEntityStateToDelete}
            onReorder={reorderEntityStates}
          />
        )}
      </ContentBox>
      <DeletePopup
        open={entityStateToDelete !== null}
        title={t('entitystate.deleteTitle')}
        onConfirm={() => deleteEntityState(entityStateToDelete?.key ?? '')}
        onDeleted={() => {
          setReorderedEntityStates(null)
          reloadMasterData()
        }}
        onClose={() => setEntityStateToDelete(null)}
      >
        <Trans
          i18nKey="entitystate.deleteQuestion"
          values={{ key: entityStateToDelete?.key ?? '' }}
          components={{ strong: <strong /> }}
        />
      </DeletePopup>
    </>
  )
}
