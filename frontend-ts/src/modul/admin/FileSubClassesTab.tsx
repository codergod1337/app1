import { useState } from 'react'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { positionsByKey } from '../../components/positions.ts'
import { AllFileSubClassesAdminList } from '../filesubclass/AllFileSubClassesAdminList.tsx'
import { changeFileSubClassPositions, deleteFileSubClass, type FileSubClass } from '../filesubclass/FileSubClass.ts'
import type { EditFileSubClassState } from './EditFileSubClassTab.tsx'

/** Beim Verlassen dieses Tabs mitgeben, damit der header „zurück zu FSC“ anbietet */
const ORIGIN_STATE: OriginState = { origin: { path: '/admin/filesubclasses', label: 'FSC' } }

/** Tab „FSC“ der Administration: alle Dateiarten. Die Endungen pflegt später die Freischalt-Matrix. */
export function FileSubClassesTab() {
  const navigate = useNavigate()
  const { fileSubClasses, errorMessage, reloadMasterData } = useMasterData()
  // Nach Drag and Drop sofort die neue Reihenfolge zeigen, ohne auf das Neuladen zu warten
  const [reorderedFileSubClasses, setReorderedFileSubClasses] = useState<FileSubClass[] | null>(null)
  const [positionErrorMessage, setPositionErrorMessage] = useState<string | null>(null)
  // Sicherheitsabfrage vor dem Löschen: null, solange keine offen ist
  const [fileSubClassToDelete, setFileSubClassToDelete] = useState<FileSubClass | null>(null)

  const shownFileSubClasses = reorderedFileSubClasses ?? fileSubClasses

  /**
   * Positionen neu durchzählen (1, 2, 3 …) und alle auf einmal speichern, danach die Stammdaten für alle Module neu
   * laden. Klappt das nicht: nur neu laden.
   */
  function reorderFileSubClasses(newOrder: FileSubClass[]) {
    const positions = positionsByKey(newOrder, (fileSubClass) => fileSubClass.key)
    setReorderedFileSubClasses(newOrder.map((fileSubClass) => ({ ...fileSubClass, listingPosition: positions[fileSubClass.key] })))
    setPositionErrorMessage(null)
    changeFileSubClassPositions(positions)
      .then((result) => setReorderedFileSubClasses(result.data))
      .catch((error: unknown) => {
        setPositionErrorMessage(error instanceof RequestError ? error.message : 'Unbekannter Fehler')
        setReorderedFileSubClasses(null)
      })
      .finally(reloadMasterData)
  }

  let footer
  if (errorMessage !== null || positionErrorMessage !== null) {
    footer = <span className="text-danger">{errorMessage ?? positionErrorMessage}</span>
  } else if (shownFileSubClasses === null) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
  } else {
    footer = <span>{shownFileSubClasses.length} Dateiarten</span>
  }

  const newFileSubClassButton = (
    <NewEntityButton entity="FileSubClass" onClick={() => navigate('/admin/filesubclasses/new', { state: ORIGIN_STATE })} />
  )

  function editFileSubClass(fileSubClass: FileSubClass) {
    const state: EditFileSubClassState = { ...ORIGIN_STATE, fileSubClassKey: fileSubClass.key }
    navigate('/admin/filesubclasses/edit', { state })
  }

  return (
    <>
      <ContentBox title="Dateiarten (FSC)" actions={newFileSubClassButton} footer={footer}>
        {shownFileSubClasses !== null && (
          <AllFileSubClassesAdminList
            fileSubClasses={shownFileSubClasses}
            onEdit={editFileSubClass}
            onDelete={setFileSubClassToDelete}
            onReorder={reorderFileSubClasses}
          />
        )}
      </ContentBox>
      <DeletePopup
        open={fileSubClassToDelete !== null}
        title="Dateiart löschen"
        onConfirm={() => deleteFileSubClass(fileSubClassToDelete?.key ?? '')}
        onDeleted={() => {
          setReorderedFileSubClasses(null)
          reloadMasterData()
        }}
        onClose={() => setFileSubClassToDelete(null)}
      >
        Soll die Dateiart <strong>{fileSubClassToDelete?.key}</strong> wirklich gelöscht werden? Dateien dieser Art
        haben danach keinen Namen und kein Symbol mehr.
      </DeletePopup>
    </>
  )
}
