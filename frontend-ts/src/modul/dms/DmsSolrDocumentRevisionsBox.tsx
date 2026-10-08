import { useEffect, useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { Popup } from '../../components/Popup.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { showToast } from '../../components/toastStore.ts'
import { solrFieldDisplayNameOf } from '../solrmanager/SolrField.ts'
import {
  DMS_CORE_KEY,
  getDmsSolrDocumentRevision,
  getDmsSolrDocumentRevisions,
  restoreDmsSolrDocumentRevision,
  type DmsSolrDocumentRevision,
} from './Dms.ts'
import { dmsRevisionDelta, type DmsRevisionDeltaRow } from './dmsRevisionDelta.ts'

interface DmsSolrDocumentRevisionsBoxProps {
  id: string
  /** der aktuelle Stand, wie die Seite ihn geladen hat: der Stand nach der jüngsten Revision */
  document: Record<string, unknown>
  /** nach dem Zurückladen: das Dokument neu laden, denn alles auf der Seite ist danach alt */
  onRestored: () => void
}

/**
 * Die Revisionen eines DMS-Dokuments mit Rückwärtsgang (nach der DmsHistoryBox der Vorlage, gebaut 2026-10-08,
 * Zuschnitt User 2026-10-08): eine eigene ContentBox unter den Details, nur wenn es Revisionen gibt. Ein nie
 * geändertes Dokument trägt Revision 0 und hat keine, das ist die Wahrheit, keine leere Box.
 *
 * Je Revision ein Aufklapper wie bei Volltext und Zugriffsrechten: Nummer, Zeitpunkt, wer, die Felder, deren Wert sich
 * wirklich geändert hat. Erst das Aufklappen holt die Stände vom Backend (das entpackt die ZIPs) und zeigt nur
 * Feld | vorher | nachher, die Änderung dieser Zeile (Stand N → Stand N+1, dafür braucht es das ZIP N+1, bei der
 * jüngsten Revision den Iststand). Felder stehen mit ihrem Anzeigenamen. Zurückladen nur nach Sicherheitsabfrage im
 * Popup und nur, wenn es etwas ändert, danach lädt die Seite das Dokument neu.
 */
export function DmsSolrDocumentRevisionsBox({ id, document, onRestored }: DmsSolrDocumentRevisionsBoxProps) {
  const { t, i18n } = useTranslation()
  const { language } = useLanguage()
  const { solrHooks, solrFields } = useMasterData()
  const [revisions, setRevisions] = useState<DmsSolrDocumentRevision[] | null>(null)
  const [listError, setListError] = useState<string | null>(null)
  /** revisionNo → das Dokument aus dem ZIP, erst beim Aufklappen geholt */
  const [states, setStates] = useState<Record<number, Record<string, unknown>>>({})
  const [open, setOpen] = useState<number | null>(null)
  const [loadingStates, setLoadingStates] = useState(false)
  /** die Revision in der Sicherheitsabfrage */
  const [restoring, setRestoring] = useState<number | null>(null)
  const [restoreRunning, setRestoreRunning] = useState(false)
  const [restoreError, setRestoreError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setListError(null)
    getDmsSolrDocumentRevisions(id)
      .then(({ data }) => {
        if (!cancelled) {
          setRevisions(data)
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setRevisions([])
          setListError(error instanceof RequestError ? error.message : t('common.unknownError'))
        }
      })
    return () => {
      cancelled = true
    }
    // neu laden, wenn die Seite ein anderes oder ein neu geladenes Dokument zeigt
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, document])

  function labelOf(fieldName: string): string {
    const hook = solrHooks?.find((candidate) => candidate.key === fieldName)
    if (hook) {
      return translate(hook.displayName, language) || hook.key
    }
    return solrFieldDisplayNameOf(solrFields ?? [], DMS_CORE_KEY, fieldName, language) ?? fieldName
  }

  function dateTime(value: string): string {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString(i18n.language)
  }

  function revisionOf(revisionNo: number): DmsSolrDocumentRevision | undefined {
    return revisions?.find((candidate) => candidate.revisionNo === revisionNo)
  }

  /** Aufklappen an oder aus: holt ZIP N und, wenn es sie gibt, ZIP N+1, einmal geholt bleibt es in der Ablage */
  function toggle(revisionNo: number) {
    if (open === revisionNo) {
      setOpen(null)
      return
    }
    setOpen(revisionNo)
    const needed = revisionOf(revisionNo + 1) ? [revisionNo, revisionNo + 1] : [revisionNo]
    const missing = needed.filter((number) => !(number in states))
    if (missing.length === 0) {
      return
    }
    setLoadingStates(true)
    Promise.all(
      missing.map((number) =>
        getDmsSolrDocumentRevision(id, number).then(({ data }) =>
          setStates((previous) => ({ ...previous, [number]: data })),
        ),
      ),
    )
      .catch((error: unknown) => {
        // ohne einen der beiden Stände bleibt die Zeile zu, eine halbe Ansicht sähe aus wie eine ganze
        showToast({
          kind: 'error',
          title: t('dms.revisions.loadFailed'),
          text: error instanceof RequestError ? error.message : t('common.unknownError'),
        })
        setOpen(null)
      })
      .finally(() => setLoadingStates(false))
  }

  /** Die beiden Stände einer Zeile: das ZIP N und das ZIP N+1, bei der jüngsten Revision der Iststand */
  function statesOf(revision: DmsSolrDocumentRevision) {
    const before = states[revision.revisionNo]
    const next = revisionOf(revision.revisionNo + 1)
    const after = next ? states[next.revisionNo] : document
    if (before === undefined || after === undefined) {
      return null
    }
    return { before, after }
  }

  function closeRestore() {
    setRestoreError(null)
    setRestoring(null)
  }

  function confirmRestore() {
    if (restoring === null) {
      return
    }
    const revisionNo = restoring
    setRestoreRunning(true)
    setRestoreError(null)
    restoreDmsSolrDocumentRevision(id, revisionNo)
      .then(() => {
        showToast({ kind: 'success', title: t('dms.revisions.restored', { revisionNo }) })
        closeRestore()
        // alles auf der Seite hängt am Dokument, die Ablage der Stände ist danach alt
        setStates({})
        setOpen(null)
        onRestored()
      })
      .catch((error: unknown) =>
        setRestoreError(error instanceof RequestError ? error.message : t('common.unknownError')),
      )
      .finally(() => setRestoreRunning(false))
  }

  if (revisions === null || (revisions.length === 0 && listError === null)) {
    return null
  }

  const deltaColumns: TableColumn<DmsRevisionDeltaRow>[] = [
    { header: t('dms.revisions.delta.field'), cell: (row) => labelOf(row.fieldName), width: '12rem' },
    { header: t('dms.revisions.delta.before'), cell: (row) => <span className="text-break">{row.before}</span> },
    { header: t('dms.revisions.delta.after'), cell: (row) => <span className="text-break">{row.after}</span> },
  ]

  const restoreFooter = (
    <>
      {restoreError !== null && <span className="text-danger">{restoreError}</span>}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={restoreRunning} onClick={closeRestore}>
          {t('common.cancel')}
        </button>
        <button type="button" className="button-save" disabled={restoreRunning} onClick={confirmRestore}>
          {t('dms.revisions.restore')}
        </button>
      </span>
    </>
  )

  return (
    <>
      <ContentBox title={t('dms.revisions.title', { count: revisions.length })}>
        <div className="d-flex flex-column gap-2">
          {listError !== null && <p className="text-danger mb-0">{listError}</p>}
          {revisions.map((revision) => {
            const isOpen = open === revision.revisionNo
            const pair = isOpen ? statesOf(revision) : null
            const delta = pair === null ? null : dmsRevisionDelta(pair.before, pair.after, document)
            return (
              <div key={revision.revisionNo}>
                <button
                  type="button"
                  className="dms-collapsible"
                  aria-expanded={isOpen}
                  onClick={() => toggle(revision.revisionNo)}
                >
                  <Icon name={isOpen ? 'expand' : 'collapsed'} />
                  {t('dms.revisions.revision', { revisionNo: revision.revisionNo })}
                  {' · '}
                  {dateTime(revision.changedAt)}
                  {' · '}
                  {revision.changedByUsersDisplayName}
                  {revision.changedFieldNames.length > 0 && (
                    <span className="text-muted"> · {revision.changedFieldNames.map(labelOf).join(', ')}</span>
                  )}
                </button>

                {isOpen && (loadingStates || delta === null) && (
                  <div className="ms-4 mt-1">
                    <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
                  </div>
                )}

                {isOpen && !loadingStates && delta !== null && (
                  <div className="ms-4 mt-1 d-flex flex-column gap-2">
                    <Table
                      columns={deltaColumns}
                      rows={delta.rows}
                      rowKey={(row) => row.fieldName}
                      detailTitle={(row) => labelOf(row.fieldName)}
                      emptyText={t('dms.revisions.noDifference')}
                    />
                    <div>
                      <button
                        type="button"
                        className="button-save"
                        disabled={!delta.restoreChangesSomething}
                        onClick={() => setRestoring(revision.revisionNo)}
                      >
                        {t('dms.revisions.restore')}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      </ContentBox>

      <Popup open={restoring !== null} title={t('dms.revisions.restoreTitle')} footer={restoreFooter} onClose={closeRestore}>
        <p className="mb-0">
          <Trans
            i18nKey="dms.revisions.restoreQuestion"
            values={{ revisionNo: restoring ?? 0 }}
            components={{ strong: <strong /> }}
          />
        </p>
      </Popup>
    </>
  )
}
