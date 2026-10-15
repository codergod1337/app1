import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { ConfirmCancelButtons } from '../../components/ConfirmCancelButtons.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { Popup } from '../../components/Popup.tsx'
import { showToast } from '../../components/toastStore.ts'
import { UsersPicker } from '../../components/UsersPicker.tsx'
import { BadgeToggleList } from '../accessrole/BadgeToggleList.tsx'
import { usersDisplayName } from '../profile/profileDisplay.ts'
import { SolrHookFields } from '../solrmanager/SolrHookFields.tsx'
import type { Users } from '../users/Users.ts'
import {
  completeTodojobFieldValues,
  createTodojob,
  suggestSimilarTodojobs,
  TODOJOB_RUECKMELDUNGS_ARTEN,
  todojobAnswerOf,
  todojobEntriesOf,
  todojobValueOf,
  todojobValuesOf,
  type TodojobDocument,
  type TodojobRueckmeldungEntryType,
} from './Todojob.ts'

/** So lange wartet die Maske nach dem Tippen, bevor sie Vervollständigung und ähnliche Jobs holt */
const TYPING_PAUSE_MS = 350

/** Ab so vielen Zeichen in Verb oder Objekt fragt die Maske nach ähnlichen Jobs */
const SIMILAR_MIN_CHARACTERS = 3

interface TodojobCreatePopupProps {
  open: boolean
  onClose: () => void
  onCreated: (job: TodojobDocument) => void
}

/** Die Eingrenzungen als Text, eine je Zeile, ohne leere */
function kontextLinesOf(text: string): string[] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line !== '')
}

/**
 * Erteilen eines Jobs (User 2026-10-16): der Befehl aus Verb und Objekt, beide mit Vervollständigung aus dem Kern,
 * darunter die ähnlichen eigenen Jobs mit GUT_GEMACHT samt ihren Eingrenzungen, Rückfragen und Antworten, damit der
 * Owner übernimmt statt neu schreibt. Dann Aufgabe, Kontext (eine Eingrenzung je Zeile), Rückmeldungsart, Schalter,
 * Anzeigeorte, Servants (Personen, ARs, ARCs), Owner-Seite, Frist und Hooks. Abbrechen und Erteilen im Fuß.
 */
export function TodojobCreatePopup({ open, onClose, onCreated }: TodojobCreatePopupProps) {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const { accessRoles, accessRoleCollections, fileSubClassDestinations } = useMasterData()

  const [verb, setVerb] = useState('')
  const [objekt, setObjekt] = useState('')
  const [verbCompletions, setVerbCompletions] = useState<string[]>([])
  const [objektCompletions, setObjektCompletions] = useState<string[]>([])
  const [similar, setSimilar] = useState<TodojobDocument[]>([])
  const [aufgabe, setAufgabe] = useState('')
  const [kontextText, setKontextText] = useState('')
  const [rueckmeldungsArt, setRueckmeldungsArt] = useState<TodojobRueckmeldungEntryType>('TEXT')
  const [bestaetigungNoetig, setBestaetigungNoetig] = useState(true)
  const [dauerhaftRelevant, setDauerhaftRelevant] = useState(false)
  const [displayContextKeys, setDisplayContextKeys] = useState<string[]>([])
  const [servantUsers, setServantUsers] = useState<Users[]>([])
  const [servantAccessRoleKeys, setServantAccessRoleKeys] = useState<string[]>([])
  const [servantArcKeys, setServantArcKeys] = useState<string[]>([])
  const [ownerSideOpen, setOwnerSideOpen] = useState(false)
  const [ownerAccessRoleKeys, setOwnerAccessRoleKeys] = useState<string[]>([])
  const [ownerArcKeys, setOwnerArcKeys] = useState<string[]>([])
  const [fristDate, setFristDate] = useState('')
  const [hooks, setHooks] = useState<Record<string, string[]>>({})
  const [saving, setSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  // Vervollständigung von Verb und Objekt, nach einer Tipppause
  useEffect(() => {
    if (!open) {
      return
    }
    const timer = window.setTimeout(() => {
      completeTodojobFieldValues('verb', verb)
        .then(({ data }) => setVerbCompletions(data))
        .catch(() => setVerbCompletions([]))
    }, TYPING_PAUSE_MS)
    return () => window.clearTimeout(timer)
  }, [open, verb])

  useEffect(() => {
    if (!open) {
      return
    }
    const timer = window.setTimeout(() => {
      completeTodojobFieldValues('objekt', objekt)
        .then(({ data }) => setObjektCompletions(data))
        .catch(() => setObjektCompletions([]))
    }, TYPING_PAUSE_MS)
    return () => window.clearTimeout(timer)
  }, [open, objekt])

  // ähnliche Jobs: dasselbe Verb, ein ähnliches Objekt, nur GUT_GEMACHT; unter der Mindestlänge zeigt die Maske keine
  const enoughTypedForSimilar = verb.trim().length >= SIMILAR_MIN_CHARACTERS || objekt.trim().length >= SIMILAR_MIN_CHARACTERS
  useEffect(() => {
    if (!open || !enoughTypedForSimilar) {
      return
    }
    const timer = window.setTimeout(() => {
      suggestSimilarTodojobs(verb.trim(), objekt.trim())
        .then(({ data }) => setSimilar(data))
        .catch(() => setSimilar([]))
    }, TYPING_PAUSE_MS)
    return () => window.clearTimeout(timer)
  }, [open, verb, objekt, enoughTypedForSimilar])

  const shownSimilar = enoughTypedForSimilar ? similar : []
  const hasServant = servantUsers.length > 0 || servantAccessRoleKeys.length > 0 || servantArcKeys.length > 0
  const canSubmit = !saving && verb.trim() !== '' && objekt.trim() !== '' && hasServant

  /** Die Eingrenzungen eines ähnlichen Jobs übernehmen, ohne Doppelte */
  function takeKontext(job: TodojobDocument) {
    const current = kontextLinesOf(kontextText)
    const taken = todojobValuesOf(job.kontext).filter((line) => !current.includes(line))
    setKontextText([...current, ...taken].join('\n'))
  }

  function reset() {
    setVerb('')
    setObjekt('')
    setSimilar([])
    setAufgabe('')
    setKontextText('')
    setRueckmeldungsArt('TEXT')
    setBestaetigungNoetig(true)
    setDauerhaftRelevant(false)
    setDisplayContextKeys([])
    setServantUsers([])
    setServantAccessRoleKeys([])
    setServantArcKeys([])
    setOwnerSideOpen(false)
    setOwnerAccessRoleKeys([])
    setOwnerArcKeys([])
    setFristDate('')
    setHooks({})
    setErrorMessage(null)
  }

  function submit() {
    if (!canSubmit) {
      return
    }
    setSaving(true)
    setErrorMessage(null)
    const hookValuesByHookKey: Record<string, string[]> = {}
    for (const [key, values] of Object.entries(hooks)) {
      if (values.length > 0) {
        hookValuesByHookKey[key] = values
      }
    }
    createTodojob({
      verb: verb.trim(),
      objekt: objekt.trim(),
      aufgabe: aufgabe.trim() === '' ? null : aufgabe.trim(),
      kontext: kontextLinesOf(kontextText),
      rueckmeldungsArt,
      bestaetigungNoetig,
      dauerhaftRelevant,
      displayContextKeys,
      ownerAccessRoleKeys,
      ownerArcKeys,
      servantGuids: servantUsers.map((users) => users.guid),
      servantAccessRoleKeys,
      servantArcKeys,
      fristAm: fristDate === '' ? null : `${fristDate}T00:00:00Z`,
      hookValuesByHookKey,
    })
      .then(({ data }) => {
        showToast({ kind: 'success', title: t('todojob.create.title'), text: t('todojob.create.created', { id: todojobValueOf(data, 'id') }) })
        reset()
        onCreated(data)
      })
      .catch((error: unknown) => {
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
      })
      .finally(() => setSaving(false))
  }

  function toggleDisplayContextKey(key: string) {
    setDisplayContextKeys((current) => (current.includes(key) ? current.filter((candidate) => candidate !== key) : [...current, key]))
  }

  const footer = (
    <div className="d-flex flex-wrap gap-2 align-items-center justify-content-between">
      <span className="text-danger">{errorMessage}</span>
      <ConfirmCancelButtons onCancel={onClose} confirmLabel={t('todojob.create.submit')} onConfirm={submit} disabled={!canSubmit} busy={saving} />
    </div>
  )

  return (
    <Popup open={open} title={t('todojob.create.title')} footer={footer} onClose={onClose}>
      <div className="todojob-form">
        <div className="todojob-form-command">
          <label className="todojob-form-field">
            <span className="form-label mb-1">{t('todojob.create.verb')}</span>
            <input
              className="form-control"
              list="todojob-verb-completions"
              value={verb}
              placeholder={t('todojob.create.verbPlaceholder')}
              autoComplete="off"
              onChange={(event) => setVerb(event.target.value)}
            />
            <datalist id="todojob-verb-completions">
              {verbCompletions.map((value) => (
                <option key={value} value={value} />
              ))}
            </datalist>
          </label>
          <label className="todojob-form-field flex-grow-1">
            <span className="form-label mb-1">{t('todojob.create.objekt')}</span>
            <input
              className="form-control"
              list="todojob-objekt-completions"
              value={objekt}
              placeholder={t('todojob.create.objektPlaceholder')}
              autoComplete="off"
              onChange={(event) => setObjekt(event.target.value)}
            />
            <datalist id="todojob-objekt-completions">
              {objektCompletions.map((value) => (
                <option key={value} value={value} />
              ))}
            </datalist>
          </label>
        </div>

        {shownSimilar.length > 0 && (
          <div className="todojob-similar">
            <strong>{t('todojob.create.similar')}</strong>
            {shownSimilar.map((job) => {
              const questions = todojobEntriesOf(job, 'rueckfragen')
              const answers = todojobEntriesOf(job, 'rueckantworten')
              const kontext = todojobValuesOf(job.kontext)
              return (
                <div key={String(job.id)} className="todojob-similar-item">
                  <div className="todojob-similar-head">
                    <span>
                      #{todojobValueOf(job, 'id')} {todojobValueOf(job, 'verb')} {todojobValueOf(job, 'objekt')}
                    </span>
                    {kontext.length > 0 && (
                      <button type="button" className="button-other" onClick={() => takeKontext(job)}>
                        {t('todojob.create.takeKontext')}
                      </button>
                    )}
                  </div>
                  {kontext.length > 0 && (
                    <ul className="todojob-similar-list">
                      {kontext.map((line) => (
                        <li key={line}>{line}</li>
                      ))}
                    </ul>
                  )}
                  {questions.length > 0 && (
                    <ul className="todojob-similar-list">
                      {questions.map((question) => {
                        const answer = question.nr === undefined ? null : todojobAnswerOf(answers, question.nr)
                        return (
                          <li key={question.nr ?? question.at}>
                            <Icon name="question" /> {question.text}
                            {answer !== null && (
                              <>
                                {' '}
                                <Icon name="reply" /> {answer.text}
                              </>
                            )}
                          </li>
                        )
                      })}
                    </ul>
                  )}
                </div>
              )
            })}
          </div>
        )}

        <label className="todojob-form-field">
          <span className="form-label mb-1">{t('todojob.create.aufgabe')}</span>
          <textarea className="form-control" rows={3} value={aufgabe} onChange={(event) => setAufgabe(event.target.value)} />
        </label>

        <label className="todojob-form-field">
          <span className="form-label mb-1">{t('todojob.create.kontext')}</span>
          <textarea
            className="form-control"
            rows={3}
            value={kontextText}
            placeholder={t('todojob.create.kontextHint')}
            onChange={(event) => setKontextText(event.target.value)}
          />
        </label>

        <div className="todojob-form-row">
          <label className="todojob-form-field">
            <span className="form-label mb-1">{t('todojob.create.rueckmeldungsArt')}</span>
            <select
              className="form-select"
              value={rueckmeldungsArt}
              onChange={(event) => setRueckmeldungsArt(event.target.value as TodojobRueckmeldungEntryType)}
            >
              {TODOJOB_RUECKMELDUNGS_ARTEN.map((art) => (
                <option key={art} value={art}>
                  {t(`todojob.entryType.${art}`)}
                </option>
              ))}
            </select>
          </label>
          <label className="form-check">
            <input className="form-check-input" type="checkbox" checked={bestaetigungNoetig} onChange={(event) => setBestaetigungNoetig(event.target.checked)} />
            <span className="form-check-label">{t('todojob.create.bestaetigungNoetig')}</span>
          </label>
          <label className="form-check">
            <input className="form-check-input" type="checkbox" checked={dauerhaftRelevant} onChange={(event) => setDauerhaftRelevant(event.target.checked)} />
            <span className="form-check-label">{t('todojob.create.dauerhaftRelevant')}</span>
          </label>
          <label className="todojob-form-field">
            <span className="form-label mb-1">{t('todojob.create.frist')}</span>
            <input className="form-control" type="date" value={fristDate} onChange={(event) => setFristDate(event.target.value)} />
          </label>
        </div>

        <div className="todojob-form-field">
          <span className="form-label mb-1">{t('todojob.create.labels')}</span>
          <div className="d-flex flex-wrap gap-3">
            {(fileSubClassDestinations ?? []).map((destination) => (
              <label key={destination.key} className="form-check">
                <input
                  className="form-check-input"
                  type="checkbox"
                  checked={displayContextKeys.includes(destination.key)}
                  onChange={() => toggleDisplayContextKey(destination.key)}
                />
                <span className="form-check-label">{translate(destination.displayName, language) || destination.key}</span>
              </label>
            ))}
            {(fileSubClassDestinations ?? []).length === 0 && <span className="text-body-secondary">{t('todojob.create.noLabels')}</span>}
          </div>
        </div>

        <div className="todojob-form-field">
          <span className="form-label mb-1">{t('todojob.create.servantUsers')}</span>
          {servantUsers.length > 0 && (
            <div className="d-flex flex-wrap gap-1 mb-1">
              {servantUsers.map((users) => (
                <button
                  key={users.guid}
                  type="button"
                  className="button-other"
                  onClick={() => setServantUsers((current) => current.filter((candidate) => candidate.guid !== users.guid))}
                >
                  {usersDisplayName(users)} <Icon name="close" />
                </button>
              ))}
            </div>
          )}
          <UsersPicker
            id="todojob-servant-picker"
            excludedGuids={servantUsers.map((users) => users.guid)}
            onPick={(users) => setServantUsers((current) => [...current, users])}
          />
        </div>

        <div className="todojob-form-field">
          <span className="form-label mb-1">{t('todojob.create.servantRoles')}</span>
          <BadgeToggleList badges={accessRoles ?? []} selectedKeys={servantAccessRoleKeys} onChange={setServantAccessRoleKeys} shape="rect" />
        </div>

        <div className="todojob-form-field">
          <span className="form-label mb-1">{t('todojob.create.servantArcs')}</span>
          <BadgeToggleList badges={accessRoleCollections ?? []} selectedKeys={servantArcKeys} onChange={setServantArcKeys} shape="pill" />
        </div>

        <div className="todojob-form-field">
          <button type="button" className="button-other" onClick={() => setOwnerSideOpen(!ownerSideOpen)}>
            <Icon name={ownerSideOpen ? 'expand' : 'collapsed'} /> {t('todojob.create.ownerSide')}
          </button>
          {ownerSideOpen && (
            <div className="d-flex flex-column gap-2 mt-2">
              <span className="small text-body-secondary">{t('todojob.create.ownerSideHint')}</span>
              <BadgeToggleList badges={accessRoles ?? []} selectedKeys={ownerAccessRoleKeys} onChange={setOwnerAccessRoleKeys} shape="rect" />
              <BadgeToggleList badges={accessRoleCollections ?? []} selectedKeys={ownerArcKeys} onChange={setOwnerArcKeys} shape="pill" />
            </div>
          )}
        </div>

        <SolrHookFields hooks={hooks} onChange={setHooks} language={language} disabled={saving} title={t('todojob.create.hooks')} />
      </div>
    </Popup>
  )
}
