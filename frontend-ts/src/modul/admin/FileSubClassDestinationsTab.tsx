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
import { AllFileSubClassDestinationsAdminList } from '../filesubclass/AllFileSubClassDestinationsAdminList.tsx'
import { changeFileSubClassDestinationPositions, deleteFileSubClassDestination, type FileSubClassDestination } from '../filesubclass/FileSubClassDestination.ts'
import type { EditFileSubClassDestinationState } from './EditFileSubClassDestinationTab.tsx'

/** Beim Verlassen dieses Tabs mitgeben, damit der header „zurück zu Anzeigeorte“ anbietet. Das label ist ein Schlüssel des Katalogs. */
const ORIGIN_STATE: OriginState = { origin: { path: '/admin/filesubclassdestinations', label: 'admin.tabs.fileSubClassDestinations' } }

/** Tab „Anzeigeorte“ der Administration (User 2026-10-13): die Stellen, an denen Dateiarten als Abschnitt stehen. */
export function FileSubClassDestinationsTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { fileSubClassDestinations, errorMessage, reloadMasterData } = useMasterData()
  // Nach Drag and Drop sofort die neue Reihenfolge zeigen, ohne auf das Neuladen zu warten
  const [reorderedDestinations, setReorderedDestinations] = useState<FileSubClassDestination[] | null>(null)
  const [positionErrorMessage, setPositionErrorMessage] = useState<string | null>(null)
  // Sicherheitsabfrage vor dem Löschen: null, solange keine offen ist
  const [destinationToDelete, setDestinationToDelete] = useState<FileSubClassDestination | null>(null)

  const shownDestinations = reorderedDestinations ?? fileSubClassDestinations

  /** Positionen neu durchzählen (1, 2, 3 …) und alle auf einmal speichern, danach die Stammdaten neu laden. */
  function reorderDestinations(newOrder: FileSubClassDestination[]) {
    const positions = positionsByKey(newOrder, (destination) => destination.key)
    setReorderedDestinations(newOrder.map((destination) => ({ ...destination, listingPosition: positions[destination.key] })))
    setPositionErrorMessage(null)
    changeFileSubClassDestinationPositions(positions)
      .then((result) => setReorderedDestinations(result.data))
      .catch((error: unknown) => {
        setPositionErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setReorderedDestinations(null)
      })
      .finally(reloadMasterData)
  }

  let footer
  if (errorMessage !== null || positionErrorMessage !== null) {
    footer = <span className="text-danger">{errorMessage ?? positionErrorMessage}</span>
  } else if (shownDestinations === null) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else {
    footer = <span>{t('filesubclassdestination.count', { count: shownDestinations.length })}</span>
  }

  const newDestinationButton = (
    <NewEntityButton entity="FileSubClassDestination" onClick={() => navigate('/admin/filesubclassdestinations/new', { state: ORIGIN_STATE })} />
  )

  function editDestination(destination: FileSubClassDestination) {
    const state: EditFileSubClassDestinationState = { ...ORIGIN_STATE, fileSubClassDestinationKey: destination.key }
    navigate('/admin/filesubclassdestinations/edit', { state })
  }

  return (
    <>
      <ContentBox title={t('filesubclassdestination.title')} actions={newDestinationButton} footer={footer}>
        <p>{t('filesubclassdestination.intro')}</p>
        {shownDestinations !== null && (
          <AllFileSubClassDestinationsAdminList
            fileSubClassDestinations={shownDestinations}
            onEdit={editDestination}
            onDelete={setDestinationToDelete}
            onReorder={reorderDestinations}
          />
        )}
      </ContentBox>
      <DeletePopup
        open={destinationToDelete !== null}
        title={t('filesubclassdestination.deleteTitle')}
        onConfirm={() => deleteFileSubClassDestination(destinationToDelete?.key ?? '')}
        onDeleted={() => {
          setReorderedDestinations(null)
          reloadMasterData()
        }}
        onClose={() => setDestinationToDelete(null)}
      >
        <Trans i18nKey="filesubclassdestination.deleteQuestion" values={{ key: destinationToDelete?.key ?? '' }} components={{ strong: <strong /> }} />
      </DeletePopup>
    </>
  )
}
