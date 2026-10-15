import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { CancelButton } from '../../components/CancelButton.tsx'
import { ConfirmButton } from '../../components/ConfirmButton.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { MailHtmlFrame, type MailNewPartState } from '../../components/MailHtmlFrame.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import type { OriginState } from '../../components/Origin.ts'
import { readableBytes } from '../../components/readableBytes.ts'
import { fileSubClassNameOf } from '../filesubclass/FileSubClass.ts'
import { FileSubClassPicker } from '../filesubclass/FileSubClassPicker.tsx'
import { KONTAKTE_NAME_FIELD_NAME, kontakteValueOf, kontakteValuesOf } from '../kontakte/Kontakte.ts'
import { showToast } from '../../components/toastStore.ts'
import type { GraphMailFolder, GraphMailRecipient } from '../mailbox/Mailbox.ts'
import { SolrHookChip } from '../solrmanager/SolrHookChip.tsx'
import { SolrHookFields } from '../solrmanager/SolrHookFields.tsx'
import { SolrHookPicker } from '../solrmanager/SolrHookPicker.tsx'
import { SolrHookValues } from '../solrmanager/SolrHookValues.tsx'
import { ARCHIVIEREN_PATH } from './Archivieren.ts'
import {
  archiveMessage,
  MESSAGE_ARCHIVE_FALLBACK_FILE_SUB_CLASS_KEY,
  prepareMessageArchive,
  suggestedKeyOf,
  type MessageArchiveAttachment,
  type MessageArchivePreparation,
  type MessageArchiveRequest,
} from './MessageArchive.ts'

/** State beim Öffnen: Postfach, Ordner und Mail. Nichts davon steht in der URL, dort sind nur guid und Long-id erlaubt. */
export interface ArchivierenMailState extends OriginState {
  mailbox: string
  folder: GraphMailFolder
  exchangeMailId: string
}

/** Ein Bild nach dem Typ laut Mail: jedes unbekannte Bild steht als Signatur vorgehakt da (User 2026-10-14) */
function isImage(attachment: MessageArchiveAttachment): boolean {
  return (attachment.contentType ?? '').toLowerCase().startsWith('image/')
}

/** Die Hooks der Kontakte, die als Badge mit Link in die Mappe stehen */
const CONTACT_HOOK_KEYS = ['kundenNummer', 'lieferantenNummer']

/** „Name <adresse>“, ohne Namen oder bei gleichem Namen nur die Adresse */
function recipientText(recipient: GraphMailRecipient): string {
  return recipient.name && recipient.name.toLowerCase() !== recipient.address ? `${recipient.name} <${recipient.address}>` : recipient.address
}

/** Eine Kopie der Hooks mit einem Wert mehr, ohne Doppelte */
function withHookValue(hooks: Record<string, string[]>, hookKey: string, value: string): Record<string, string[]> {
  const values = hooks[hookKey] ?? []
  return values.includes(value) ? hooks : { ...hooks, [hookKey]: [...values, value] }
}

/** Ein Schalter je Anhang, als Menge der sha256 */
function toggled(set: ReadonlySet<string>, sha256: string, on: boolean): Set<string> {
  const next = new Set(set)
  if (on) {
    next.add(sha256)
  } else {
    next.delete(sha256)
  }
  return next
}

/**
 * Eine Mail aufbereiten (User 2026-10-14, nach der Aufbereiten-Seite der Vorlage, der Scan liegt hier im Backend):
 * links die Mail und darunter ihre Anhänge, rechts der Inspektor mit Nachrichtenart, Beteiligten, Nummern und Hooks.
 *
 * Was archiviert wird, steht sichtbar in der Mail: Vorgabe ist nur der neue Teil, an der Grenze „bis hier wird
 * archiviert“, das Zitat schraffiert; mit „ganze Mail archivieren“ kommt die Mail samt Verlauf ins Archiv.
 *
 * Vorschläge springen mit: Nachrichtenart und Dateiart je neuem Anhang rechnen den festen Teil aus dem Backend
 * (Postfach, Triggerwörter) plus 2 Punkte je Trigger-Hook mit Wert. Wer selbst wählt, behält seine Wahl. Beide wählt man
 * über dieselben Kacheln (FileSubClassPicker, User 2026-10-14). Signaturen stehen nicht in der Liste, sie werden still
 * an der Nachricht eingetragen. Ein Anhang ohne Zugriff (NO_ACCESS) sperrt das Archivieren.
 */
export function ArchivierenMailPage() {
  const { t, i18n } = useTranslation()
  const { language } = useLanguage()
  const { messageSubClasses, fileSubClasses, solrHooks } = useMasterData()
  const navigate = useNavigate()
  const state = useLocation().state as Partial<ArchivierenMailState> | null
  const mailbox = state?.mailbox
  const folder = state?.folder ?? 'INBOX'
  const exchangeMailId = state?.exchangeMailId
  const backPath = state?.origin?.path ?? `${ARCHIVIEREN_PATH}/mailboxes`
  /** läuft gerade das Archivieren */
  const [archiving, setArchiving] = useState(false)
  const [archiveErrorMessage, setArchiveErrorMessage] = useState<string | null>(null)

  const [preparation, setPreparation] = useState<MessageArchivePreparation | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  /** wie der neue Teil in der ganzen Mail lag; null, bis die Anzeige es weiß */
  const [newPartState, setNewPartState] = useState<MailNewPartState | null>(null)
  /** die ganze Mail samt Verlauf archivieren; Vorgabe: nur der neue Teil */
  const [wholeMail, setWholeMail] = useState(false)
  /** der eine Hook-Satz für Nachricht und Anhänge, vorbelegt mit den Vorschlägen */
  const [hooks, setHooks] = useState<Record<string, string[]>>({})
  /** die selbst gewählte Nachrichtenart; null: es gilt der Vorschlag */
  const [chosenMessageSubClassKey, setChosenMessageSubClassKey] = useState<string | null>(null)
  /** je neuem Anhang (sha256) die selbst gewählte Dateiart; fehlt sie, gilt der Vorschlag */
  const [chosenFileSubClassKeys, setChosenFileSubClassKeys] = useState<Record<string, string>>({})
  /** als Signatur markiert: geht als Bild ohne Hooks ins DMS, mit wtf = ATTACHMENT_SIGNATURE */
  const [signatureMarks, setSignatureMarks] = useState<Set<string>>(new Set())
  /** zum Ignorieren markiert: wird nicht archiviert, bekommt wtf = ATTACHMENT_IGNORE */
  const [ignoreMarks, setIgnoreMarks] = useState<Set<string>>(new Set())
  /** ignorierte, die trotzdem archiviert werden */
  const [archiveAnyway, setArchiveAnyway] = useState<Set<string>>(new Set())

  useEffect(() => {
    if (!mailbox || !exchangeMailId) {
      return
    }
    let active = true
    setPreparation(null)
    setErrorMessage(null)
    setNewPartState(null)
    setWholeMail(false)
    setChosenMessageSubClassKey(null)
    setChosenFileSubClassKeys({})
    setSignatureMarks(new Set())
    setIgnoreMarks(new Set())
    setArchiveAnyway(new Set())
    setArchiveErrorMessage(null)
    prepareMessageArchive(mailbox, exchangeMailId)
      .then((result) => {
        if (active) {
          setPreparation(result.data)
          setHooks(Object.fromEntries(Object.entries(result.data.suggestedHooks).map(([key, values]) => [key, [...values]])))
          // jedes unbekannte Bild ist meist eine Signatur (Base64 im Text, Logo im HTML): vorgehakt, der User entscheidet
          setSignatureMarks(
            new Set(
              result.data.attachments
                .filter((attachment) => attachment.state === 'NEW' && attachment.sha256 !== null && isImage(attachment))
                .map((attachment) => attachment.sha256 as string),
            ),
          )
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        }
      })
    return () => {
      active = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mailbox, exchangeMailId])

  if (!mailbox || !exchangeMailId) {
    return <ContentBox title={t('archivieren.mail.title')}>{t('archivieren.mail.notChosen')}</ContentBox>
  }
  if (preparation === null) {
    return (
      <ContentBox
        title={t('archivieren.mail.title')}
        footer={
          errorMessage !== null ? (
            <span className="text-danger">{errorMessage}</span>
          ) : (
            <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
          )
        }
      >
        {errorMessage === null && <span className="text-body-secondary">{t('archivieren.prepare.loading')}</span>}
      </ContentBox>
    )
  }

  const mailView = preparation.mail
  const hasBody = mailView.bodyHtml.trim() !== ''
  const choiceShown = hasBody && (newPartState === 'REST' || newPartState === 'NOT_FOUND')

  // Nachrichtenart: Vorschlag aus Postfach, Triggerwörtern und Trigger-Hooks, die eigene Wahl gewinnt
  const suggestedMessageSubClassKey = suggestedKeyOf(messageSubClasses ?? [], preparation.messageSubClassBaseScores, hooks)
  const messageSubClassKey = chosenMessageSubClassKey ?? suggestedMessageSubClassKey

  /** Die Dateiart eines neuen Anhangs: die eigene Wahl, sonst der Vorschlag, sonst TEMP, wenn er dort schreiben darf */
  function fileSubClassKeyOf(attachment: MessageArchiveAttachment): string | null {
    const sha256 = attachment.sha256 ?? ''
    if (chosenFileSubClassKeys[sha256]) {
      return chosenFileSubClassKeys[sha256]
    }
    const writable = (fileSubClasses ?? []).filter((fileSubClass) => fileSubClass.key in attachment.fileSubClassBaseScores)
    const suggested = suggestedKeyOf(writable, attachment.fileSubClassBaseScores, hooks)
    if (suggested !== null) {
      return suggested
    }
    return writable.some((fileSubClass) => fileSubClass.key === MESSAGE_ARCHIVE_FALLBACK_FILE_SUB_CLASS_KEY) ? MESSAGE_ARCHIVE_FALLBACK_FILE_SUB_CLASS_KEY : null
  }

  const listedAttachments = preparation.attachments.filter((attachment) => attachment.state !== 'SIGNATURE')
  const signatureCount = preparation.attachments.length - listedAttachments.length
  const noAccess = preparation.attachments.some((attachment) => attachment.state === 'NO_ACCESS')

  /** Je Datei die Entscheidung, wie das Backend sie erwartet; bekannte Signaturen und Verweise brauchen keine */
  function attachmentChoices(): MessageArchiveRequest['attachments'] {
    const choices: MessageArchiveRequest['attachments'] = []
    for (const attachment of preparation?.attachments ?? []) {
      const sha256 = attachment.sha256
      if (sha256 === null) {
        continue
      }
      if (attachment.state === 'NEW') {
        if (ignoreMarks.has(sha256)) {
          choices.push({ sha256, action: 'IGNORE', fileSubClassKey: null })
        } else if (signatureMarks.has(sha256)) {
          choices.push({ sha256, action: 'SIGNATURE', fileSubClassKey: null })
        } else {
          choices.push({ sha256, action: 'ARCHIVE', fileSubClassKey: fileSubClassKeyOf(attachment) })
        }
      } else if (attachment.state === 'MERGE') {
        choices.push({ sha256, action: ignoreMarks.has(sha256) ? 'IGNORE' : 'ARCHIVE', fileSubClassKey: null })
      } else if (attachment.state === 'IGNORED') {
        choices.push({ sha256, action: archiveAnyway.has(sha256) ? 'ARCHIVE' : 'SKIP', fileSubClassKey: null })
      }
    }
    return choices
  }

  const choices = attachmentChoices()
  const missingFileSubClass = choices.some((choice) => choice.action === 'ARCHIVE' && choice.fileSubClassKey === null &&
    preparation.attachments.some((attachment) => attachment.sha256 === choice.sha256 && attachment.state === 'NEW'))
  let archiveBlockedReason: string | null = null
  if (noAccess) {
    archiveBlockedReason = t('archivieren.prepare.archiveBlockedNoAccess')
  } else if (!messageSubClassKey) {
    archiveBlockedReason = t('archivieren.prepare.archiveMissingMessageSubClass')
  } else if (missingFileSubClass) {
    archiveBlockedReason = t('archivieren.prepare.archiveMissingFileSubClass')
  }

  function archive() {
    if (!mailbox || !exchangeMailId || !messageSubClassKey || archiveBlockedReason !== null) {
      return
    }
    setArchiving(true)
    setArchiveErrorMessage(null)
    archiveMessage({ mailbox, exchangeMailId, folder, messageSubClassKey, wholeMail, hooks, attachments: choices })
      .then((result) => {
        showToast({
          kind: 'success',
          title: t('archivieren.prepare.archived', { id: result.data.id }),
          text: t('archivieren.prepare.archivedSummary', {
            newAttachments: result.data.newAttachments,
            mergedAttachments: result.data.mergedAttachments,
            signatures: result.data.signatures,
            ignored: result.data.ignored,
          }),
        })
        navigate(backPath)
      })
      .catch((error: unknown) => {
        const message = error instanceof RequestError ? error.message : t('common.unknownError')
        setArchiveErrorMessage(message)
        showToast({ kind: 'error', title: t('archivieren.prepare.archiveFailed'), text: message })
        setArchiving(false)
      })
  }

  function hooksWithValue(value: string): string[] {
    return Object.entries(hooks)
      .filter(([, values]) => values.includes(value))
      .map(([hookKey]) => hookKey)
  }

  function existingFileSubClassName(key: string | null): string {
    const fileSubClass = (fileSubClasses ?? []).find((candidate) => candidate.key === key)
    return fileSubClass ? fileSubClassNameOf(fileSubClass, language) : (key ?? '')
  }

  function attachmentRow(attachment: MessageArchiveAttachment, index: number) {
    const sha256 = attachment.sha256 ?? ''
    const ignoredNow = attachment.state === 'IGNORED' ? !archiveAnyway.has(sha256) : ignoreMarks.has(sha256)
    return (
      <div key={`${sha256}-${index}`} className={ignoredNow ? 'archivieren-attachment archivieren-attachment-ignored' : 'archivieren-attachment'}>
        <div className="d-flex flex-wrap align-items-baseline gap-2">
          <strong>{attachment.name}</strong>
          <span className="text-body-secondary small">
            {readableBytes(attachment.size)}
            {attachment.inline ? ` · ${t('archivieren.mail.inlineYes')}` : ''}
          </span>
          <span className="badge text-bg-secondary">{t(`archivieren.prepare.states.${attachment.state}`)}</span>
        </div>
        {attachment.state === 'NO_ACCESS' && <div className="text-danger small">{t('archivieren.prepare.noAccessHint')}</div>}
        {attachment.state === 'NO_FILE' && <div className="text-body-secondary small">{t('archivieren.prepare.noFileHint')}</div>}
        {(attachment.state === 'MERGE' || attachment.state === 'IGNORED') && (
          <div className="small">
            {t('archivieren.prepare.existingFileSubClass')}: {existingFileSubClassName(attachment.existingFileSubClassKey)}
          </div>
        )}
        {attachment.state === 'IGNORED' && (
          <label className="form-check small mb-0">
            <input
              className="form-check-input"
              type="checkbox"
              checked={archiveAnyway.has(sha256)}
              onChange={(event) => setArchiveAnyway(toggled(archiveAnyway, sha256, event.target.checked))}
            />
            <span className="form-check-label">{t('archivieren.prepare.archiveAnyway')}</span>
          </label>
        )}
        {(attachment.state === 'NEW' || attachment.state === 'MERGE') && (
          <div className="d-flex flex-wrap gap-3 small">
            {attachment.state === 'NEW' && (
              <label className="form-check mb-0">
                <input
                  className="form-check-input"
                  type="checkbox"
                  checked={signatureMarks.has(sha256)}
                  onChange={(event) => setSignatureMarks(toggled(signatureMarks, sha256, event.target.checked))}
                />
                <span className="form-check-label">{t('archivieren.prepare.markSignature')}</span>
              </label>
            )}
            <label className="form-check mb-0">
              <input
                className="form-check-input"
                type="checkbox"
                checked={ignoreMarks.has(sha256)}
                onChange={(event) => setIgnoreMarks(toggled(ignoreMarks, sha256, event.target.checked))}
              />
              <span className="form-check-label">{t('archivieren.prepare.markIgnore')}</span>
            </label>
          </div>
        )}
        {attachment.state === 'NEW' && !signatureMarks.has(sha256) && !ignoreMarks.has(sha256) && (
          <FileSubClassPicker
            fileSubClasses={(fileSubClasses ?? []).filter((fileSubClass) => fileSubClass.key in attachment.fileSubClassBaseScores)}
            value={fileSubClassKeyOf(attachment)}
            onChange={(key) => setChosenFileSubClassKeys({ ...chosenFileSubClassKeys, [sha256]: key })}
            language={language}
            required
          />
        )}
      </div>
    )
  }

  const receivedOrSent = mailView.receivedDateTime ?? mailView.sentDateTime

  // dasselbe Paar im Kopf und im Footer des Prüfers, aus den beiden Knöpfen einzeln, weil nur Archivieren das Hoverlay
  // mit dem Sperrgrund trägt; der Grund steht auch im Footer
  const archiveButtons = (
    <span className="ms-auto d-flex gap-2">
      <CancelButton disabled={archiving} onClick={() => navigate(backPath)} />
      <Hoverlay text={archiveBlockedReason}>
        <span>
          <ConfirmButton
            label={archiving ? t('archivieren.prepare.archiving') : t('archivieren.prepare.archive')}
            onClick={archive}
            disabled={archiveBlockedReason !== null}
            busy={archiving}
          />
        </span>
      </Hoverlay>
    </span>
  )

  return (
    <div className="row g-3">
      <div className="col-lg-7 d-flex flex-column gap-3">
        <ContentBox title={mailView.subject || t('archivieren.noSubject')} footer={<span>{mailbox}</span>}>
          <dl className="mail-header">
            <dt>{t('archivieren.mail.from')}</dt>
            <dd>{mailView.from ? recipientText(mailView.from) : ''}</dd>
            <dt>{t('archivieren.mail.to')}</dt>
            <dd>{mailView.toRecipients.map(recipientText).join(', ')}</dd>
            {mailView.ccRecipients.length > 0 && (
              <>
                <dt>{t('archivieren.mail.cc')}</dt>
                <dd>{mailView.ccRecipients.map(recipientText).join(', ')}</dd>
              </>
            )}
            {mailView.bccRecipients.length > 0 && (
              <>
                <dt>{t('archivieren.mail.bcc')}</dt>
                <dd>{mailView.bccRecipients.map(recipientText).join(', ')}</dd>
              </>
            )}
            <dt>{t('archivieren.mail.date')}</dt>
            <dd>{receivedOrSent ? new Date(receivedOrSent).toLocaleString(i18n.language) : ''}</dd>
          </dl>
          {choiceShown && (
            <div className="form-check mb-1">
              <input id="archivieren-whole-mail" className="form-check-input" type="checkbox" checked={wholeMail} onChange={(event) => setWholeMail(event.target.checked)} />
              <label className="form-check-label" htmlFor="archivieren-whole-mail">
                {t('archivieren.mail.wholeMail')}
              </label>
            </div>
          )}
          {choiceShown && <div className="form-text mb-3">{newPartState === 'NOT_FOUND' ? t('archivieren.mail.newPartNotFound') : t('archivieren.mail.wholeMailHint')}</div>}
          {hasBody ? (
            <MailHtmlFrame
              html={mailView.bodyHtml}
              title={mailView.subject}
              newPartHtml={mailView.uniqueBodyHtml}
              restMode={wholeMail ? 'show' : 'warn'}
              onNewPartFound={setNewPartState}
            />
          ) : (
            <span className="text-body-secondary">{t('archivieren.mail.noBody')}</span>
          )}
        </ContentBox>
        {preparation.attachments.length > 0 && (
          <ContentBox
            title={t('archivieren.mail.attachments')}
            footer={
              <span>
                {t('archivieren.mail.attachmentCount', { count: listedAttachments.length })}
                {signatureCount > 0 && ` · ${t('archivieren.prepare.signaturesHidden', { count: signatureCount })}`}
              </span>
            }
          >
            {listedAttachments.length === 0 ? (
              <span className="text-body-secondary">{t('archivieren.prepare.onlySignatures')}</span>
            ) : (
              <div className="d-flex flex-column gap-3">{listedAttachments.map(attachmentRow)}</div>
            )}
          </ContentBox>
        )}
      </div>

      <div className="col-lg-5">
        <ContentBox
          title={t('archivieren.prepare.inspectorTitle')}
          actions={archiveButtons}
          footer={
            <>
              {archiveErrorMessage !== null ? (
                <span className="text-danger">{archiveErrorMessage}</span>
              ) : archiveBlockedReason !== null ? (
                <span className={noAccess ? 'text-danger' : undefined}>{archiveBlockedReason}</span>
              ) : null}
              {archiveButtons}
            </>
          }
        >
          <div className="d-flex flex-column gap-4">
            <section>
              {(messageSubClasses ?? []).length === 0 ? (
                <>
                  <div className="fw-semibold">{t('archivieren.prepare.messageSubClass')}</div>
                  <div className="text-body-secondary small">{t('archivieren.prepare.noMessageSubClasses')}</div>
                </>
              ) : (
                <>
                  {/* dieselben Kacheln wie bei der Dateiart (User 2026-10-14) */}
                  <FileSubClassPicker
                    fileSubClasses={messageSubClasses ?? []}
                    value={messageSubClassKey}
                    onChange={setChosenMessageSubClassKey}
                    language={language}
                    title={t('archivieren.prepare.messageSubClass')}
                    required
                  />
                  {chosenMessageSubClassKey === null && suggestedMessageSubClassKey !== null && (
                    <div className="form-text">{t('archivieren.prepare.suggestedMessageSubClass')}</div>
                  )}
                </>
              )}
            </section>

            <section>
              <div className="fw-semibold mb-2">{t('archivieren.prepare.participants')}</div>
              {!preparation.contactsAllowed && <div className="text-body-secondary small mb-2">{t('archivieren.prepare.contactsNotAllowed')}</div>}
              <div className="d-flex flex-column gap-2">
                {preparation.participants.map((participant, index) => (
                  <div key={`${participant.role}-${participant.address}-${index}`}>
                    <div>
                      <span className="text-body-secondary small me-2">{t(`archivieren.prepare.roles.${participant.role}`)}</span>
                      {recipientText({ name: participant.name, address: participant.address })}
                    </div>
                    {participant.contacts.map((contact) => (
                      <div key={kontakteValueOf(contact, 'id')} className="d-flex flex-wrap align-items-center gap-2 ms-3 small">
                        <span>{kontakteValueOf(contact, KONTAKTE_NAME_FIELD_NAME) || kontakteValueOf(contact, 'id')}</span>
                        {CONTACT_HOOK_KEYS.map((hookKey) => {
                          const solrHook = (solrHooks ?? []).find((candidate) => candidate.key === hookKey)
                          const values = kontakteValuesOf(contact[hookKey])
                          return solrHook && values.length > 0 ? <SolrHookValues key={hookKey} solrHook={solrHook} values={values} language={language} /> : null
                        })}
                      </div>
                    ))}
                  </div>
                ))}
              </div>
            </section>

            <section>
              <div className="fw-semibold mb-2">{t('archivieren.prepare.numbers')}</div>
              {preparation.numbers.length === 0 ? (
                <div className="text-body-secondary small">{t('archivieren.prepare.noNumbers')}</div>
              ) : (
                <div className="d-flex flex-column gap-2">
                  {preparation.numbers.map((number) => {
                    const inHooks = hooksWithValue(number.lookup.belegnummer)
                    return (
                      <div key={number.lookup.belegnummer} className="d-flex flex-wrap align-items-center gap-2">
                        <strong>{number.lookup.belegnummer}</strong>
                        <span className="text-body-secondary small">{number.lookup.responseMappingKey ?? number.lookup.objectType}</span>
                        {inHooks.map((hookKey) => {
                          const solrHook = (solrHooks ?? []).find((candidate) => candidate.key === hookKey)
                          return solrHook ? <SolrHookChip key={hookKey} solrHook={solrHook} language={language} /> : <code key={hookKey}>{hookKey}</code>
                        })}
                        <SolrHookPicker
                          label={t('archivieren.prepare.takeIntoHook')}
                          language={language}
                          excludeKeys={inHooks}
                          onPick={(solrHook) => setHooks(withHookValue(hooks, solrHook.key, number.lookup.belegnummer))}
                        />
                      </div>
                    )
                  })}
                </div>
              )}
            </section>

            <SolrHookFields hooks={hooks} onChange={setHooks} language={language} title={t('archivieren.prepare.hooks')} />
          </div>
        </ContentBox>
      </div>
    </div>
  )
}
