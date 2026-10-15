import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { CancelButton } from '../../components/CancelButton.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { Icon } from '../../components/Icon.tsx'
import { IntegerInput } from '../../components/IntegerInput.tsx'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import { Popup } from '../../components/Popup.tsx'
import { Subheader } from '../../components/Subheader.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import {
  type BlockClientIpRequest,
  blockClientIp,
  type BlockedClientIp,
  getBlockedClientIps,
  getLoggedClientRequests,
  liftClientIpBlock,
  type LoggedClientRequest,
  REQUESTMONITOR_DEFAULT_BLOCK_MINUTES,
  REQUESTMONITOR_POLL_MS,
} from './RequestMonitor.ts'

const BLOCK_FORM_ID = 'requestmonitor-block-form'

/** Höchstens ein Jahr, als Minuten */
const MAX_BLOCK_MINUTES = 525_600

const EMPTY_BLOCK_REQUEST: BlockClientIpRequest = { clientIp: '', durationMinutes: REQUESTMONITOR_DEFAULT_BLOCK_MINUTES, reason: '' }

/** Teilstring über die IP, Groß- und Kleinschreibung egal (IPv6 hat Buchstaben) */
function matchesIpFilter(clientIp: string, filter: string): boolean {
  return filter.trim() === '' || clientIp.toLowerCase().includes(filter.trim().toLowerCase())
}

/**
 * Das Modul Anfragenmonitor (User 2026-10-15): oben die Sperrliste der IPs mit Ablauf, Sperre aussprechen (Popup) und
 * aufheben (Mülleimer mit Sicherheitsabfrage); darunter die Map der Anfragen, die neueste zuerst. Beide Listen holen
 * alle zwei Sekunden den Stand, nie mit zwei Anfragen gleichzeitig, und lassen sich nach IP filtern.
 *
 * Offen, nicht vergessen (User 2026-10-15, Details in ziele.md und RequestMonitorService.java): weitere Limits aus der
 * application.yml mit automatischer Sperre, die Endpunkt-History (Map als Eingangskorb oder asynchron nach Postgres,
 * noch zu besprechen) und die eigene History je User mit Suche darin (letzte Suchen, Artikelmappen, Dokumente).
 */
export function RequestMonitor() {
  const { t, i18n } = useTranslation()
  const [loggedClientRequests, setLoggedClientRequests] = useState<LoggedClientRequest[] | null>(null)
  const [blockedClientIps, setBlockedClientIps] = useState<BlockedClientIp[] | null>(null)
  const [loadedAt, setLoadedAt] = useState<Date | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [requestsFilter, setRequestsFilter] = useState('')
  const [blockedFilter, setBlockedFilter] = useState('')
  // das Popup zum Sperren: null = zu
  const [blockRequest, setBlockRequest] = useState<BlockClientIpRequest | null>(null)
  const [blocking, setBlocking] = useState(false)
  const [blockErrorMessage, setBlockErrorMessage] = useState<string | null>(null)
  const [blockToLift, setBlockToLift] = useState<BlockedClientIp | null>(null)
  const inFlight = useRef(false)

  function load() {
    if (inFlight.current) {
      return
    }
    inFlight.current = true
    Promise.all([getLoggedClientRequests(), getBlockedClientIps()])
      .then(([requestsResult, blockedResult]) => {
        setLoggedClientRequests(requestsResult.data)
        setBlockedClientIps(blockedResult.data)
        setLoadedAt(new Date())
        setErrorMessage(null)
      })
      .catch((error: unknown) => setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError')))
      .finally(() => {
        inFlight.current = false
      })
  }

  useEffect(() => {
    load()
    const interval = setInterval(load, REQUESTMONITOR_POLL_MS)
    return () => clearInterval(interval)
    // beim Betreten, dann im Takt
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function formatInstant(value: string): string {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString(i18n.language)
  }

  function openBlockPopup(clientIp = '') {
    setBlockErrorMessage(null)
    setBlockRequest({ ...EMPTY_BLOCK_REQUEST, clientIp })
  }

  function closeBlockPopup() {
    setBlockRequest(null)
    setBlockErrorMessage(null)
  }

  function submitBlock(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (blockRequest === null) {
      return
    }
    setBlocking(true)
    setBlockErrorMessage(null)
    blockClientIp({ ...blockRequest, clientIp: blockRequest.clientIp.trim(), reason: blockRequest.reason.trim() })
      .then(() => {
        closeBlockPopup()
        load()
      })
      .catch((error: unknown) => setBlockErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError')))
      .finally(() => setBlocking(false))
  }

  const now = Date.now()

  const blockedColumns: TableColumn<BlockedClientIp>[] = [
    { header: t('requestmonitor.blocked.columns.clientIp'), cell: (row) => <code>{row.clientIp}</code>, width: '14rem' },
    { header: t('requestmonitor.blocked.columns.blockedAt'), cell: (row) => formatInstant(row.blockedAt), width: '12rem', unimportant: true },
    {
      header: t('requestmonitor.blocked.columns.expiresAt'),
      cell: (row) =>
        new Date(row.expiresAt).getTime() <= now ? (
          <span className="text-body-secondary">{t('requestmonitor.blocked.expired')}</span>
        ) : (
          formatInstant(row.expiresAt)
        ),
      width: '12rem',
    },
    { header: t('requestmonitor.blocked.columns.reason'), cell: (row) => row.reason, unimportant: true },
    {
      header: t('requestmonitor.blocked.columns.rejectedRequests'),
      headerHint: t('requestmonitor.blocked.columns.rejectedRequestsHint'),
      cell: (row) => (row.rejectedRequests > 0 ? <span className="badge text-bg-danger">{row.rejectedRequests.toLocaleString(i18n.language)}</span> : '0'),
      width: '7rem',
    },
  ]

  const requestColumns: TableColumn<LoggedClientRequest>[] = [
    { header: t('requestmonitor.columns.receivedAt'), cell: (row) => formatInstant(row.receivedAt), width: '12rem' },
    { header: t('requestmonitor.columns.clientIp'), cell: (row) => <code>{row.clientIp}</code>, width: '14rem' },
    { header: t('requestmonitor.columns.endpoint'), cell: (row) => <code>{row.endpoint}</code> },
    {
      header: t('requestmonitor.columns.blocked'),
      cell: (row) => (row.blocked ? <span className="badge text-bg-danger">{t('requestmonitor.blockedBadge')}</span> : ''),
      width: '7rem',
    },
  ]

  const shownBlocked = (blockedClientIps ?? []).filter((row) => matchesIpFilter(row.clientIp, blockedFilter))
  const shownRequests = (loggedClientRequests ?? []).filter((row) => matchesIpFilter(row.clientIp, requestsFilter))

  function blockedRowActions(row: BlockedClientIp) {
    return <DeleteButton label={t('requestmonitor.blocked.liftLabel', { clientIp: row.clientIp })} onClick={() => setBlockToLift(row)} />
  }

  function requestRowActions(row: LoggedClientRequest) {
    return (
      <button type="button" className="button-other" aria-label={t('requestmonitor.blockThisIp', { clientIp: row.clientIp })} onClick={() => openBlockPopup(row.clientIp)}>
        <Icon name="locked" />
      </button>
    )
  }

  let footer
  if (errorMessage !== null) {
    footer = <span className="text-danger">{errorMessage}</span>
  } else if (loggedClientRequests === null || loadedAt === null) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else {
    footer = <span>{t('requestmonitor.footer', { count: loggedClientRequests.length, time: loadedAt.toLocaleTimeString(i18n.language) })}</span>
  }

  const blockedFooter = blockedClientIps === null ? <span /> : <span>{t('requestmonitor.blocked.footer', { count: blockedClientIps.length })}</span>

  const blockPopupFooter = (
    <>
      {blockErrorMessage !== null && <span className="text-danger">{blockErrorMessage}</span>}
      <span className="ms-auto d-flex gap-2">
        <CancelButton disabled={blocking} onClick={closeBlockPopup} />
        <button type="submit" form={BLOCK_FORM_ID} className="button-save" disabled={blocking || blockRequest?.clientIp.trim() === ''}>
          {blocking ? t('requestmonitor.blocked.blocking') : t('requestmonitor.blocked.block')}
        </button>
      </span>
    </>
  )

  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="requestmonitor" /> {t('requestmonitor.title')}
          </>
        }
        sticky
      />
      <div className="main-content d-flex flex-column gap-3">
        <ContentBox title={t('requestmonitor.blocked.title')} actions={<NewEntityButton entity="BlockedClientIp" onClick={() => openBlockPopup()} />} footer={blockedFooter}>
          <p className="text-body-secondary small">{t('requestmonitor.blocked.intro')}</p>
          <input
            className="form-control mb-3"
            placeholder={t('requestmonitor.filterPlaceholder')}
            aria-label={t('requestmonitor.filterPlaceholder')}
            value={blockedFilter}
            onChange={(event) => setBlockedFilter(event.target.value)}
          />
          {blockedClientIps !== null && (
            <Table
              columns={blockedColumns}
              rows={shownBlocked}
              rowKey={(row) => row.clientIp}
              actions={blockedRowActions}
              actionsWidth="4rem"
              detailTitle={(row) => row.clientIp}
              emptyText={t('requestmonitor.blocked.empty')}
            />
          )}
        </ContentBox>

        <ContentBox title={t('requestmonitor.box.title')} footer={footer}>
          <p className="text-body-secondary small">{t('requestmonitor.intro')}</p>
          <input
            className="form-control mb-3"
            placeholder={t('requestmonitor.filterPlaceholder')}
            aria-label={t('requestmonitor.filterPlaceholder')}
            value={requestsFilter}
            onChange={(event) => setRequestsFilter(event.target.value)}
          />
          {loggedClientRequests !== null && (
            <Table
              columns={requestColumns}
              rows={shownRequests}
              rowKey={(row) => String(row.id)}
              actions={requestRowActions}
              actionsWidth="4rem"
              detailTitle={(row) => row.clientIp}
              emptyText={t('requestmonitor.empty')}
            />
          )}
        </ContentBox>
      </div>

      <Popup open={blockRequest !== null} title={t('requestmonitor.blocked.newTitle')} footer={blockPopupFooter} onClose={closeBlockPopup}>
        {blockRequest !== null && (
          <form id={BLOCK_FORM_ID} onSubmit={submitBlock}>
            <fieldset className="row g-3" disabled={blocking}>
              <div className="col-12">
                <label className="form-label" htmlFor={`${BLOCK_FORM_ID}-clientIp`}>
                  {t('requestmonitor.blocked.form.clientIp')}
                </label>
                <input
                  id={`${BLOCK_FORM_ID}-clientIp`}
                  className="form-control"
                  required
                  maxLength={45}
                  value={blockRequest.clientIp}
                  onChange={(event) => setBlockRequest({ ...blockRequest, clientIp: event.target.value })}
                />
              </div>
              <div className="col-12">
                <label className="form-label" htmlFor={`${BLOCK_FORM_ID}-durationMinutes`}>
                  {t('requestmonitor.blocked.form.durationMinutes')}
                </label>
                <IntegerInput
                  id={`${BLOCK_FORM_ID}-durationMinutes`}
                  value={blockRequest.durationMinutes}
                  min={1}
                  max={MAX_BLOCK_MINUTES}
                  onChange={(value) => setBlockRequest({ ...blockRequest, durationMinutes: value })}
                />
                <div className="form-text">
                  {t('requestmonitor.blocked.form.expiresHint', {
                    time: new Date(Date.now() + blockRequest.durationMinutes * 60_000).toLocaleString(i18n.language),
                  })}
                </div>
              </div>
              <div className="col-12">
                <label className="form-label" htmlFor={`${BLOCK_FORM_ID}-reason`}>
                  {t('requestmonitor.blocked.form.reason')}
                </label>
                <input
                  id={`${BLOCK_FORM_ID}-reason`}
                  className="form-control"
                  maxLength={200}
                  value={blockRequest.reason}
                  onChange={(event) => setBlockRequest({ ...blockRequest, reason: event.target.value })}
                />
              </div>
            </fieldset>
          </form>
        )}
      </Popup>

      <DeletePopup
        open={blockToLift !== null}
        title={t('requestmonitor.blocked.liftTitle')}
        onConfirm={() => liftClientIpBlock(blockToLift?.clientIp ?? '')}
        onDeleted={load}
        onClose={() => setBlockToLift(null)}
      >
        {t('requestmonitor.blocked.liftQuestion', { clientIp: blockToLift?.clientIp ?? '' })}
      </DeletePopup>
    </>
  )
}
