import { useCallback, useState, type CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'
import { RequestError, type RequestResult } from '../../api/client.ts'
import { useLoad } from '../../api/useLoad.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { Popup } from '../../components/Popup.tsx'
import { Subheader } from '../../components/Subheader.tsx'
import { showToast } from '../../components/toastStore.ts'
import { AccessRoleBadge } from '../accessrole/AccessRoleBadge.tsx'
import { useSession } from '../login/sessionContext.ts'
import { SolrHookFields } from '../solrmanager/SolrHookFields.tsx'
import {
  addTodojobRueckmeldungEntry,
  answerTodojobQuestion,
  askTodojobQuestion,
  changeTodojobDueDate,
  changeTodojobLabels,
  claimTodojob,
  commentTodojob,
  confirmTodojob,
  getTodojob,
  isTodojobGroupJob,
  isTodojobOwnerSide,
  isTodojobServantSide,
  releaseTodojobClaim,
  respondTodojob,
  retractTodojobRueckmeldungEntry,
  returnTodojob,
  takeOverTodojobClaim,
  TODOJOB_REFERENCE_ENTRY_TYPES,
  todojobAnswerOf,
  todojobEntriesOf,
  todojobEntryTypeLabel,
  todojobFormatDate,
  todojobFormatTime,
  todojobNumberOf,
  todojobRetractedNumbers,
  todojobStatusColor,
  todojobValueOf,
  todojobValuesOf,
  withdrawTodojob,
  type TodojobDocument,
  type TodojobEntry,
  type TodojobRueckmeldungEntryType,
} from './Todojob.ts'
import { TodojobStatusBadge } from './TodojobStatusBadge.tsx'
import { TodojobUsersName } from './TodojobUsersName.tsx'

/** Wohin ein Wert einer Rückmeldung führt: DMS-Datei in die Dokumentseite, Artikelnummer in die Mappe, sonst nirgends */
function referencePathOf(type: string | undefined, value: string): string | null {
  if (type === 'DMS_DATEI') {
    return `/dms/solr-document/${encodeURIComponent(value)}`
  }
  if (type === 'ARTIKELNUMMER') {
    return `/artikel/${encodeURIComponent(value)}`
  }
  return null
}

/**
 * Die Seite eines Jobs (User 2026-10-16): oben der Befehl mit Status, dann Aufgabe, Kontext, Parteien, Frist, Labels
 * und Hooks; die Rückmeldung in Schritten mit dem Beitragsformular des Servants; die Rückfragen als Faden mit Antworten
 * des Owners; rechts die Vorgänge, die der User in diesem Zustand hat, und der Verlauf aus dem Log. Eine Leseseite mit
 * Vorgängen: kein Abbrechen, nur „zurück zu …“ im header, wenn ein origin mitkam.
 */
export function TodojobPage() {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const { id = '' } = useParams()
  const { sessionInfo } = useSession()
  const { accessRoles, accessRoleCollections, fileSubClassDestinations } = useMasterData()
  const load = useCallback(() => getTodojob(id), [id])
  const { data: job, errorMessage, reload } = useLoad(load)

  // die Formulare der Vorgänge
  const [entryType, setEntryType] = useState<TodojobRueckmeldungEntryType>('TEXT')
  const [entryText, setEntryText] = useState('')
  const [entryValue, setEntryValue] = useState('')
  const [questionText, setQuestionText] = useState('')
  const [answerTexts, setAnswerTexts] = useState<Record<number, string>>({})
  const [commentText, setCommentText] = useState('')
  const [takeReason, setTakeReason] = useState<string | null>(null)
  const [returnReason, setReturnReason] = useState<string | null>(null)
  const [withdrawing, setWithdrawing] = useState(false)
  const [dueDate, setDueDate] = useState<string | null>(null)
  const [labelsDraft, setLabelsDraft] = useState<{ keys: string[]; dauerhaft: boolean } | null>(null)
  const [busy, setBusy] = useState(false)

  if (errorMessage !== null) {
    return (
      <>
        <Subheader status={<><Icon name="todojob" /> {t('todojob.job', { id })}</>} />
        <div className="main-content">
          <ContentBox title={t('todojob.job', { id })} footer={<span className="text-danger">{errorMessage}</span>}>
            <p className="mb-0">{t('todojob.page.notFound', { id })}</p>
          </ContentBox>
        </div>
      </>
    )
  }
  if (job === null || sessionInfo === null) {
    return (
      <>
        <Subheader status={<><Icon name="todojob" /> {t('todojob.job', { id })}</>} />
        <div className="main-content">
          <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
        </div>
      </>
    )
  }

  const status = todojobValueOf(job, 'status')
  const claimGuid = todojobValueOf(job, 'claimGuid')
  const me = sessionInfo.usersGuid ?? ''
  const owner = isTodojobOwnerSide(job, sessionInfo)
  const servant = isTodojobServantSide(job, sessionInfo)
  const groupJob = isTodojobGroupJob(job)
  const finished = status === 'ABGESCHLOSSEN'
  const working = status === 'OFFEN' || status === 'CLAIMED' || status === 'IN_BEARBEITUNG'
  const holderIsOther = claimGuid !== '' && claimGuid !== me
  const canContribute = servant && working && !holderIsOther
  const openQuestions = todojobNumberOf(job, 'offeneRueckfragen')
  const requiredType = todojobValueOf(job, 'rueckmeldungsArt')
  const entries = todojobEntriesOf(job, 'rueckmeldung')
  const retracted = todojobRetractedNumbers(entries)
  const questions = todojobEntriesOf(job, 'rueckfragen')
  const answers = todojobEntriesOf(job, 'rueckantworten')
  const logEntries = todojobEntriesOf(job, 'log')
  const kontext = todojobValuesOf(job.kontext)
  // die Hooks des Jobs: SolrHookFields zeigt davon nur die Felder, die laut Stammdaten Hooks sind
  const hooks: Record<string, string[]> = {}
  for (const [fieldName, value] of Object.entries(job)) {
    if (Array.isArray(value)) {
      hooks[fieldName] = value.map(String)
    }
  }

  /** Ein Vorgang: ausführen, den Job neu laden, Fehler als Toast */
  function run(action: () => Promise<RequestResult<TodojobDocument>>, successText?: string) {
    setBusy(true)
    action()
      .then(() => {
        if (successText !== undefined) {
          showToast({ kind: 'success', title: t('todojob.job', { id }), text: successText })
        }
        reload()
      })
      .catch((error: unknown) => {
        showToast({ kind: 'error', title: t('todojob.job', { id }), text: error instanceof RequestError ? error.message : t('common.unknownError') })
      })
      .finally(() => setBusy(false))
  }

  function addEntry() {
    const needsValue = entryType === 'ZAHL' || entryType === 'DATUM' || entryType === 'BOOL' || TODOJOB_REFERENCE_ENTRY_TYPES.includes(entryType)
    run(
      () => addTodojobRueckmeldungEntry(id, entryType, entryText.trim() === '' ? null : entryText.trim(), needsValue ? entryValue.trim() : null),
    )
    setEntryText('')
    setEntryValue('')
  }

  const title = (
    <>
      <span className="todojob-card-number">#{id}</span> {todojobValueOf(job, 'verb')} {todojobValueOf(job, 'objekt')}
    </>
  )

  const servantRoleBadges = (accessRoles ?? []).filter((accessRole) => todojobValuesOf(job.servantAccessRoleKeys).includes(accessRole.key))
  const servantArcBadges = (accessRoleCollections ?? []).filter((arc) => todojobValuesOf(job.servantArcKeys).includes(arc.key))
  const ownerRoleBadges = (accessRoles ?? []).filter((accessRole) => todojobValuesOf(job.ownerAccessRoleKeys).includes(accessRole.key))
  const ownerArcBadges = (accessRoleCollections ?? []).filter((arc) => todojobValuesOf(job.ownerArcKeys).includes(arc.key))

  return (
    <>
      <Subheader status={<><Icon name="todojob" /> {t('todojob.job', { id })}</>} sticky />
      <div className="main-content">
        <div className="todojob-page-grid" style={{ '--todojob-color': todojobStatusColor(status) } as CSSProperties}>
          <div className="profile-column">
            <ContentBox
              title={title}
              actions={<TodojobStatusBadge job={job} />}
              footer={
                <span>
                  {t('todojob.page.erstellt')} {todojobFormatTime(todojobValueOf(job, 'erstelltAm'), language)}
                  {todojobValueOf(job, 'abgeschlossenAm') !== '' && ` · ${t('todojob.page.abgeschlossen')} ${todojobFormatTime(todojobValueOf(job, 'abgeschlossenAm'), language)}`}
                  {' · '}
                  {t('todojob.page.requiredType', { type: todojobEntryTypeLabel(t, requiredType) })}
                </span>
              }
            >
              {todojobValueOf(job, 'aufgabe') !== '' && (
                <>
                  <h3 className="h6">{t('todojob.page.aufgabe')}</h3>
                  <p className="todojob-text">{todojobValueOf(job, 'aufgabe')}</p>
                </>
              )}
              {kontext.length > 0 && (
                <>
                  <h3 className="h6">{t('todojob.page.kontext')}</h3>
                  <ul className="todojob-kontext">
                    {kontext.map((line) => (
                      <li key={line}>{line}</li>
                    ))}
                  </ul>
                </>
              )}
              <dl className="profile-fields">
                <dt>{t('todojob.page.owner')}</dt>
                <dd>
                  <TodojobUsersName guid={todojobValueOf(job, 'ownerGuid')} />
                  {ownerRoleBadges.map((badge) => (
                    <span key={badge.key} className="ms-1"><AccessRoleBadge badge={badge} size="compact" /></span>
                  ))}
                  {ownerArcBadges.map((badge) => (
                    <span key={badge.key} className="ms-1"><AccessRoleBadge badge={badge} size="compact" shape="pill" /></span>
                  ))}
                </dd>
                <dt>{t('todojob.page.servants')}</dt>
                <dd>
                  {todojobValuesOf(job.servantGuids).map((guid) => (
                    <span key={guid} className="me-2"><TodojobUsersName guid={guid} /></span>
                  ))}
                  {servantRoleBadges.map((badge) => (
                    <span key={badge.key} className="me-1"><AccessRoleBadge badge={badge} size="compact" /></span>
                  ))}
                  {servantArcBadges.map((badge) => (
                    <span key={badge.key} className="me-1"><AccessRoleBadge badge={badge} size="compact" shape="pill" /></span>
                  ))}
                </dd>
                {groupJob && (
                  <>
                    <dt>{t('todojob.page.claim')}</dt>
                    <dd>{claimGuid === '' ? t('todojob.page.noClaim') : <TodojobUsersName guid={claimGuid} />}</dd>
                  </>
                )}
                <dt>{t('todojob.page.frist')}</dt>
                <dd>{todojobValueOf(job, 'fristAm') === '' ? '–' : todojobFormatDate(todojobValueOf(job, 'fristAm'), language)}</dd>
                <dt>{t('todojob.page.labels')}</dt>
                <dd>
                  {todojobValuesOf(job.displayContextKeys)
                    .map((key) => translate(fileSubClassDestinations?.find((destination) => destination.key === key)?.displayName ?? null, language) || key)
                    .join(', ') || '–'}
                  {todojobValueOf(job, 'dauerhaftRelevant') === 'true' && ` · ${t('todojob.page.dauerhaftRelevant')}`}
                </dd>
              </dl>
              <SolrHookFields hooks={hooks} language={language} title={t('todojob.page.hooks')} />
            </ContentBox>

            <ContentBox
              title={
                <>
                  {t('todojob.page.rueckmeldung')} {entries.length > 0 && <span className="profile-count-chip">{entries.length}</span>}
                </>
              }
              footer={
                canContribute ? (
                  <div className="todojob-inline-form">
                    <select className="form-select form-select-sm" value={entryType} onChange={(event) => setEntryType(event.target.value as TodojobRueckmeldungEntryType)}>
                      {(['TEXT', 'COMMENT', 'BOOL', 'ZAHL', 'DATUM', 'DMS_DATEI', 'ARTIKELNUMMER', 'STERNZEIT'] as const).map((type) => (
                        <option key={type} value={type}>
                          {t(`todojob.entryType.${type}`)}
                        </option>
                      ))}
                    </select>
                    {(entryType === 'ZAHL' || entryType === 'DATUM' || entryType === 'BOOL' || TODOJOB_REFERENCE_ENTRY_TYPES.includes(entryType)) && (
                      <input
                        className="form-control form-control-sm"
                        value={entryValue}
                        placeholder={t('todojob.page.entryValue')}
                        onChange={(event) => setEntryValue(event.target.value)}
                      />
                    )}
                    <input
                      className="form-control form-control-sm flex-grow-1"
                      value={entryText}
                      placeholder={t('todojob.page.entryText')}
                      onChange={(event) => setEntryText(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') {
                          event.preventDefault()
                          addEntry()
                        }
                      }}
                    />
                    <button type="button" className="button-save" disabled={busy} onClick={addEntry}>
                      <Icon name="add" /> {t('todojob.page.addEntry')}
                    </button>
                  </div>
                ) : undefined
              }
            >
              {entries.length === 0 && <p className="mb-0">{t('todojob.page.noEntries')}</p>}
              {entries.length > 0 && (
                <div className="todojob-entries">
                  {entries.map((entry) => {
                    const isRetracted = entry.nr !== undefined && retracted.has(entry.nr)
                    const referencePath = entry.value === undefined ? null : referencePathOf(entry.type, entry.value)
                    return (
                      <div key={entry.nr ?? entry.at} className={isRetracted ? 'todojob-entry todojob-entry-retracted' : 'todojob-entry'}>
                        <div className="todojob-entry-head">
                          <span>#{entry.nr}</span>
                          <span>{todojobEntryTypeLabel(t, entry.type ?? 'TEXT')}</span>
                          <TodojobUsersName guid={entry.user} />
                          <span>{todojobFormatTime(entry.at, language)}</span>
                          {entry.type === 'RETRACTED' && <span>{t('todojob.page.retracts', { nr: entry.retracts })}</span>}
                          {canContribute && !isRetracted && entry.type !== 'RETRACTED' && entry.nr !== undefined && (
                            <button type="button" className="button-other" disabled={busy} onClick={() => run(() => retractTodojobRueckmeldungEntry(id, entry.nr ?? 0))}>
                              {t('todojob.page.retract')}
                            </button>
                          )}
                        </div>
                        {entry.value !== undefined && (
                          <div className="font-monospace">
                            {referencePath !== null ? <Link to={referencePath}>{entry.value}</Link> : entry.value}
                          </div>
                        )}
                        {entry.text !== undefined && <div className="todojob-text">{entry.text}</div>}
                      </div>
                    )
                  })}
                </div>
              )}
            </ContentBox>

            <ContentBox
              title={
                <>
                  {t('todojob.page.rueckfragen')} {openQuestions > 0 && <span className="profile-count-chip">{openQuestions}</span>}
                </>
              }
              footer={
                servant && !finished ? (
                  <div className="todojob-inline-form">
                    <input
                      className="form-control form-control-sm flex-grow-1"
                      value={questionText}
                      placeholder={t('todojob.page.askPlaceholder')}
                      onChange={(event) => setQuestionText(event.target.value)}
                    />
                    <button
                      type="button"
                      className="button-save"
                      disabled={busy || questionText.trim() === ''}
                      onClick={() => {
                        run(() => askTodojobQuestion(id, questionText.trim()))
                        setQuestionText('')
                      }}
                    >
                      <Icon name="question" /> {t('todojob.page.ask')}
                    </button>
                  </div>
                ) : undefined
              }
            >
              {questions.length === 0 && <p className="mb-0">{t('todojob.page.noQuestions')}</p>}
              {questions.length > 0 && (
                <div className="todojob-entries">
                  {questions.map((question) => {
                    const answer = question.nr === undefined ? null : todojobAnswerOf(answers, question.nr)
                    return (
                      <div key={question.nr ?? question.at} className="todojob-entry">
                        <div className="todojob-entry-head">
                          <Icon name="question" /> <span>#{question.nr}</span>
                          <TodojobUsersName guid={question.user} />
                          <span>{todojobFormatTime(question.at, language)}</span>
                          {answer === null && <span className="text-danger fw-semibold">{t('todojob.page.unanswered')}</span>}
                        </div>
                        <div className="todojob-text">{question.text}</div>
                        {answer !== null && (
                          <div className="todojob-answer">
                            <div className="todojob-entry-head">
                              <Icon name="reply" /> <TodojobUsersName guid={answer.user} />
                              <span>{todojobFormatTime(answer.at, language)}</span>
                            </div>
                            <div className="todojob-text">{answer.text}</div>
                          </div>
                        )}
                        {answer === null && owner && question.nr !== undefined && (
                          <div className="todojob-inline-form mt-2">
                            <input
                              className="form-control form-control-sm flex-grow-1"
                              value={answerTexts[question.nr] ?? ''}
                              placeholder={t('todojob.page.answerPlaceholder')}
                              onChange={(event) => setAnswerTexts({ ...answerTexts, [question.nr ?? 0]: event.target.value })}
                            />
                            <button
                              type="button"
                              className="button-save"
                              disabled={busy || (answerTexts[question.nr] ?? '').trim() === ''}
                              onClick={() => run(() => answerTodojobQuestion(id, question.nr ?? 0, (answerTexts[question.nr ?? 0] ?? '').trim()))}
                            >
                              <Icon name="reply" /> {t('todojob.page.answer')}
                            </button>
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>
              )}
            </ContentBox>
          </div>

          <div className="profile-column">
            <ContentBox title={t('todojob.page.actions')} footer={busy ? <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} /> : undefined}>
              <div className="todojob-actions">
                {servant && groupJob && status === 'OFFEN' && claimGuid === '' && (
                  <button type="button" className="button-other" disabled={busy} onClick={() => run(() => claimTodojob(id), t('todojob.card.claimed'))}>
                    <Icon name="claim" /> {t('todojob.card.claim')}
                  </button>
                )}
                {claimGuid !== '' && !finished && (claimGuid === me || owner) && (
                  <button type="button" className="button-other" disabled={busy} onClick={() => run(() => releaseTodojobClaim(id))}>
                    <Icon name="claim" /> {t('todojob.page.releaseClaim')}
                  </button>
                )}
                {servant && holderIsOther && !finished && (
                  <button type="button" className="button-other" disabled={busy} onClick={() => setTakeReason('')}>
                    <Icon name="claim" /> {t('todojob.page.takeClaim')}
                  </button>
                )}
                {canContribute && (
                  <button type="button" className="button-save" disabled={busy || openQuestions > 0} onClick={() => run(() => respondTodojob(id), t('todojob.page.responded'))}>
                    <Icon name="check" /> {t('todojob.page.respond')}
                  </button>
                )}
                {owner && status === 'FERTIG' && (
                  <>
                    <button type="button" className="button-save" disabled={busy} onClick={() => run(() => confirmTodojob(id), t('todojob.page.confirmed'))}>
                      <Icon name="check" /> {t('todojob.page.confirm')}
                    </button>
                    <button type="button" className="button-cancel" disabled={busy} onClick={() => setReturnReason('')}>
                      <Icon name="returned" /> {t('todojob.page.return')}
                    </button>
                  </>
                )}
                {owner && !finished && (
                  <>
                    <button type="button" className="button-other" disabled={busy} onClick={() => setDueDate(todojobValueOf(job, 'fristAm').slice(0, 10))}>
                      <Icon name="due" /> {t('todojob.page.dueDate')}
                    </button>
                    <button
                      type="button"
                      className="button-other"
                      disabled={busy}
                      onClick={() => setLabelsDraft({ keys: todojobValuesOf(job.displayContextKeys), dauerhaft: todojobValueOf(job, 'dauerhaftRelevant') === 'true' })}
                    >
                      <Icon name="fileSubClassDestinations" /> {t('todojob.page.labels')}
                    </button>
                    <button type="button" className="button-cancel" disabled={busy} onClick={() => setWithdrawing(true)}>
                      <Icon name="close" /> {t('todojob.page.withdraw')}
                    </button>
                  </>
                )}
                {(owner || servant) && (
                  <div className="todojob-inline-form">
                    <input
                      className="form-control form-control-sm flex-grow-1"
                      value={commentText}
                      placeholder={t('todojob.page.commentPlaceholder')}
                      onChange={(event) => setCommentText(event.target.value)}
                    />
                    <button
                      type="button"
                      className="button-other"
                      disabled={busy || commentText.trim() === ''}
                      onClick={() => {
                        run(() => commentTodojob(id, commentText.trim()))
                        setCommentText('')
                      }}
                    >
                      <Icon name="comment" /> {t('todojob.page.comment')}
                    </button>
                  </div>
                )}
              </div>
            </ContentBox>

            <ContentBox title={t('todojob.page.log')}>
              <ul className="todojob-log">
                {logEntries.map((entry: TodojobEntry, index) => (
                  <li key={index}>
                    <span className="todojob-log-time">{todojobFormatTime(entry.at, language)}</span>{' '}
                    <strong>{t(`todojob.action.${entry.action ?? 'COMMENT'}`, { nr: entry.nr ?? '' })}</strong>{' '}
                    <TodojobUsersName guid={entry.user} />
                    {entry.from !== undefined && (
                      <>
                        {' '}
                        ← <TodojobUsersName guid={entry.from} />
                      </>
                    )}
                    {entry.rueckmeldungBis !== undefined && ` · ${t('todojob.page.rueckmeldungBis', { nr: entry.rueckmeldungBis })}`}
                    {entry.text !== undefined && entry.text !== '' && <div className="todojob-text">{entry.text}</div>}
                  </li>
                ))}
              </ul>
            </ContentBox>
          </div>
        </div>
      </div>

      <Popup
        open={takeReason !== null}
        title={t('todojob.page.takeClaim')}
        narrow
        onClose={() => setTakeReason(null)}
        footer={
          <button
            type="button"
            className="button-save"
            disabled={busy || (takeReason ?? '').trim() === ''}
            onClick={() => {
              run(() => takeOverTodojobClaim(id, (takeReason ?? '').trim()))
              setTakeReason(null)
            }}
          >
            <Icon name="claim" /> {t('todojob.page.takeClaim')}
          </button>
        }
      >
        <label className="todojob-form-field">
          <span className="form-label mb-1">{t('todojob.page.takeReason')}</span>
          <textarea className="form-control" rows={3} value={takeReason ?? ''} onChange={(event) => setTakeReason(event.target.value)} />
        </label>
      </Popup>

      <Popup
        open={returnReason !== null}
        title={t('todojob.page.return')}
        narrow
        onClose={() => setReturnReason(null)}
        footer={
          <button
            type="button"
            className="button-save"
            disabled={busy || (returnReason ?? '').trim() === ''}
            onClick={() => {
              run(() => returnTodojob(id, (returnReason ?? '').trim(), null, null), t('todojob.page.returned'))
              setReturnReason(null)
            }}
          >
            <Icon name="returned" /> {t('todojob.page.return')}
          </button>
        }
      >
        <label className="todojob-form-field">
          <span className="form-label mb-1">{t('todojob.page.returnReason')}</span>
          <textarea className="form-control" rows={3} value={returnReason ?? ''} onChange={(event) => setReturnReason(event.target.value)} />
        </label>
      </Popup>

      <Popup
        open={dueDate !== null}
        title={t('todojob.page.dueDate')}
        narrow
        onClose={() => setDueDate(null)}
        footer={
          <button
            type="button"
            className="button-save"
            disabled={busy}
            onClick={() => {
              run(() => changeTodojobDueDate(id, dueDate === null || dueDate === '' ? null : `${dueDate}T00:00:00Z`))
              setDueDate(null)
            }}
          >
            <Icon name="check" /> {t('common.save')}
          </button>
        }
      >
        <label className="todojob-form-field">
          <span className="form-label mb-1">{t('todojob.page.frist')}</span>
          <input className="form-control" type="date" value={dueDate ?? ''} onChange={(event) => setDueDate(event.target.value)} />
        </label>
      </Popup>

      <Popup
        open={labelsDraft !== null}
        title={t('todojob.page.labels')}
        narrow
        onClose={() => setLabelsDraft(null)}
        footer={
          <button
            type="button"
            className="button-save"
            disabled={busy}
            onClick={() => {
              if (labelsDraft !== null) {
                run(() => changeTodojobLabels(id, labelsDraft.keys, labelsDraft.dauerhaft))
              }
              setLabelsDraft(null)
            }}
          >
            <Icon name="check" /> {t('common.save')}
          </button>
        }
      >
        {labelsDraft !== null && (
          <div className="d-flex flex-column gap-2">
            {(fileSubClassDestinations ?? []).map((destination) => (
              <label key={destination.key} className="form-check">
                <input
                  className="form-check-input"
                  type="checkbox"
                  checked={labelsDraft.keys.includes(destination.key)}
                  onChange={() =>
                    setLabelsDraft({
                      ...labelsDraft,
                      keys: labelsDraft.keys.includes(destination.key)
                        ? labelsDraft.keys.filter((key) => key !== destination.key)
                        : [...labelsDraft.keys, destination.key],
                    })
                  }
                />
                <span className="form-check-label">{translate(destination.displayName, language) || destination.key}</span>
              </label>
            ))}
            <label className="form-check">
              <input className="form-check-input" type="checkbox" checked={labelsDraft.dauerhaft} onChange={(event) => setLabelsDraft({ ...labelsDraft, dauerhaft: event.target.checked })} />
              <span className="form-check-label">{t('todojob.create.dauerhaftRelevant')}</span>
            </label>
          </div>
        )}
      </Popup>

      <DeletePopup
        open={withdrawing}
        title={t('todojob.page.withdraw')}
        onConfirm={() => withdrawTodojob(id)}
        onDeleted={reload}
        onClose={() => setWithdrawing(false)}
      >
        {t('todojob.page.withdrawQuestion', { id })}
      </DeletePopup>
    </>
  )
}
