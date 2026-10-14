import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useParams } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { MailHtmlFrame } from '../../components/MailHtmlFrame.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { useOrigin, type OriginState } from '../../components/Origin.ts'
import { Subheader } from '../../components/Subheader.tsx'
import { UsersGuidName } from '../../components/UsersGuidName.tsx'
import { downloadDmsFileBlob, getDmsSolrDocument } from '../dms/Dms.ts'
import { DmsFileSubClassDocumentsList } from '../dms/DmsFileSubClassDocumentsList.tsx'
import type { DmsSolrDocument } from '../dms/DmsSolrDocumentFacts.tsx'
import { FileSubClassSymbol } from '../filesubclass/FileSubClassSymbol.tsx'
import { messageSubClassNameOf } from '../messagesubclass/MessageSubClass.ts'
import { SolrHookValues } from '../solrmanager/SolrHookValues.tsx'
import { dmsImageIdsOf, getNachrichtConversation, nachrichtenValueOf, nachrichtenValuesOf, nachrichtPath, withImageUrls } from './Nachrichten.ts'

type NachrichtDocument = Record<string, unknown>

/** „Name <adresse>“ je Beteiligtem; Namen und Adressen liegen in zwei Feldern in derselben Reihenfolge */
function participantsText(document: NachrichtDocument, namesFieldName: string, addressesFieldName: string): string {
  const names = nachrichtenValuesOf(document[namesFieldName])
  return nachrichtenValuesOf(document[addressesFieldName])
    .map((address, index) => {
      const name = names[index] ?? ''
      return name !== '' && name.toLowerCase() !== address.toLowerCase() ? `${name} <${address}>` : address
    })
    .join(', ')
}

/**
 * Die Bilder eines Mailtexts aus dem DMS (User 2026-10-14): Jedes dms:<id> im Text wird über den normalen Download
 * geholt, mit den Rechten des Users an dieser Datei, und als blob:-Adresse eingesetzt. ready, sobald jede Anfrage
 * beantwortet ist; ein Bild ohne Zugriff bleibt leer. Die Adressen werden beim Verlassen wieder freigegeben.
 */
function useDmsImageUrls(html: string): { urlById: Record<string, string>; ready: boolean } {
  const [loaded, setLoaded] = useState<{ html: string; urlById: Record<string, string> } | null>(null)

  useEffect(() => {
    const ids = dmsImageIdsOf(html)
    let active = true
    const created: string[] = []
    Promise.allSettled(ids.map((id) => downloadDmsFileBlob(id).then((result) => [id, URL.createObjectURL(result.data)] as const))).then((results) => {
      const urlById: Record<string, string> = {}
      for (const result of results) {
        if (result.status === 'fulfilled') {
          created.push(result.value[1])
          urlById[result.value[0]] = result.value[1]
        }
      }
      if (active) {
        setLoaded({ html, urlById })
      } else {
        created.forEach((url) => URL.revokeObjectURL(url))
      }
    })
    return () => {
      active = false
      created.forEach((url) => URL.revokeObjectURL(url))
    }
  }, [html])

  return loaded !== null && loaded.html === html ? { urlById: loaded.urlById, ready: true } : { urlById: {}, ready: false }
}

/** Der Mailtext mit seinen Bildern; erst gezeigt, wenn die Bilder da sind, damit das iframe nicht zweimal lädt */
function NachrichtBody({ html, title }: { html: string; title: string }) {
  const { t } = useTranslation()
  const { urlById, ready } = useDmsImageUrls(html)
  if (html.trim() === '') {
    return <span className="text-body-secondary">{t('archivieren.mail.noBody')}</span>
  }
  if (!ready) {
    return <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  }
  return <MailHtmlFrame html={withImageUrls(html, urlById)} title={title} />
}

/**
 * Die Anhänge der geöffneten Nachricht als DMS-Dokumente (User 2026-10-14): Ob der User einen sieht, entscheidet beim
 * Anzeigen dessen Dateiart, jeder wird einzeln mit seinen Rechten geladen. Was er nicht sehen darf, zählt nur.
 * Signaturen stehen nie hier, sie sind Bilder im Text.
 */
function NachrichtAttachments({ ids, origin }: { ids: readonly string[]; origin: OriginState }) {
  const { t } = useTranslation()
  const idsKey = ids.join(',')
  const [loaded, setLoaded] = useState<{ key: string; documents: DmsSolrDocument[]; hidden: number } | null>(null)

  useEffect(() => {
    let active = true
    Promise.allSettled(idsKey.split(',').filter((id) => id !== '').map((id) => getDmsSolrDocument(id))).then((results) => {
      if (!active) {
        return
      }
      const documents: DmsSolrDocument[] = []
      let hidden = 0
      for (const result of results) {
        if (result.status === 'fulfilled') {
          documents.push(result.value.data.document)
        } else {
          hidden += 1
        }
      }
      setLoaded({ key: idsKey, documents, hidden })
    })
    return () => {
      active = false
    }
  }, [idsKey])

  const current = loaded !== null && loaded.key === idsKey ? loaded : null
  return (
    <ContentBox
      title={t('archivieren.mail.attachments')}
      footer={
        current === null ? (
          <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
        ) : (
          <span>
            {t('archivieren.mail.attachmentCount', { count: current.documents.length })}
            {current.hidden > 0 && ` · ${t('nachricht.attachmentsHidden', { count: current.hidden })}`}
          </span>
        )
      }
    >
      {current !== null && current.documents.length > 0 && (
        <DmsFileSubClassDocumentsList documents={current.documents} displayContextKey="" mode="WITH_FILES" origin={origin} />
      )}
      {current !== null && current.documents.length === 0 && <span className="text-body-secondary">{t('nachricht.noVisibleAttachments')}</span>}
    </ContentBox>
  )
}

interface NachrichtMessageProps {
  message: NachrichtDocument
  /** ganz: mit ganzer Mail, wenn sie so archiviert wurde, mit Hooks und wer archiviert hat; sonst nur der neue Teil */
  full: boolean
  /** die Nachricht dieser Seite: kein Link auf sich selbst, im Verlauf markiert */
  opened: boolean
  origin: OriginState
}

/** Eine Nachricht: Kopf, Nachrichtenart, Text. Oben die geöffnete ganz, im Verlauf jede mit ihrem neuen Teil */
function NachrichtMessage({ message, full, opened, origin }: NachrichtMessageProps) {
  const { t, i18n } = useTranslation()
  const { language } = useLanguage()
  const { messageSubClasses, solrHooks } = useMasterData()
  const id = nachrichtenValueOf(message, 'id')
  const subject = nachrichtenValueOf(message, 'subject') || t('archivieren.noSubject')
  const messageSubClass = (messageSubClasses ?? []).find((candidate) => candidate.key === nachrichtenValueOf(message, 'messageSubClassKey')) ?? null
  const sent = nachrichtenValueOf(message, 'sentDateTime') || nachrichtenValueOf(message, 'receivedDateTime')
  const from = participantsText(message, 'fromName', 'fromAddress')
  const sender = participantsText(message, 'senderName', 'senderAddress')
  const to = participantsText(message, 'toRecipientNames', 'toRecipientAddresses')
  const cc = participantsText(message, 'ccRecipientNames', 'ccRecipientAddresses')
  const bcc = participantsText(message, 'bccRecipientNames', 'bccRecipientAddresses')
  const wholeMail = nachrichtenValueOf(message, 'emailBody')
  // die geöffnete Nachricht so, wie sie archiviert wurde; im Verlauf nur ihr neuer Teil, das Zitat steht ja darüber
  const html = full && wholeMail !== '' ? wholeMail : nachrichtenValueOf(message, 'uniqueBody')
  const archivedBy = nachrichtenValueOf(message, 'archivedBy')
  const archivedAt = nachrichtenValueOf(message, 'archivedAt')
  const hooksWithValues = (solrHooks ?? []).filter((hook) => nachrichtenValuesOf(message[hook.key]).length > 0)

  const title = (
    <span className="d-inline-flex align-items-center gap-2">
      {messageSubClass && <FileSubClassSymbol fileSubClass={messageSubClass} />}
      {opened ? (
        subject
      ) : (
        <Link to={nachrichtPath(id)} state={origin}>
          {subject}
        </Link>
      )}
      {opened && !full && <span className="badge text-bg-primary">{t('nachricht.thisMessage')}</span>}
    </span>
  )

  return (
    <ContentBox
      title={title}
      footer={
        <span>
          {t('nachricht.number', { id })}
          {messageSubClass && ` · ${messageSubClassNameOf(messageSubClass, language)}`}
          {` · ${nachrichtenValueOf(message, 'mailbox')}`}
          {full && wholeMail !== '' && ` · ${t('nachricht.wholeMailArchived')}`}
        </span>
      }
    >
      <dl className="mail-header">
        <dt>{t('archivieren.mail.from')}</dt>
        <dd>{from}</dd>
        {sender !== '' && sender !== from && (
          <>
            <dt>{t('nachricht.sender')}</dt>
            <dd>{sender}</dd>
          </>
        )}
        <dt>{t('archivieren.mail.to')}</dt>
        <dd>{to}</dd>
        {cc !== '' && (
          <>
            <dt>{t('archivieren.mail.cc')}</dt>
            <dd>{cc}</dd>
          </>
        )}
        {bcc !== '' && (
          <>
            <dt>{t('archivieren.mail.bcc')}</dt>
            <dd>{bcc}</dd>
          </>
        )}
        <dt>{t('archivieren.mail.date')}</dt>
        <dd>{sent !== '' ? new Date(sent).toLocaleString(i18n.language) : ''}</dd>
        {full && archivedAt !== '' && (
          <>
            <dt>{t('nachricht.archived')}</dt>
            <dd>
              {new Date(archivedAt).toLocaleString(i18n.language)}
              {archivedBy !== '' && (
                <>
                  {' · '}
                  <UsersGuidName guid={archivedBy} />
                </>
              )}
            </dd>
          </>
        )}
      </dl>
      {full && hooksWithValues.length > 0 && (
        <div className="d-flex flex-wrap gap-2 mb-3">
          {hooksWithValues.map((hook) => (
            <SolrHookValues key={hook.key} solrHook={hook} values={nachrichtenValuesOf(message[hook.key])} language={language} origin={origin} />
          ))}
        </div>
      )}
      <NachrichtBody html={html} title={subject} />
    </ContentBox>
  )
}

/**
 * Die Seite einer archivierten Nachricht (User 2026-10-14): oben die Nachricht selbst mit ihren Hooks, darunter ihre
 * Anhänge als DMS-Dokumente, dann der Verlauf, älteste zuerst, je Nachricht ihr neuer Teil. Die Nachricht und der
 * Verlauf kommen mit einer Anfrage; was der User nicht sehen darf, fehlt im Verlauf. Bilder im Text lädt jede Nachricht
 * aus dem DMS (dms:<id>).
 */
export function NachrichtPage() {
  const { t } = useTranslation()
  const { id = '' } = useParams()
  const backOrigin = useOrigin()
  const location = useLocation()
  const [loaded, setLoaded] = useState<{ id: string; messages: NachrichtDocument[] } | { id: string; errorMessage: string } | null>(null)

  useEffect(() => {
    let active = true
    getNachrichtConversation(id)
      .then((result) => {
        if (active) {
          setLoaded({ id, messages: result.data })
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setLoaded({ id, errorMessage: error instanceof RequestError ? error.message : t('common.unknownError') })
        }
      })
    return () => {
      active = false
    }
  }, [id, t])

  // Wege von hier tragen die Herkunft dieser Seite weiter; ein Sprung im Verlauf behält die ursprüngliche Herkunft
  const origin: OriginState = { origin: backOrigin ?? { path: location.pathname, label: t('nachricht.title', { id }) } }
  const current = loaded !== null && loaded.id === id ? loaded : null

  let content
  if (current === null) {
    content = (
      <ContentBox title={t('nachricht.title', { id })}>
        <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
      </ContentBox>
    )
  } else if ('errorMessage' in current) {
    content = (
      <ContentBox title={t('nachricht.title', { id })}>
        <span className="text-danger">{current.errorMessage}</span>
      </ContentBox>
    )
  } else {
    const opened = current.messages.find((message) => nachrichtenValueOf(message, 'id') === id) ?? null
    const attachmentIds = opened === null ? [] : nachrichtenValuesOf(opened.attachments)
    content = (
      <div className="d-flex flex-column gap-3">
        {opened !== null && <NachrichtMessage message={opened} full opened origin={origin} />}
        {attachmentIds.length > 0 && <NachrichtAttachments ids={attachmentIds} origin={origin} />}
        {/* der Verlauf, sobald es mehr als diese eine Nachricht gibt: alle in Reihenfolge, diese markiert */}
        {current.messages.length > 1 && (
          <>
            <h2 className="h6 mb-0 mt-2">{t('nachricht.conversation', { count: current.messages.length })}</h2>
            {current.messages.map((message) => (
              <NachrichtMessage
                key={nachrichtenValueOf(message, 'id')}
                message={message}
                full={false}
                opened={message === opened}
                origin={origin}
              />
            ))}
          </>
        )}
      </div>
    )
  }

  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="inbox" /> {t('nachricht.title', { id })}
          </>
        }
        sticky
      />
      <div className="main-content">{content}</div>
    </>
  )
}
