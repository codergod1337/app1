import { useEffect, useState, type FormEvent } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { showToast } from '../../components/toastStore.ts'
import { ViewButton } from '../../components/ViewButton.tsx'
import {
  type AbasSternzeit,
  type AbasSternzeitOverviewCounts,
  type AbasSternzeitQueueAction,
  enqueueAbasSternzeit,
  findAbasSternzeit,
  getAbasSternzeitOverviewCounts,
  getAbasSternzeitPage,
  isValidSternzeit,
  STERNZEIT_MAX_LENGTH,
} from './AbasSternzeit.ts'
import { AbasIgnoreSternzeitPopup } from './AbasIgnoreSternzeitPopup.tsx'
import { AbasSternzeitFetcherStatusBox } from './AbasSternzeitFetcherStatusBox.tsx'
import { AbasSternzeitQueueTopBox } from './AbasSternzeitQueueTopBox.tsx'
import { AbasSternzeitResponsePopup } from './AbasSternzeitResponsePopup.tsx'
import { AbasSternzeitTransferBox } from './AbasSternzeitTransferBox.tsx'

/**
 * Der Tab „Übersicht“ des AbasManagers, sechs Boxen: ganz oben die große Badge des Queue-Fetchers, dann der Bestand als
 * Kopfzahlen, Export und Import der Sternzeiten, eine Kennung von Hand einreihen oder sperren, die vordersten 50 der
 * Queue, und die Liste der Stammsätze. Die Liste blättert per Keyset über den einzigen Index, also aufsteigend nach Kennung, 100 je
 * Seite; dazu der exakte Treffer per Kennung. Je Zeile: holen, die rohe Antwort von ABAS ansehen, zum Löschen
 * einreihen. Bewusst kein Typfilter: Ohne Index auf object_type_id wäre das bei 10^9 Zeilen ein Tabellenscan.
 */
export function AbasSternzeitOverviewTab() {
  const { t, i18n } = useTranslation()

  // ----- Bestand -----
  const [counts, setCounts] = useState<AbasSternzeitOverviewCounts | null>(null)
  const [countsError, setCountsError] = useState<string | null>(null)

  // ----- Einreihen von Hand -----
  const [sternzeitInput, setSternzeitInput] = useState('')
  const [enqueueing, setEnqueueing] = useState(false)

  // ----- Liste -----
  const [rows, setRows] = useState<AbasSternzeit[] | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [listError, setListError] = useState<string | null>(null)
  const [listLoading, setListLoading] = useState(false)
  const [searchInput, setSearchInput] = useState('')
  /** die gesuchte Kennung; null heißt die Liste, nicht ein Treffer */
  const [searchedSternzeit, setSearchedSternzeit] = useState<string | null>(null)

  // ----- Popups -----
  const [sternzeitToDelete, setSternzeitToDelete] = useState<string | null>(null)
  const [sternzeitToIgnore, setSternzeitToIgnore] = useState<string | null>(null)
  const [sternzeitForResponse, setSternzeitForResponse] = useState<string | null>(null)

  function errorMessageOf(error: unknown): string {
    return error instanceof RequestError ? error.message : t('common.unknownError')
  }

  function formatInstant(value: string | null): string {
    if (value === null) {
      return ''
    }
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString(i18n.language)
  }

  function loadCounts() {
    setCountsError(null)
    getAbasSternzeitOverviewCounts()
      .then((result) => setCounts(result.data))
      .catch((error: unknown) => setCountsError(errorMessageOf(error)))
  }

  /** Eine Seite laden; null heißt von vorn. Ersetzt die Zeilen, die Liste blättert nur vorwärts und zum Anfang. */
  function loadPage(afterSternzeit: string | null) {
    setListLoading(true)
    setListError(null)
    setSearchedSternzeit(null)
    getAbasSternzeitPage(afterSternzeit)
      .then((result) => {
        setRows(result.data.sternzeiten)
        setHasMore(result.data.hasMore)
      })
      .catch((error: unknown) => setListError(errorMessageOf(error)))
      .finally(() => setListLoading(false))
  }

  useEffect(() => {
    loadCounts()
    loadPage(null)
    // einmal beim Betreten
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /** Der exakte Treffer per Kennung; 404 heißt kein Stammsatz, die Liste bleibt leer mit Hinweis */
  function search(event: FormEvent) {
    event.preventDefault()
    const sternzeit = searchInput.trim()
    if (!isValidSternzeit(sternzeit)) {
      setListError(t('abasmanager.overview.sternzeitRule'))
      return
    }
    setListLoading(true)
    setListError(null)
    setSearchedSternzeit(sternzeit)
    setHasMore(false)
    findAbasSternzeit(sternzeit)
      .then((result) => setRows([result.data]))
      .catch((error: unknown) => {
        if (error instanceof RequestError && error.status === 404) {
          setRows([])
        } else {
          setListError(errorMessageOf(error))
        }
      })
      .finally(() => setListLoading(false))
  }

  /** Einreihen, aus der Box wie aus der Zeile. Die Antwort als Toast, danach die Kopfzahlen neu. */
  function enqueue(sternzeit: string, action: AbasSternzeitQueueAction): Promise<unknown> {
    setEnqueueing(true)
    return enqueueAbasSternzeit(sternzeit, action)
      .then((result) => {
        const text = result.data.alreadyQueued
          ? t('abasmanager.overview.alreadyQueued', { sternzeit })
          : action === 'FETCH'
            ? t('abasmanager.overview.queuedFetch', { sternzeit })
            : t('abasmanager.overview.queuedDelete', { sternzeit })
        showToast({
          kind: result.data.alreadyQueued ? 'info' : 'success',
          title: t('abasmanager.overview.enqueueTitle'),
          text,
        })
        loadCounts()
      })
      .finally(() => setEnqueueing(false))
  }

  function enqueueFromInput(event: FormEvent) {
    event.preventDefault()
    const sternzeit = sternzeitInput.trim()
    if (!isValidSternzeit(sternzeit)) {
      return
    }
    enqueue(sternzeit, 'FETCH')
      .then(() => setSternzeitInput(''))
      .catch((error: unknown) =>
        showToast({ kind: 'error', title: t('abasmanager.overview.notQueued'), text: errorMessageOf(error) }),
      )
  }

  function enqueueFetchFromRow(sternzeit: string) {
    enqueue(sternzeit, 'FETCH').catch((error: unknown) =>
      showToast({ kind: 'error', title: t('abasmanager.overview.notQueued'), text: errorMessageOf(error) }),
    )
  }

  // ----- Bestand -----

  const countItems: { key: string; label: string; value: number; estimated?: boolean }[] =
    counts === null
      ? []
      : [
          {
            key: 'sternzeitCount',
            label: t('abasmanager.overview.sternzeitCount'),
            value: counts.sternzeitCount,
            estimated: counts.sternzeitCountEstimated,
          },
          {
            key: 'withoutObjectType',
            label: t('abasmanager.overview.withoutObjectType'),
            value: counts.sternzeitWithoutObjectTypeCount,
          },
          {
            key: 'queueCount',
            label: t('abasmanager.overview.queueCount'),
            value: counts.queueCount,
            estimated: counts.queueCountEstimated,
          },
          { key: 'queueClaimed', label: t('abasmanager.overview.queueClaimed'), value: counts.queueClaimedCount },
          { key: 'queueFailed', label: t('abasmanager.overview.queueFailed'), value: counts.queueFailedCount },
          { key: 'ignored', label: t('abasmanager.overview.ignoredCount'), value: counts.ignoredCount },
        ]

  const reloadCountsButton = (
    <Hoverlay text={t('abasmanager.overview.reloadCounts')}>
      <button
        type="button"
        className="button-other"
        aria-label={t('abasmanager.overview.reloadCounts')}
        onClick={loadCounts}
      >
        <Icon name="refresh" />
      </button>
    </Hoverlay>
  )

  let countsFooter
  if (countsError !== null) {
    countsFooter = <span className="text-danger">{countsError}</span>
  } else if (counts === null) {
    countsFooter = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else {
    countsFooter = <span>{t('abasmanager.overview.countsFooter')}</span>
  }

  // ----- Liste -----

  const columns: TableColumn<AbasSternzeit>[] = [
    {
      header: t('abasmanager.overview.columns.sternzeit'),
      cell: (row) => <strong>{row.sternzeit}</strong>,
    },
    {
      header: t('abasmanager.overview.columns.objectType'),
      headerHint: t('abasmanager.overview.columns.objectTypeHint'),
      cell: (row) =>
        row.objectType ?? <span className="text-body-secondary">{t('abasmanager.overview.noObjectType')}</span>,
    },
    {
      header: t('abasmanager.overview.columns.firstPushedAt'),
      headerHint: t('abasmanager.overview.columns.firstPushedAtHint'),
      cell: (row) => formatInstant(row.firstPushedAt),
      unimportant: true,
    },
    {
      header: t('abasmanager.overview.columns.lastSyncAt'),
      headerHint: t('abasmanager.overview.columns.lastSyncAtHint'),
      cell: (row) =>
        row.lastSyncAt === null ? (
          <span className="text-body-secondary">{t('abasmanager.overview.neverSynced')}</span>
        ) : (
          formatInstant(row.lastSyncAt)
        ),
      unimportant: true,
    },
    {
      header: t('abasmanager.overview.columns.fetchCount'),
      cell: (row) => row.fetchCount,
      unimportant: true,
      width: '6rem',
    },
  ]

  function rowActions(row: AbasSternzeit) {
    return (
      <span className="d-inline-flex align-items-center gap-2">
        <Hoverlay text={t('abasmanager.overview.fetchRow', { sternzeit: row.sternzeit })}>
          <button
            type="button"
            className="button-other"
            aria-label={t('abasmanager.overview.fetchRow', { sternzeit: row.sternzeit })}
            disabled={enqueueing}
            onClick={() => enqueueFetchFromRow(row.sternzeit)}
          >
            <Icon name="refresh" />
          </button>
        </Hoverlay>
        <ViewButton
          label={t('abasmanager.overview.viewResponse', { sternzeit: row.sternzeit })}
          onClick={() => setSternzeitForResponse(row.sternzeit)}
        />
        <Hoverlay text={t('abasmanager.overview.ignoreRow', { sternzeit: row.sternzeit })}>
          <button
            type="button"
            className="button-other"
            aria-label={t('abasmanager.overview.ignoreRow', { sternzeit: row.sternzeit })}
            onClick={() => setSternzeitToIgnore(row.sternzeit)}
          >
            <Icon name="locked" />
          </button>
        </Hoverlay>
        <DeleteButton
          label={t('abasmanager.overview.deleteRow', { sternzeit: row.sternzeit })}
          onClick={() => setSternzeitToDelete(row.sternzeit)}
        />
      </span>
    )
  }

  const searchForm = (
    <form className="d-flex align-items-center gap-2" onSubmit={search}>
      <input
        type="text"
        className="form-control form-control-sm"
        value={searchInput}
        maxLength={STERNZEIT_MAX_LENGTH}
        placeholder={t('abasmanager.overview.searchPlaceholder')}
        aria-label={t('abasmanager.overview.searchPlaceholder')}
        onChange={(event) => setSearchInput(event.target.value)}
      />
      <button type="submit" className="button-other" disabled={listLoading || searchInput.trim() === ''}>
        {t('common.search')}
      </button>
      {searchedSternzeit !== null && (
        <button type="button" className="button-other" onClick={() => loadPage(null)}>
          {t('abasmanager.overview.showList')}
        </button>
      )}
    </form>
  )

  let listFooter
  if (listError !== null) {
    listFooter = <span className="text-danger">{listError}</span>
  } else if (listLoading || rows === null) {
    listFooter = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else if (searchedSternzeit !== null) {
    listFooter = (
      <span>
        {rows.length === 0
          ? t('abasmanager.overview.notFound', { sternzeit: searchedSternzeit })
          : t('abasmanager.overview.found', { sternzeit: searchedSternzeit })}
      </span>
    )
  } else {
    const lastSternzeit = rows.length === 0 ? null : rows[rows.length - 1].sternzeit
    listFooter = (
      <span className="d-inline-flex align-items-center gap-2 flex-wrap">
        <span>{t('abasmanager.overview.pageInfo', { count: rows.length })}</span>
        <button type="button" className="button-other" onClick={() => loadPage(null)}>
          {t('abasmanager.overview.toStart')}
        </button>
        <button
          type="button"
          className="button-other"
          disabled={!hasMore || lastSternzeit === null}
          onClick={() => loadPage(lastSternzeit)}
        >
          {t('abasmanager.overview.next')}
        </button>
      </span>
    )
  }

  const enqueueInputValid = isValidSternzeit(sternzeitInput.trim())

  return (
    <>
      <AbasSternzeitFetcherStatusBox />

      <ContentBox title={t('abasmanager.overview.countsTitle')} actions={reloadCountsButton} footer={countsFooter}>
        <div className="d-flex flex-wrap gap-3">
          {countItems.map((item) => (
            <span key={item.key}>
              <strong>{item.value.toLocaleString(i18n.language)}</strong> {item.label}
              {item.estimated && <> ({t('abasmanager.overview.estimated')})</>}
            </span>
          ))}
        </div>
      </ContentBox>

      <AbasSternzeitTransferBox onImported={loadCounts} />

      <ContentBox title={t('abasmanager.overview.enqueueTitle')} footer={t('abasmanager.overview.enqueueFooter')}>
        <form className="d-flex align-items-center gap-2 flex-wrap" onSubmit={enqueueFromInput}>
          <Hoverlay text={t('abasmanager.overview.sternzeitRule')}>
            <input
              type="text"
              className="form-control form-control-sm"
              value={sternzeitInput}
              maxLength={STERNZEIT_MAX_LENGTH}
              placeholder={t('abasmanager.overview.sternzeitPlaceholder')}
              aria-label={t('abasmanager.overview.sternzeitPlaceholder')}
              onChange={(event) => setSternzeitInput(event.target.value)}
            />
          </Hoverlay>
          <button type="submit" className="button-save" disabled={!enqueueInputValid || enqueueing}>
            {t('abasmanager.overview.fetch')}
          </button>
          <button
            type="button"
            className="button-other"
            disabled={!enqueueInputValid || enqueueing}
            onClick={() => setSternzeitToIgnore(sternzeitInput.trim())}
          >
            <Icon name="locked" /> {t('abasmanager.overview.ignore')}
          </button>
          <DeleteButton
            label={t('abasmanager.overview.delete')}
            disabled={!enqueueInputValid || enqueueing}
            onClick={() => setSternzeitToDelete(sternzeitInput.trim())}
          >
            {t('abasmanager.overview.delete')}
          </DeleteButton>
        </form>
      </ContentBox>

      <AbasSternzeitQueueTopBox onIgnore={setSternzeitToIgnore} />

      <ContentBox title={t('abasmanager.overview.listTitle')} actions={searchForm} footer={listFooter}>
        {rows !== null && (
          <Table
            columns={columns}
            rows={rows}
            rowKey={(row) => row.sternzeit}
            actions={rowActions}
            actionsWidth="9rem"
            detailTitle={(row) => row.sternzeit}
            emptyText={
              searchedSternzeit === null ? t('abasmanager.overview.empty') : t('abasmanager.overview.notFoundShort')
            }
          />
        )}
      </ContentBox>

      <DeletePopup
        open={sternzeitToDelete !== null}
        title={t('abasmanager.overview.deleteTitle')}
        onConfirm={() => enqueue(sternzeitToDelete ?? '', 'DELETE')}
        onDeleted={() => setSternzeitInput('')}
        onClose={() => setSternzeitToDelete(null)}
      >
        <Trans
          i18nKey="abasmanager.overview.deleteQuestion"
          values={{ sternzeit: sternzeitToDelete ?? '' }}
          components={{ strong: <strong /> }}
        />
      </DeletePopup>

      <AbasIgnoreSternzeitPopup
        sternzeit={sternzeitToIgnore}
        onClose={() => setSternzeitToIgnore(null)}
        onIgnored={() => {
          setSternzeitInput('')
          loadCounts()
        }}
      />

      <AbasSternzeitResponsePopup sternzeit={sternzeitForResponse} onClose={() => setSternzeitForResponse(null)} />
    </>
  )
}
