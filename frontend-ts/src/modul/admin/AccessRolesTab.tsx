import { useState } from 'react'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { positionsByKey } from '../../components/positions.ts'
import { changeAccessRolePositions, deleteAccessRole, type AccessRole } from '../accessrole/AccessRole.ts'
import { AllAccessRolesAdminList } from '../accessrole/AllAccessRolesAdminList.tsx'
import type { EditAccessRoleState } from './EditAccessRoleTab.tsx'

/** Beim Verlassen dieses Tabs mitgeben, damit der header „zurück zu AR“ anbietet */
const ORIGIN_STATE: OriginState = { origin: { path: '/admin/accessroles', label: 'AR' } }

/** Tab „AccessRoles“ der Administration: alles unter dem subheader. */
export function AccessRolesTab() {
  const navigate = useNavigate()
  const { accessRoles, errorMessage, reloadMasterData } = useMasterData()
  // Nach Drag and Drop sofort die neue Reihenfolge zeigen, ohne auf das Neuladen zu warten
  const [reorderedAccessRoles, setReorderedAccessRoles] = useState<AccessRole[] | null>(null)
  const [positionErrorMessage, setPositionErrorMessage] = useState<string | null>(null)
  // Sicherheitsabfrage vor dem Löschen: null, solange keine offen ist
  const [accessRoleToDelete, setAccessRoleToDelete] = useState<AccessRole | null>(null)

  const shownAccessRoles = reorderedAccessRoles ?? accessRoles

  /**
   * Positionen neu durchzählen (1, 2, 3 …) und alle auf einmal speichern, danach die Stammdaten für alle Module neu
   * laden. Klappt das nicht: nur neu laden.
   */
  function reorderAccessRoles(newOrder: AccessRole[]) {
    const positions = positionsByKey(newOrder, (accessRole) => accessRole.key)
    setReorderedAccessRoles(newOrder.map((accessRole) => ({ ...accessRole, listingPosition: positions[accessRole.key] })))
    setPositionErrorMessage(null)
    changeAccessRolePositions(positions)
      .then((result) => setReorderedAccessRoles(result.data))
      .catch((error: unknown) => {
        setPositionErrorMessage(error instanceof RequestError ? error.message : 'Unbekannter Fehler')
        setReorderedAccessRoles(null)
      })
      .finally(reloadMasterData)
  }

  let footer
  if (errorMessage !== null || positionErrorMessage !== null) {
    footer = <span className="text-danger">{errorMessage ?? positionErrorMessage}</span>
  } else if (shownAccessRoles === null) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
  } else {
    footer = <span>{shownAccessRoles.length} AccessRoles</span>
  }

  const newAccessRoleButton = (
    <NewEntityButton
      entity="AccessRole"
      onClick={() => navigate('/admin/accessroles/new', { state: ORIGIN_STATE })}
    />
  )

  function editAccessRole(accessRole: AccessRole) {
    const state: EditAccessRoleState = { ...ORIGIN_STATE, accessRoleKey: accessRole.key }
    navigate('/admin/accessroles/edit', { state })
  }

  return (
    <>
      <ContentBox title="AccessRoles" actions={newAccessRoleButton} footer={footer}>
        {shownAccessRoles !== null && (
          <AllAccessRolesAdminList
            accessRoles={shownAccessRoles}
            onEdit={editAccessRole}
            onDelete={setAccessRoleToDelete}
            onReorder={reorderAccessRoles}
          />
        )}
      </ContentBox>
      <DeletePopup
        open={accessRoleToDelete !== null}
        title="AccessRole löschen"
        onConfirm={() => deleteAccessRole(accessRoleToDelete?.key ?? '')}
        onDeleted={() => {
          setReorderedAccessRoles(null)
          reloadMasterData()
        }}
        onClose={() => setAccessRoleToDelete(null)}
      >
        Soll die AccessRole <strong>{accessRoleToDelete?.key}</strong> wirklich gelöscht werden? Sie wird auch aus
        allen ARCs entfernt und allen Usern entzogen.
      </DeletePopup>
    </>
  )
}
