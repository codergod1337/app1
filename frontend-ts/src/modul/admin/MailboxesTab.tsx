import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { useLoad } from '../../api/useLoad.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { positionsByKey } from '../../components/positions.ts'
import { AllMailboxesAdminList } from '../mailbox/AllMailboxesAdminList.tsx'
import { changeMailboxPositions, deleteMailbox, getAllMailboxes, type Mailbox } from '../mailbox/Mailbox.ts'
import type { EditMailboxState } from './EditMailboxTab.tsx'

/** Der Pfad dieses Tabs, Herkunft für Anlegen und Bearbeiten */
export const MAILBOXES_PATH = '/admin/mailboxes'

/** Beim Verlassen dieses Tabs mitgeben, damit der header „zurück zu Postfächer“ anbietet */
const ORIGIN_STATE: OriginState = { origin: { path: MAILBOXES_PATH, label: 'admin.tabs.mailboxes' } }

/**
 * Tab „Postfächer“ der Administration (User 2026-10-13, nach dem MailboxTab der Vorlage): alle Postfächer über den
 * Admin-Endpunkt, nicht aus den Stammdaten, sortierbar per Drag and Drop, mit Anlegen, Bearbeiten und Löschen nach
 * Sicherheitsabfrage.
 */
export function MailboxesTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: mailboxes, errorMessage, reload } = useLoad(getAllMailboxes)
  // Nach Drag and Drop sofort die neue Reihenfolge zeigen, ohne auf das Neuladen zu warten
  const [reorderedMailboxes, setReorderedMailboxes] = useState<Mailbox[] | null>(null)
  const [positionErrorMessage, setPositionErrorMessage] = useState<string | null>(null)
  // Sicherheitsabfrage vor dem Löschen: null, solange keine offen ist
  const [mailboxToDelete, setMailboxToDelete] = useState<Mailbox | null>(null)

  const shownMailboxes = reorderedMailboxes ?? mailboxes

  /** Positionen neu durchzählen und alle auf einmal speichern, danach die Liste neu laden; klappt das nicht, nur neu laden */
  function reorderMailboxes(newOrder: Mailbox[]) {
    const positions = positionsByKey(newOrder, (mailbox) => mailbox.emailAddress)
    setReorderedMailboxes(newOrder.map((mailbox) => ({ ...mailbox, listingPosition: positions[mailbox.emailAddress] })))
    setPositionErrorMessage(null)
    changeMailboxPositions(positions)
      .then((result) => setReorderedMailboxes(result.data))
      .catch((error: unknown) => {
        setPositionErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setReorderedMailboxes(null)
      })
      .finally(reload)
  }

  let footer
  if (errorMessage !== null || positionErrorMessage !== null) {
    footer = <span className="text-danger">{errorMessage ?? positionErrorMessage}</span>
  } else if (shownMailboxes === null) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else {
    footer = <span>{t('mailbox.count', { count: shownMailboxes.length })}</span>
  }

  function editMailbox(mailbox: Mailbox) {
    const state: EditMailboxState = { ...ORIGIN_STATE, mailboxEmailAddress: mailbox.emailAddress }
    navigate(`${MAILBOXES_PATH}/edit`, { state })
  }

  return (
    <>
      <ContentBox
        title={t('mailbox.title')}
        actions={<NewEntityButton entity="Mailbox" onClick={() => navigate(`${MAILBOXES_PATH}/new`, { state: ORIGIN_STATE })} />}
        footer={footer}
      >
        {shownMailboxes !== null && <AllMailboxesAdminList mailboxes={shownMailboxes} onEdit={editMailbox} onDelete={setMailboxToDelete} onReorder={reorderMailboxes} />}
      </ContentBox>
      <DeletePopup
        open={mailboxToDelete !== null}
        title={t('mailbox.deleteTitle')}
        onConfirm={() => deleteMailbox(mailboxToDelete?.emailAddress ?? '')}
        onDeleted={() => {
          setReorderedMailboxes(null)
          reload()
        }}
        onClose={() => setMailboxToDelete(null)}
      >
        {t('mailbox.deleteQuestion', { emailAddress: mailboxToDelete?.emailAddress ?? '' })}
      </DeletePopup>
    </>
  )
}
