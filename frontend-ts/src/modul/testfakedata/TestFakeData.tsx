import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import { IntegerInput } from '../../components/IntegerInput.tsx'
import { Subheader } from '../../components/Subheader.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { showToast } from '../../components/toastStore.ts'
import {
  cancelTestFakeData,
  getTestFakeDataStatus,
  startTestFakeData,
  TESTFAKEDATA_MAX_ARTICLE_COUNT,
  TESTFAKEDATA_MAX_DOCUMENT_COUNT,
  TESTFAKEDATA_MAX_PARTNER_COUNT,
  TESTFAKEDATA_STATUS_POLL_MS,
  type TestFakeDataPhase,
  type TestFakeDataRequest,
  type TestFakeDataStatus,
} from './TestFakeData.ts'

/** Die Vorgabe der Maske: klein genug für einen ersten Lauf */
const DEFAULT_REQUEST: TestFakeDataRequest = {
  articleCount: 10_000,
  customerCount: 500,
  supplierCount: 200,
  documentCount: 20_000,
  documentsPerArticleMean: 3,
  documentsPerArticleDeviation: 1,
}

/** Die Felder der Maske in ihrer Reihenfolge, mit Obergrenze */
const FIELDS: { key: keyof TestFakeDataRequest; max: number }[] = [
  { key: 'articleCount', max: TESTFAKEDATA_MAX_ARTICLE_COUNT },
  { key: 'customerCount', max: TESTFAKEDATA_MAX_PARTNER_COUNT },
  { key: 'supplierCount', max: TESTFAKEDATA_MAX_PARTNER_COUNT },
  { key: 'documentCount', max: TESTFAKEDATA_MAX_DOCUMENT_COUNT },
  { key: 'documentsPerArticleMean', max: 10_000 },
  { key: 'documentsPerArticleDeviation', max: 10_000 },
]

/** Sekunden seit dem Start einer Phase, bis zu ihrem Ende oder bis jetzt */
function secondsOf(phase: TestFakeDataPhase): number | null {
  if (phase.startedAt === null) {
    return null
  }
  const end = phase.finishedAt !== null ? new Date(phase.finishedAt).getTime() : Date.now()
  return Math.max(0, (end - new Date(phase.startedAt).getTime()) / 1000)
}

/** 75 s, 12 min 5 s, 2 h 3 min */
function durationText(seconds: number): string {
  const whole = Math.round(seconds)
  if (whole < 60) {
    return `${whole} s`
  }
  if (whole < 3600) {
    return `${Math.floor(whole / 60)} min ${whole % 60} s`
  }
  return `${Math.floor(whole / 3600)} h ${Math.floor((whole % 3600) / 60)} min`
}

/**
 * Das Modul testfakedata (User 2026-10-14): Testdaten für die Solr-Kerne, um früh die spätere Performance
 * einzuschätzen. Oben die Maske: wie viele Artikel, Kunden, Lieferanten und Datenblätter, wie viele Datenblätter ein
 * Artikel im Mittel hat und wie stark das streut; darunter, an wie vielen Artikeln ein Datenblatt damit im Mittel hängt.
 * Unten der Stand des Laufs je Phase mit Solr-Dokumenten je Sekunde, abgefragt alle zwei Sekunden, solange er läuft.
 * Der Lauf selbst läuft im Backend; die Seite darf man verlassen und wiederkommen.
 */
export function TestFakeData() {
  const { t, i18n } = useTranslation()
  const [form, setForm] = useState<TestFakeDataRequest>(DEFAULT_REQUEST)
  const [status, setStatus] = useState<TestFakeDataStatus | null>(null)
  const [statusErrorMessage, setStatusErrorMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  /** neu abfragen: nach dem Start und nach dem Abbrechen */
  const [pollCount, setPollCount] = useState(0)

  useEffect(() => {
    let active = true
    let timer: number | undefined
    function poll() {
      getTestFakeDataStatus()
        .then((result) => {
          if (!active) {
            return
          }
          setStatus(result.data)
          setStatusErrorMessage(null)
          if (result.data.running) {
            timer = window.setTimeout(poll, TESTFAKEDATA_STATUS_POLL_MS)
          }
        })
        .catch((error: unknown) => {
          if (active) {
            setStatusErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
          }
        })
    }
    poll()
    return () => {
      active = false
      window.clearTimeout(timer)
    }
  }, [pollCount, t])

  const running = status?.running === true
  const number = (value: number, fractionDigits = 0) => value.toLocaleString(i18n.language, { maximumFractionDigits: fractionDigits })

  function start() {
    setBusy(true)
    startTestFakeData(form)
      .then((result) => {
        setStatus(result.data)
        setPollCount((count) => count + 1)
      })
      .catch((error: unknown) => {
        showToast({ kind: 'error', title: t('testfakedata.startFailed'), text: error instanceof RequestError ? error.message : undefined })
      })
      .finally(() => setBusy(false))
  }

  function cancel() {
    cancelTestFakeData()
      .then((result) => setStatus(result.data))
      .catch((error: unknown) => {
        showToast({ kind: 'error', title: t('testfakedata.cancelFailed'), text: error instanceof RequestError ? error.message : undefined })
      })
  }

  // an wie vielen Artikeln ein Datenblatt im Mittel hängt: alle Verknüpfungen durch die Datenblätter
  const articlesPerDocument = form.documentCount > 0 && form.articleCount > 0 ? (form.articleCount * form.documentsPerArticleMean) / form.documentCount : null

  const columns: TableColumn<TestFakeDataPhase>[] = [
    { header: t('testfakedata.columns.phase'), cell: (phase) => t(`testfakedata.phases.${phase.key}`), width: '9rem' },
    {
      header: t('testfakedata.columns.numbers'),
      headerHint: t('testfakedata.columns.numbersHint'),
      // ohne Grouping: Nummern liest man ohne Tausenderpunkte
      cell: (phase) => (phase.total > 0 ? `${phase.firstNumber} – ${phase.lastNumber}` : '—'),
      width: '11rem',
    },
    {
      header: t('testfakedata.columns.progress'),
      cell: (phase) => {
        const percent = phase.total > 0 ? Math.min(100, (phase.done / phase.total) * 100) : phase.finishedAt !== null ? 100 : 0
        return (
          <div className="d-flex flex-column gap-1">
            <span className="small">
              {number(phase.done)} / {number(phase.total)}
            </span>
            <div className="progress" role="progressbar" aria-valuenow={Math.round(percent)} aria-valuemin={0} aria-valuemax={100} style={{ height: '0.5rem' }}>
              <div className="progress-bar" style={{ width: `${percent}%` }} />
            </div>
          </div>
        )
      },
    },
    {
      header: t('testfakedata.columns.written'),
      headerHint: t('testfakedata.columns.writtenHint'),
      cell: (phase) => number(phase.writtenSolrDocuments),
      width: '8rem',
    },
    { header: t('testfakedata.columns.rate'), cell: (phase) => (phase.startedAt === null ? '' : t('testfakedata.perSecond', { value: number(phase.solrDocumentsPerSecond, 1) })), width: '7rem' },
    {
      header: t('testfakedata.columns.duration'),
      cell: (phase) => {
        const seconds = secondsOf(phase)
        return seconds === null ? '' : durationText(seconds)
      },
      width: '7rem',
      unimportant: true,
    },
    { header: t('testfakedata.columns.skipped'), headerHint: t('testfakedata.columns.skippedHint'), cell: (phase) => number(phase.skipped), width: '7rem', unimportant: true },
    {
      header: t('testfakedata.columns.failed'),
      // der Grund steht unter der Tabelle
      cell: (phase) => (phase.failed > 0 ? <span className="text-danger">{number(phase.failed)}</span> : '0'),
      width: '6rem',
      unimportant: true,
    },
  ]

  let statusLine
  if (status === null || status.startedAt === null) {
    statusLine = <span className="text-body-secondary">{t('testfakedata.status.never')}</span>
  } else if (running) {
    statusLine = (
      <span>
        <span className="spinner-border spinner-border-sm me-2" role="status" aria-label={t('common.loading')} />
        {status.cancelRequested ? t('testfakedata.status.cancelling') : t('testfakedata.status.running', { phase: status.currentPhase ? t(`testfakedata.phases.${status.currentPhase}`) : '' })}
      </span>
    )
  } else if (status.errorMessage !== null) {
    statusLine = <span className="text-danger">{t('testfakedata.status.failed', { message: status.errorMessage })}</span>
  } else {
    statusLine = <span>{status.cancelRequested ? t('testfakedata.status.cancelled') : t('testfakedata.status.finished')}</span>
  }

  const lastErrors = (status?.phases ?? []).filter((phase) => phase.lastErrorMessage !== null)

  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="testfakedata" /> {t('testfakedata.title')}
          </>
        }
        sticky
      />
      <div className="main-content d-flex flex-column gap-3">
        <ContentBox
          title={t('testfakedata.form.title')}
          actions={
            <button type="button" className="button-save" disabled={busy || running} onClick={start}>
              {busy ? t('testfakedata.starting') : t('testfakedata.start')}
            </button>
          }
          footer={<span>{t('testfakedata.testDataFlag')}</span>}
        >
          <p className="text-body-secondary small">{t('testfakedata.intro')}</p>
          <div className="row g-3">
            {FIELDS.map((field) => (
              <div key={field.key} className="col-12 col-md-6 col-xl-4">
                <label className="form-label" htmlFor={`testfakedata-${field.key}`}>
                  {t(`testfakedata.fields.${field.key}`)}
                </label>
                <IntegerInput
                  id={`testfakedata-${field.key}`}
                  value={form[field.key]}
                  min={0}
                  max={field.max}
                  disabled={running}
                  onChange={(value) => setForm({ ...form, [field.key]: value })}
                />
                <div className="form-text">{t(`testfakedata.hints.${field.key}`)}</div>
              </div>
            ))}
          </div>
          <div className="mt-3">
            {articlesPerDocument === null ? (
              <span className="text-body-secondary">{t('testfakedata.derivedNone')}</span>
            ) : (
              <strong>{t('testfakedata.derived', { value: number(articlesPerDocument, 2) })}</strong>
            )}
          </div>
        </ContentBox>

        <ContentBox
          title={t('testfakedata.status.title')}
          actions={
            running ? (
              <button type="button" className="button-other" disabled={status?.cancelRequested === true} onClick={cancel}>
                {t('testfakedata.cancel')}
              </button>
            ) : undefined
          }
          footer={statusErrorMessage !== null ? <span className="text-danger">{statusErrorMessage}</span> : statusLine}
        >
          {status !== null && status.startedAt !== null && (
            <p className="small text-body-secondary">
              {t('testfakedata.status.startedAt', { time: new Date(status.startedAt).toLocaleString(i18n.language) })}
              {status.finishedAt !== null && ` · ${t('testfakedata.status.finishedAt', { time: new Date(status.finishedAt).toLocaleString(i18n.language) })}`}
            </p>
          )}
          <Table
            columns={columns}
            rows={status?.phases ?? []}
            rowKey={(phase) => phase.key}
            detailTitle={(phase) => t(`testfakedata.phases.${phase.key}`)}
            emptyText={t('testfakedata.status.never')}
          />
          {lastErrors.map((phase) => (
            <div key={phase.key} className="small text-danger mt-2">
              {t(`testfakedata.phases.${phase.key}`)}: {phase.lastErrorMessage}
            </div>
          ))}
        </ContentBox>
      </div>
    </>
  )
}
