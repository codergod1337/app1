import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useOutletContext } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { getMailHeaders, type GraphMailFolder, type GraphMailHeader } from '../mailbox/Mailbox.ts'
import { nachrichtPath } from '../nachrichten/Nachrichten.ts'
import { ARCHIVIEREN_PATH, type ArchivierenMailbox, type ArchivierenOutletContext } from './Archivieren.ts'
import type { ArchivierenMailState } from './ArchivierenMailPage.tsx'
import { getArchivedNachrichtenIds } from './MessageArchive.ts'

/** Beim Öffnen einer Mail mitgeben, damit der header „zurück zu Postfächer“ anbietet */
const ORIGIN = { path: `${ARCHIVIEREN_PATH}/mailboxes`, label: 'archivieren.tabs.mailboxes' }

/** So viele Mails fragt die Liste auf einmal, ob sie archiviert sind; das Backend nimmt höchstens 200 */
const ARCHIVED_LOOKUP_CHUNK = 200

/** Eine Zeile der Liste: die Mail und das Postfach, aus dem sie kam */
interface ArchivierenMailRow extends GraphMailHeader {
  mailbox: ArchivierenMailbox
}

/** Der Zeitpunkt, nach dem sortiert wird: im Posteingang der Empfang, bei den gesendeten das Senden */
function timeOf(row: GraphMailHeader, folder: GraphMailFolder): number {
  const value = folder === 'SENT_ITEMS' ? (row.sentDateTime ?? row.receivedDateTime) : (row.receivedDateTime ?? row.sentDateTime)
  return value ? new Date(value).getTime() : 0
}

/**
 * Reiter „Postfächer“ (User 2026-10-14, nach der ArchivierungSeite der Vorlage): die neuesten Mails aller
 * eingeschalteten Postfächer im gewählten Ordner in einer Tabelle, neueste zuerst, so wie das Backend sie über Graph
 * liest. Gespeichert wird nichts. Die Maus auf dem Betreff zeigt den Vorschautext, den die Liste schon mitbringt, ein
 * Klick öffnet die Mail (ArchivierenMailPage), Postfach und id als Router-State.
 * Ein Postfach, das nicht antwortet, steht im Fuß, die anderen bleiben sichtbar. Liegt eine Mail schon im Archiv
 * (gleiche internetMessageId), steht in der Spalte Archiv die Nummer der Nachricht als Link auf ihre Seite.
 */
export function ArchivierenMailboxTab() {
  const { t, i18n } = useTranslation()
  const { mailboxes, activeMailboxes, folder, mailboxesErrorMessage } = useOutletContext<ArchivierenOutletContext>()
  /** null, solange geladen wird */
  const [rows, setRows] = useState<ArchivierenMailRow[] | null>(null)
  const [mailboxErrors, setMailboxErrors] = useState<string[]>([])
  const [reloadCount, setReloadCount] = useState(0)
  /** internetMessageId -> laufende Nummer der Nachricht, für die Mails, die schon im Archiv liegen */
  const [archivedIds, setArchivedIds] = useState<Record<string, string>>({})
  const [archivedErrorMessage, setArchivedErrorMessage] = useState<string | null>(null)
  // die eingeschalteten Postfächer als ein Wert: Der Effekt läuft nur, wenn sich die Auswahl wirklich ändert
  const activeMailboxKey = activeMailboxes.map((mailbox) => mailbox.emailAddress).join(',')

  useEffect(() => {
    if (mailboxes === null) {
      return
    }
    let active = true
    setRows(null)
    setMailboxErrors([])
    setArchivedIds({})
    setArchivedErrorMessage(null)
    Promise.allSettled(
      activeMailboxes.map((mailbox) =>
        getMailHeaders(mailbox.emailAddress, folder).then((result) => result.data.map((header) => ({ ...header, mailbox }))),
      ),
    ).then((results) => {
      if (!active) {
        return
      }
      const loaded: ArchivierenMailRow[] = []
      const failed: string[] = []
      results.forEach((result, index) => {
        if (result.status === 'fulfilled') {
          loaded.push(...result.value)
        } else {
          const message = result.reason instanceof RequestError ? result.reason.message : t('common.unknownError')
          failed.push(`${activeMailboxes[index].label}: ${message}`)
        }
      })
      loaded.sort((a, b) => timeOf(b, folder) - timeOf(a, folder))
      setRows(loaded)
      setMailboxErrors(failed)
      // danach, ohne die Liste aufzuhalten: welche schon im Archiv liegen
      const internetMessageIds = [...new Set(loaded.map((row) => row.internetMessageId).filter((id): id is string => id !== null))]
      const chunks: string[][] = []
      for (let start = 0; start < internetMessageIds.length; start += ARCHIVED_LOOKUP_CHUNK) {
        chunks.push(internetMessageIds.slice(start, start + ARCHIVED_LOOKUP_CHUNK))
      }
      Promise.all(chunks.map((chunk) => getArchivedNachrichtenIds(chunk)))
        .then((answers) => {
          if (active) {
            setArchivedIds(Object.assign({}, ...answers.map((answer) => answer.data)) as Record<string, string>)
          }
        })
        .catch((error: unknown) => {
          if (active) {
            setArchivedErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
          }
        })
    })
    return () => {
      active = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mailboxes === null, activeMailboxKey, folder, reloadCount])

  function formatTime(value: string | null): string {
    return value ? new Date(value).toLocaleString(i18n.language) : ''
  }

  const sent = folder === 'SENT_ITEMS'
  const columns: TableColumn<ArchivierenMailRow>[] = [
    {
      header: sent ? t('archivieren.columns.sentAt') : t('archivieren.columns.receivedAt'),
      cell: (row) => formatTime(sent ? row.sentDateTime : row.receivedDateTime),
      width: '10rem',
    },
    { header: t('archivieren.columns.mailbox'), cell: (row) => row.mailbox.label, width: '11rem', unimportant: true },
    sent
      ? { header: t('archivieren.columns.to'), cell: (row) => row.toRecipientAddresses.join(', '), width: '16rem' }
      : {
          header: t('archivieren.columns.from'),
          cell: (row) => (
            <Hoverlay text={row.fromAddress}>
              <span>{row.fromName ?? row.fromAddress}</span>
            </Hoverlay>
          ),
          width: '16rem',
        },
    {
      header: t('archivieren.columns.subject'),
      cell: (row) => {
        const state: ArchivierenMailState = { origin: ORIGIN, mailbox: row.mailbox.emailAddress, folder, exchangeMailId: row.exchangeMailId }
        return (
          <Hoverlay text={row.bodyPreview || null}>
            <Link to={`${ARCHIVIEREN_PATH}/mail`} state={state}>
              {row.subject || t('archivieren.noSubject')}
            </Link>
          </Hoverlay>
        )
      },
    },
    {
      header: t('archivieren.columns.attachments'),
      cell: (row) => (row.hasAttachments ? <Icon name="attachment" /> : null),
      width: '3rem',
      verticalHeader: true,
    },
    {
      header: t('archivieren.columns.archived'),
      cell: (row) => {
        const nachrichtId = row.internetMessageId === null ? undefined : archivedIds[row.internetMessageId]
        return nachrichtId === undefined ? null : (
          <Hoverlay text={t('archivieren.archivedHint', { id: nachrichtId })}>
            <Link to={nachrichtPath(nachrichtId)} state={{ origin: ORIGIN }} className="badge text-bg-success text-decoration-none">
              {nachrichtId}
            </Link>
          </Hoverlay>
        )
      },
      width: '5rem',
    },
  ]

  let content
  if (mailboxesErrorMessage !== null) {
    content = <span className="text-danger">{mailboxesErrorMessage}</span>
  } else if (mailboxes !== null && mailboxes.length === 0) {
    content = <span className="text-body-secondary">{t('archivieren.noMailboxes')}</span>
  } else if (mailboxes !== null && activeMailboxes.length === 0) {
    content = <span className="text-body-secondary">{t('archivieren.noActiveMailbox')}</span>
  } else if (rows !== null) {
    content = (
      <Table
        columns={columns}
        rows={rows}
        rowKey={(row) => `${row.mailbox.emailAddress} ${row.exchangeMailId}`}
        detailTitle={(row) => row.subject || t('archivieren.noSubject')}
        emptyText={t('archivieren.empty')}
      />
    )
  }

  const footer = (
    <>
      {(mailboxes === null || (rows === null && activeMailboxes.length > 0)) && mailboxesErrorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
      )}
      {rows !== null && <span>{t('archivieren.count', { count: rows.length })}</span>}
      {mailboxErrors.map((error) => (
        <span key={error} className="text-danger">
          {error}
        </span>
      ))}
      {archivedErrorMessage !== null && <span className="text-danger">{t('archivieren.archivedFailed', { message: archivedErrorMessage })}</span>}
    </>
  )

  return (
    <ContentBox
      title={t(`archivieren.folders.${folder}`)}
      actions={
        <Hoverlay text={t('archivieren.reload')}>
          <button type="button" className="button-other" aria-label={t('archivieren.reload')} disabled={rows === null} onClick={() => setReloadCount((count) => count + 1)}>
            <Icon name="refresh" />
          </button>
        </Hoverlay>
      }
      footer={footer}
    >
      {content}
    </ContentBox>
  )
}
