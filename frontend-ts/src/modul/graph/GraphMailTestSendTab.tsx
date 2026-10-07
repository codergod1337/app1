import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { branding } from '../../branding/branding.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { showToast } from '../../components/toastStore.ts'
import { sendGraphTestMail, type GraphMailTestSendRequest } from './GraphMailTestSend.ts'

const FORM_ID = 'graph-mail-test-send-form'

const EMPTY_TEST_MAIL: GraphMailTestSendRequest = {
  senderAddress: '',
  recipientEmail: '',
  subject: '',
  htmlBody: '',
}

/**
 * Tab „Test-Mail“ des GraphManagers: eine Mail über Graph verschicken, mit Absender, Empfänger, Betreff und Text von
 * Hand. Damit lassen sich Zertifikat, Berechtigung und ein Absenderpostfach prüfen, ohne einen Login zu starten.
 * Beim Absender wird nur der Teil vor dem @ eingegeben, die Domain steht fest (branding.mailDomain): Graph verschickt
 * ohnehin nur im Namen eines Postfachs im Tenant.
 */
export function GraphMailTestSendTab() {
  const { t } = useTranslation()
  const [testMail, setTestMail] = useState<GraphMailTestSendRequest>(EMPTY_TEST_MAIL)
  // der Teil des Absenders vor dem @, die Domain hängt das Formular an
  const [senderLocalPart, setSenderLocalPart] = useState('')
  const [sending, setSending] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  // wann die letzte Mail hinausging, null solange keine
  const [sentAt, setSentAt] = useState<Date | null>(null)

  function change<K extends keyof GraphMailTestSendRequest>(field: K, value: GraphMailTestSendRequest[K]) {
    setTestMail((current) => ({ ...current, [field]: value }))
  }

  function submitTestMail(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSending(true)
    setErrorMessage(null)
    sendGraphTestMail({ ...testMail, senderAddress: `${senderLocalPart.trim()}@${branding.mailDomain}` })
      .then(() => {
        setSentAt(new Date())
        showToast({ kind: 'success', title: t('graph.testMail.sentToast'), text: testMail.recipientEmail })
      })
      .catch((error: unknown) => {
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
      })
      .finally(() => setSending(false))
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {errorMessage === null && sentAt !== null && (
        <span>{t('graph.testMail.sentAt', { time: sentAt.toLocaleTimeString() })}</span>
      )}
      <span className="ms-auto d-flex gap-2">
        <button type="submit" form={FORM_ID} className="button-save" disabled={sending}>
          {t('graph.testMail.send')}
        </button>
      </span>
    </>
  )

  return (
    <ContentBox title={t('graph.testMail.title')} footer={footer}>
      <p>{t('graph.testMail.intro')}</p>
      <form id={FORM_ID} onSubmit={submitTestMail}>
        <fieldset className="row g-3" disabled={sending}>
          <div className="col-md-6">
            <label className="form-label" htmlFor={`${FORM_ID}-sender`}>
              {t('graph.testMail.from')}
            </label>
            <div className="input-group">
              <input
                id={`${FORM_ID}-sender`}
                className="form-control"
                required
                // nur der Teil vor dem @: kein @, keine Leerzeichen
                pattern="[^@\s]+"
                value={senderLocalPart}
                onChange={(event) => setSenderLocalPart(event.target.value)}
              />
              <span className="input-group-text">@{branding.mailDomain}</span>
            </div>
          </div>
          <div className="col-md-6">
            <label className="form-label" htmlFor={`${FORM_ID}-recipient`}>
              {t('graph.testMail.to')}
            </label>
            <input
              id={`${FORM_ID}-recipient`}
              className="form-control"
              type="email"
              required
              value={testMail.recipientEmail}
              onChange={(event) => change('recipientEmail', event.target.value)}
            />
          </div>
          <div className="col-12">
            <label className="form-label" htmlFor={`${FORM_ID}-subject`}>
              {t('graph.testMail.subject')}
            </label>
            <input
              id={`${FORM_ID}-subject`}
              className="form-control"
              required
              value={testMail.subject}
              onChange={(event) => change('subject', event.target.value)}
            />
          </div>
          <div className="col-12">
            <label className="form-label" htmlFor={`${FORM_ID}-html-body`}>
              {t('graph.testMail.htmlBody')}
            </label>
            <textarea
              id={`${FORM_ID}-html-body`}
              className="form-control"
              rows={8}
              required
              value={testMail.htmlBody}
              onChange={(event) => change('htmlBody', event.target.value)}
            />
          </div>
        </fieldset>
      </form>
    </ContentBox>
  )
}
