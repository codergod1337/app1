import { useState } from 'react'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { positionsByKey } from '../../components/positions.ts'
import {
  changeAccessRoleCollectionPositions,
  deleteAccessRoleCollection,
  type AccessRoleCollection,
} from '../accessrolecollection/AccessRoleCollection.ts'
import { AllAccessRoleCollectionsAdminList } from '../accessrolecollection/AllAccessRoleCollectionsAdminList.tsx'
import type { EditAccessRoleCollectionState } from './EditAccessRoleCollectionTab.tsx'

/** Beim Verlassen dieses Tabs mitgeben, damit der header „zurück zu ARC“ anbietet */
const ORIGIN_STATE: OriginState = { origin: { path: '/admin/accessrolecollections', label: 'ARC' } }

/** Tab „AccessRoleCollections“ der Administration: alles unter dem subheader. */
export function AccessRoleCollectionsTab() {
  const navigate = useNavigate()
  const { accessRoleCollections, errorMessage, reloadMasterData } = useMasterData()
  // Nach Drag and Drop sofort die neue Reihenfolge zeigen, ohne auf das Neuladen zu warten
  const [reordered, setReordered] = useState<AccessRoleCollection[] | null>(null)
  const [positionErrorMessage, setPositionErrorMessage] = useState<string | null>(null)
  // Sicherheitsabfrage vor dem Löschen: null, solange keine offen ist
  const [arcToDelete, setArcToDelete] = useState<AccessRoleCollection | null>(null)

  const shownArcs = reordered ?? accessRoleCollections

  /**
   * Positionen neu durchzählen (1, 2, 3 …) und alle auf einmal speichern, danach die Stammdaten für alle Module neu
   * laden. Klappt das nicht: nur neu laden.
   */
  function reorderAccessRoleCollections(newOrder: AccessRoleCollection[]) {
    const positions = positionsByKey(newOrder, (arc) => arc.key)
    setReordered(newOrder.map((arc) => ({ ...arc, listingPosition: positions[arc.key] })))
    setPositionErrorMessage(null)
    changeAccessRoleCollectionPositions(positions)
      .then((result) => setReordered(result.data))
      .catch((error: unknown) => {
        setPositionErrorMessage(error instanceof RequestError ? error.message : 'Unbekannter Fehler')
        setReordered(null)
      })
      .finally(reloadMasterData)
  }

  const shownErrorMessage = errorMessage ?? positionErrorMessage
  let footer
  if (shownErrorMessage !== null) {
    footer = <span className="text-danger">{shownErrorMessage}</span>
  } else if (shownArcs === null) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
  } else {
    footer = <span>{shownArcs.length} AccessRoleCollections</span>
  }

  const newArcButton = (
    <NewEntityButton
      entity="AccessRoleCollection"
      onClick={() => navigate('/admin/accessrolecollections/new', { state: ORIGIN_STATE })}
    />
  )

  function editAccessRoleCollection(arc: AccessRoleCollection) {
    const state: EditAccessRoleCollectionState = { ...ORIGIN_STATE, accessRoleCollectionKey: arc.key }
    navigate('/admin/accessrolecollections/edit', { state })
  }

  return (
    <>
      <ContentBox title="AccessRoleCollections" actions={newArcButton} footer={footer}>
        {shownArcs !== null && (
          <AllAccessRoleCollectionsAdminList
            accessRoleCollections={shownArcs}
            onEdit={editAccessRoleCollection}
            onDelete={setArcToDelete}
            onReorder={reorderAccessRoleCollections}
          />
        )}
      </ContentBox>
      <DeletePopup
        open={arcToDelete !== null}
        title="AccessRoleCollection löschen"
        onConfirm={() => deleteAccessRoleCollection(arcToDelete?.key ?? '')}
        onDeleted={() => {
          setReordered(null)
          reloadMasterData()
        }}
        onClose={() => setArcToDelete(null)}
      >
        Soll die AccessRoleCollection <strong>{arcToDelete?.key}</strong> wirklich gelöscht werden? Sie wird auch aus
        den Slaves aller anderen ARCs entfernt.
      </DeletePopup>
    </>
  )
}
