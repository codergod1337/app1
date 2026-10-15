import { useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { useLoad } from '../../api/useLoad.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { showToast } from '../../components/toastStore.ts'
import { useSession } from '../login/sessionContext.ts'
import {
  claimTodojob,
  isTodojobGroupJob,
  listTodojobs,
  TODOJOB_DESK_ORIGIN_STATE,
  todojobFormatDate,
  todojobNumberOf,
  todojobPath,
  todojobValueOf,
  todojobValuesOf,
  type TodojobDocument,
} from './Todojob.ts'
import { TodojobCreatePopup } from './TodojobCreatePopup.tsx'
import { TodojobStatusBadge } from './TodojobStatusBadge.tsx'
import { TodojobUsersName } from './TodojobUsersName.tsx'

/** Eine Zeile der Übersicht: der Job und in welcher Rolle der User ihn sieht */
interface TodojobOverviewRow {
  job: TodojobDocument
  id: string
  /** Servant: der Job ging an mich; Owner: ich habe ihn erteilt; beides ist möglich */
  servant: boolean
  owner: boolean
}

/**
 * Der Block „Jobs“ auf der Landingpage Profil (User 2026-10-16): damit man nicht vergisst, was noch zu tun ist. Oben
 * die Jobs, bei denen der Ball beim User liegt, darunter die, die bei anderen liegen; jede Zeile führt auf die
 * Detailseite des Jobs, bearbeitet wird nur dort. Erteilen über den NewEntityButton im Kopf. Ein Gruppenjob ohne Claim
 * hat den Claim direkt in der Zeile.
 */
export function TodojobOverviewBox() {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const { sessionInfo } = useSession()
  const { accessRoles, accessRoleCollections } = useMasterData()
  const [creating, setCreating] = useState(false)
  const [claimingId, setClaimingId] = useState<string | null>(null)
  // einmal beim Zeichnen, für die Fristen genügt das
  const [now] = useState(() => Date.now())

  const loadForMe = useCallback(() => listTodojobs('FOR_ME', 0), [])
  const loadFromMe = useCallback(() => listTodojobs('FROM_ME', 0), [])
  const loadDone = useCallback(() => listTodojobs('DONE', 0), [])
  const forMe = useLoad(loadForMe)
  const fromMe = useLoad(loadFromMe)
  const done = useLoad(loadDone)
  const me = sessionInfo?.usersGuid ?? ''
  const errorMessage = forMe.errorMessage ?? fromMe.errorMessage ?? done.errorMessage
  const loading = forMe.data === null || fromMe.data === null

  function reload() {
    forMe.reload()
    fromMe.reload()
    done.reload()
  }

  // ein Job, bei dem ich Owner und Servant bin, steht einmal, mit beiden Rollen
  const rowsById = new Map<string, TodojobOverviewRow>()
  for (const job of forMe.data?.documents ?? []) {
    const id = todojobValueOf(job, 'id')
    rowsById.set(id, { job, id, servant: true, owner: false })
  }
  for (const job of fromMe.data?.documents ?? []) {
    const id = todojobValueOf(job, 'id')
    const existing = rowsById.get(id)
    rowsById.set(id, existing ? { ...existing, owner: true } : { job, id, servant: false, owner: true })
  }
  const rows = [...rowsById.values()]

  /** Liegt der Ball bei mir: als Servant offen ohne fremden Claim und ohne offene Rückfrage, als Owner FERTIG oder mit Rückfrage */
  function isMyTurn(row: TodojobOverviewRow): boolean {
    const status = todojobValueOf(row.job, 'status')
    const claimGuid = todojobValueOf(row.job, 'claimGuid')
    const openQuestions = todojobNumberOf(row.job, 'offeneRueckfragen')
    if (row.owner && (status === 'FERTIG' || openQuestions > 0)) {
      return true
    }
    return row.servant && status !== 'FERTIG' && (claimGuid === '' || claimGuid === me) && openQuestions === 0
  }

  const myTurn = rows.filter(isMyTurn)
  const others = rows.filter((row) => !isMyTurn(row))

  function claim(row: TodojobOverviewRow) {
    setClaimingId(row.id)
    claimTodojob(row.id)
      .then(() => {
        showToast({ kind: 'success', title: t('todojob.job', { id: row.id }), text: t('todojob.card.claimed') })
        reload()
      })
      .catch((error: unknown) => {
        showToast({ kind: 'error', title: t('todojob.job', { id: row.id }), text: error instanceof RequestError ? error.message : t('common.unknownError') })
      })
      .finally(() => setClaimingId(null))
  }

  /** Die Gegenseite in einer Zeile: als Servant der Owner, als Owner die Servants */
  function counterpart(row: TodojobOverviewRow) {
    if (row.servant && !row.owner) {
      return (
        <>
          {t('todojob.card.owner')} <TodojobUsersName guid={todojobValueOf(row.job, 'ownerGuid')} />
        </>
      )
    }
    const names: string[] = [
      ...todojobValuesOf(row.job.servantAccessRoleKeys).map(
        (key) => translate(accessRoles?.find((accessRole) => accessRole.key === key)?.displayName ?? null) || key,
      ),
      ...todojobValuesOf(row.job.servantArcKeys).map(
        (key) => translate(accessRoleCollections?.find((arc) => arc.key === key)?.displayName ?? null) || key,
      ),
    ]
    return (
      <>
        {t('todojob.card.servant')}{' '}
        {todojobValuesOf(row.job.servantGuids).map((guid) => (
          <span key={guid} className="me-1">
            <TodojobUsersName guid={guid} />
          </span>
        ))}
        {names.join(', ')}
      </>
    )
  }

  const columns: TableColumn<TodojobOverviewRow>[] = [
    {
      header: t('todojob.overview.job'),
      cell: (row) => (
        <Link className="todojob-overview-link" to={todojobPath(row.id)} state={TODOJOB_DESK_ORIGIN_STATE}>
          <span className="todojob-card-number">#{row.id}</span> {todojobValueOf(row.job, 'verb')} {todojobValueOf(row.job, 'objekt')}
        </Link>
      ),
    },
    { header: t('todojob.overview.who'), width: '16rem', unimportant: true, cell: counterpart },
    {
      header: t('todojob.overview.status'),
      width: '13rem',
      cell: (row) => {
        const openQuestions = todojobNumberOf(row.job, 'offeneRueckfragen')
        const claimGuid = todojobValueOf(row.job, 'claimGuid')
        return (
          <span className="d-inline-flex flex-wrap align-items-center gap-1">
            <TodojobStatusBadge job={row.job} />
            {openQuestions > 0 && (
              <span className="text-danger small fw-semibold">
                <Icon name="question" /> {openQuestions}
              </span>
            )}
            {claimGuid !== '' && claimGuid !== me && (
              <span className="small">
                <Icon name="claim" /> <TodojobUsersName guid={claimGuid} />
              </span>
            )}
          </span>
        )
      },
    },
    {
      header: t('todojob.overview.frist'),
      width: '7rem',
      unimportant: true,
      cell: (row) => {
        const frist = todojobValueOf(row.job, 'fristAm')
        if (frist === '') {
          return ''
        }
        const overdue = new Date(frist).getTime() < now
        return <span className={overdue ? 'text-danger fw-semibold' : undefined}>{todojobFormatDate(frist, language)}</span>
      },
    },
  ]

  function actionsOf(row: TodojobOverviewRow) {
    const status = todojobValueOf(row.job, 'status')
    const canClaim = row.servant && status === 'OFFEN' && todojobValueOf(row.job, 'claimGuid') === '' && isTodojobGroupJob(row.job)
    if (!canClaim) {
      return null
    }
    return (
      <button type="button" className="button-other" disabled={claimingId === row.id} onClick={() => claim(row)}>
        <Icon name="claim" /> {t('todojob.card.claim')}
      </button>
    )
  }

  const openCount = rows.length
  const doneCount = done.data?.totalFound ?? 0

  return (
    <>
      <ContentBox
        title={
          <>
            {t('todojob.overview.title')} {!loading && <span className="profile-count-chip">{myTurn.length}</span>}
          </>
        }
        actions={<NewEntityButton entity="TodoJob" onClick={() => setCreating(true)} />}
        footer={
          errorMessage !== null ? (
            <span className="text-danger">{errorMessage}</span>
          ) : loading ? undefined : (
            t('todojob.overview.footer', { open: openCount, done: doneCount })
          )
        }
      >
        {loading && errorMessage === null && <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />}
        {!loading && (
          <>
            <h3 className="h6">{t('todojob.overview.myTurn')}</h3>
            <Table
              columns={columns}
              rows={myTurn}
              rowKey={(row) => row.id}
              detailTitle={(row) => `#${row.id} ${todojobValueOf(row.job, 'verb')} ${todojobValueOf(row.job, 'objekt')}`}
              emptyText={t('todojob.overview.nothingForMe')}
              actions={actionsOf}
              actionsWidth="9rem"
            />
            {others.length > 0 && (
              <>
                <h3 className="h6 mt-3">{t('todojob.overview.others')}</h3>
                <Table
                  columns={columns}
                  rows={others}
                  rowKey={(row) => row.id}
                  detailTitle={(row) => `#${row.id} ${todojobValueOf(row.job, 'verb')} ${todojobValueOf(row.job, 'objekt')}`}
                  emptyText=""
                />
              </>
            )}
          </>
        )}
      </ContentBox>
      <TodojobCreatePopup
        open={creating}
        onClose={() => setCreating(false)}
        onCreated={() => {
          setCreating(false)
          reload()
        }}
      />
    </>
  )
}
