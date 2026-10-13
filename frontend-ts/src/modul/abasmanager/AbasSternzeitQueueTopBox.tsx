import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { type AbasSternzeitQueueEntry, getAbasSternzeitQueueTop } from './AbasSternzeit.ts'

/** So oft fragt die Box nach der Queue */
const POLL_MS = 5000

interface AbasSternzeitQueueTopBoxProps {
  /** Klick auf das Schloss einer Zeile: die Kennung sperren */
  onIgnore: (sternzeit: string) => void
}

/**
 * Die vordersten 50 der Queue in FIFO-Ordnung (User 2026-10-10): was als Nächstes drankommt, mit Aktion, Quelle,
 * Zeiten, Fehlversuchen und dem letzten Fehler. Fragt alle fünf Sekunden nach, nie mit zwei Anfragen gleichzeitig.
 */
export function AbasSternzeitQueueTopBox({ onIgnore }: AbasSternzeitQueueTopBoxProps) {
  const { t, i18n } = useTranslation()
  const [entries, setEntries] = useState<AbasSternzeitQueueEntry[] | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const inFlight = useRef(false)

  function load() {
    if (inFlight.current) {
      return
    }
    inFlight.current = true
    getAbasSternzeitQueueTop()
      .then((result) => {
        setEntries(result.data)
        setErrorMessage(null)
      })
      .catch((error: unknown) =>
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError')),
      )
      .finally(() => {
        inFlight.current = false
      })
  }

  useEffect(() => {
    load()
    const interval = setInterval(load, POLL_MS)
    return () => clearInterval(interval)
    // beim Betreten, dann im Takt
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function formatInstant(value: string | null): string {
    if (value === null) {
      return ''
    }
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString(i18n.language)
  }

  const columns: TableColumn<AbasSternzeitQueueEntry>[] = [
    { header: t('abasmanager.queue.columns.sternzeit'), cell: (row) => <strong>{row.sternzeit}</strong> },
    {
      header: t('abasmanager.queue.columns.action'),
      cell: (row) => (
        <span className={`badge ${row.action === 'DELETE' ? 'text-bg-danger' : 'text-bg-primary'}`}>
          {row.action === 'DELETE' ? t('abasmanager.queue.action.delete') : t('abasmanager.queue.action.fetch')}
        </span>
      ),
      width: '6rem',
    },
    {
      header: t('abasmanager.queue.columns.source'),
      cell: (row) =>
        row.source === 'ABAS'
          ? t('abasmanager.queue.source.abas')
          : row.source === 'IMPORT'
            ? t('abasmanager.queue.source.import')
            : t('abasmanager.queue.source.admin'),
      unimportant: true,
      width: '6rem',
    },
    { header: t('abasmanager.queue.columns.queuedAt'), cell: (row) => formatInstant(row.queuedAt), unimportant: true },
    {
      header: t('abasmanager.queue.columns.claimedAt'),
      cell: (row) =>
        row.claimedAt === null ? (
          <span className="text-body-secondary">{t('abasmanager.queue.waiting')}</span>
        ) : (
          formatInstant(row.claimedAt)
        ),
      unimportant: true,
    },
    {
      header: t('abasmanager.queue.columns.attempts'),
      cell: (row) => (row.attempts > 0 ? <span className="badge text-bg-danger">{row.attempts}</span> : row.attempts),
      width: '6rem',
    },
    {
      header: t('abasmanager.queue.columns.lastError'),
      cell: (row) => row.lastErrorMessage ?? '',
      unimportant: true,
    },
  ]

  function rowActions(row: AbasSternzeitQueueEntry) {
    return (
      <Hoverlay text={t('abasmanager.overview.ignoreRow', { sternzeit: row.sternzeit })}>
        <button
          type="button"
          className="button-other"
          aria-label={t('abasmanager.overview.ignoreRow', { sternzeit: row.sternzeit })}
          onClick={() => onIgnore(row.sternzeit)}
        >
          <Icon name="locked" />
        </button>
      </Hoverlay>
    )
  }

  let footer
  if (errorMessage !== null) {
    footer = <span className="text-danger">{errorMessage}</span>
  } else if (entries === null) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else {
    footer = <span>{t('abasmanager.queue.footer')}</span>
  }

  return (
    <ContentBox title={t('abasmanager.queue.title')} footer={footer}>
      {entries !== null && (
        <Table
          columns={columns}
          rows={entries}
          rowKey={(row) => row.sternzeit}
          actions={rowActions}
          actionsWidth="4rem"
          detailTitle={(row) => row.sternzeit}
          emptyText={t('abasmanager.queue.empty')}
        />
      )}
    </ContentBox>
  )
}
