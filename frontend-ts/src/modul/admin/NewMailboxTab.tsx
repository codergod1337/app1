import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ConfirmCancelButtons } from '../../components/ConfirmCancelButtons.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { useOrigin } from '../../components/Origin.ts'
import { createMailbox, type Mailbox } from '../mailbox/Mailbox.ts'
import { MailboxForm } from '../mailbox/MailboxForm.tsx'
import { MAILBOXES_PATH } from './MailboxesTab.tsx'

const FORM_ID = 'new-mailbox-form'

/** Neues Postfach anlegen. Abbrechen und Anlegen springen zum origin, ohne ihn zum Tab „Postfächer“. */
export function NewMailboxTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? MAILBOXES_PATH
  const [saving, setSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const { reloadMasterData } = useMasterData()

  function saveNewMailbox(newMailboxData: Mailbox) {
    setSaving(true)
    setErrorMessage(null)
    createMailbox(newMailboxData)
      .then(() => {
        reloadMasterData()
        navigate(originPath)
      })
      .catch((error: unknown) => {
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setSaving(false)
      })
  }

  // dasselbe Paar im Kopf und im Footer
  const buttons = (
    <ConfirmCancelButtons
      onCancel={() => navigate(originPath)}
      confirmLabel={t('common.create')}
      confirmForm={FORM_ID}
      busy={saving}
    />
  )
  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {buttons}
    </>
  )

  return (
    <ContentBox title="new Mailbox()" actions={buttons} footer={footer}>
      <MailboxForm formId={FORM_ID} disabled={saving} onSubmit={saveNewMailbox} />
    </ContentBox>
  )
}
