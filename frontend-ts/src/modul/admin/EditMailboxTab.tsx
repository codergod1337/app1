import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { useLoad } from '../../api/useLoad.ts'
import { ConfirmCancelButtons } from '../../components/ConfirmCancelButtons.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useOrigin, type OriginState } from '../../components/Origin.ts'
import { getAllMailboxes, updateMailbox, type Mailbox } from '../mailbox/Mailbox.ts'
import { MailboxForm } from '../mailbox/MailboxForm.tsx'
import { MAILBOXES_PATH } from './MailboxesTab.tsx'

const FORM_ID = 'edit-mailbox-form'

/** State beim Öffnen: der origin und die Adresse des Postfachs. Die Adresse steht nicht in der URL, dort sind nur guid und Long-ID erlaubt. */
export interface EditMailboxState extends OriginState {
  mailboxEmailAddress: string
}

/** Ein Postfach bearbeiten. Abbrechen und Speichern springen zum origin. */
export function EditMailboxTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? MAILBOXES_PATH
  const mailboxEmailAddress = (useLocation().state as Partial<EditMailboxState> | null)?.mailboxEmailAddress
  // Alle Postfächer über den Admin-Endpunkt, daraus das gesuchte; der Tab „Postfächer“ lädt nach dem Speichern selbst neu
  const { data: mailboxes, errorMessage: loadErrorMessage } = useLoad(getAllMailboxes)
  const mailbox = mailboxes?.find((candidate) => candidate.emailAddress === mailboxEmailAddress) ?? null
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)

  function saveMailbox(changedMailboxData: Mailbox) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateMailbox(changedMailboxData)
      .then(() => navigate(originPath))
      .catch((error: unknown) => {
        setSaveErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setSaving(false)
      })
  }

  let errorMessage = loadErrorMessage ?? saveErrorMessage
  if (errorMessage === null && mailboxes !== null && mailbox === null) {
    errorMessage = t('mailbox.notFound')
  }

  // dasselbe Paar im Kopf und im Footer
  const buttons = (
    <ConfirmCancelButtons
      onCancel={() => navigate(originPath)}
      confirmLabel={t('common.save')}
      confirmForm={FORM_ID}
      disabled={mailbox === null}
      busy={saving}
    />
  )
  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {mailboxes === null && errorMessage === null && <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />}
      {buttons}
    </>
  )

  return (
    <ContentBox title={mailbox ? t('mailbox.editTitle', { emailAddress: mailbox.emailAddress }) : t('mailbox.editTitleEmpty')} actions={buttons} footer={footer}>
      {/* Erst mit den geladenen Daten anzeigen, damit das Formular mit ihnen startet */}
      {mailbox !== null && <MailboxForm formId={FORM_ID} initialMailbox={mailbox} disabled={saving} onSubmit={saveMailbox} />}
    </ContentBox>
  )
}
