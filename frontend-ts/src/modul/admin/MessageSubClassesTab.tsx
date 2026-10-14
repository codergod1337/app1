import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { positionsByKey } from '../../components/positions.ts'
import { AllMessageSubClassesAdminList } from '../messagesubclass/AllMessageSubClassesAdminList.tsx'
import { changeMessageSubClassPositions, deleteMessageSubClass, type MessageSubClass } from '../messagesubclass/MessageSubClass.ts'
import type { EditMessageSubClassState } from './EditMessageSubClassTab.tsx'

/** Der Pfad dieses Tabs, Herkunft für Anlegen und Bearbeiten */
export const MESSAGE_SUB_CLASSES_PATH = '/admin/messagesubclasses'

/** Beim Verlassen dieses Tabs mitgeben, damit der header „zurück zu Nachrichtenarten“ anbietet */
const ORIGIN_STATE: OriginState = { origin: { path: MESSAGE_SUB_CLASSES_PATH, label: 'admin.tabs.messageSubClasses' } }

/**
 * Tab „Nachrichtenarten“ der Administration (User 2026-10-13, nach dem MessageSubClassTab der Vorlage): alle Arten,
 * sortierbar per Drag and Drop, mit Anlegen, Bearbeiten und Löschen nach Sicherheitsabfrage.
 */
export function MessageSubClassesTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { messageSubClasses, errorMessage, reloadMasterData } = useMasterData()
  // Nach Drag and Drop sofort die neue Reihenfolge zeigen, ohne auf das Neuladen zu warten
  const [reorderedMessageSubClasses, setReorderedMessageSubClasses] = useState<MessageSubClass[] | null>(null)
  const [positionErrorMessage, setPositionErrorMessage] = useState<string | null>(null)
  // Sicherheitsabfrage vor dem Löschen: null, solange keine offen ist
  const [messageSubClassToDelete, setMessageSubClassToDelete] = useState<MessageSubClass | null>(null)

  const shownMessageSubClasses = reorderedMessageSubClasses ?? messageSubClasses

  /** Positionen neu durchzählen und alle auf einmal speichern, danach die Stammdaten neu laden; klappt das nicht, nur neu laden */
  function reorderMessageSubClasses(newOrder: MessageSubClass[]) {
    const positions = positionsByKey(newOrder, (messageSubClass) => messageSubClass.key)
    setReorderedMessageSubClasses(newOrder.map((messageSubClass) => ({ ...messageSubClass, listingPosition: positions[messageSubClass.key] })))
    setPositionErrorMessage(null)
    changeMessageSubClassPositions(positions)
      .then((result) => setReorderedMessageSubClasses(result.data))
      .catch((error: unknown) => {
        setPositionErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setReorderedMessageSubClasses(null)
      })
      .finally(reloadMasterData)
  }

  let footer
  if (errorMessage !== null || positionErrorMessage !== null) {
    footer = <span className="text-danger">{errorMessage ?? positionErrorMessage}</span>
  } else if (shownMessageSubClasses === null) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else {
    footer = <span>{t('messagesubclass.count', { count: shownMessageSubClasses.length })}</span>
  }

  function editMessageSubClass(messageSubClass: MessageSubClass) {
    const state: EditMessageSubClassState = { ...ORIGIN_STATE, messageSubClassKey: messageSubClass.key }
    navigate(`${MESSAGE_SUB_CLASSES_PATH}/edit`, { state })
  }

  return (
    <>
      <ContentBox
        title={t('messagesubclass.title')}
        actions={<NewEntityButton entity="MessageSubClass" onClick={() => navigate(`${MESSAGE_SUB_CLASSES_PATH}/new`, { state: ORIGIN_STATE })} />}
        footer={footer}
      >
        {shownMessageSubClasses !== null && (
          <AllMessageSubClassesAdminList
            messageSubClasses={shownMessageSubClasses}
            onEdit={editMessageSubClass}
            onDelete={setMessageSubClassToDelete}
            onReorder={reorderMessageSubClasses}
          />
        )}
      </ContentBox>
      <DeletePopup
        open={messageSubClassToDelete !== null}
        title={t('messagesubclass.deleteTitle')}
        onConfirm={() => deleteMessageSubClass(messageSubClassToDelete?.key ?? '')}
        onDeleted={() => {
          setReorderedMessageSubClasses(null)
          reloadMasterData()
        }}
        onClose={() => setMessageSubClassToDelete(null)}
      >
        {t('messagesubclass.deleteQuestion', { key: messageSubClassToDelete?.key ?? '' })}
      </DeletePopup>
    </>
  )
}
