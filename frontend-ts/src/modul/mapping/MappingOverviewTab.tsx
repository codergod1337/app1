import { useEffect, useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import type { EditResponseMappingState } from './EditResponseMappingTab.tsx'
import {
  deleteResponseMapping,
  getAllResponseMappings,
  ignoredPathsOf,
  MAPPING_PATH,
  matchConditionsOf,
  type ResponseMappingWithLines,
} from './ResponseMapping.ts'

/** Beim Verlassen dieses Tabs mitgeben, damit der header „zurück zu Mappings“ anbietet. Das label ist ein Schlüssel des Katalogs. */
const ORIGIN_STATE: OriginState = { origin: { path: `${MAPPING_PATH}/overview`, label: 'mapping.tabs.overview' } }

/**
 * Tab „Mappings“: nur die Übersicht der Gesamt-Mappings zum Filtern und Bearbeiten (User 2026-10-10), je Zeile Typ mit
 * kennzeichnenden Feldern, Ziel, Zahl der Einträge und der ignorierten Adressen, rechts Bearbeiten und Löschen.
 * Angelegt wird am JSON-Baum, in der JSON-Ansicht oder bei „ABAS holen“.
 */
export function MappingOverviewTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [responseMappings, setResponseMappings] = useState<ResponseMappingWithLines[] | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  /** Teilstring über Name, Ziel, Typ und die Keys der Einträge, ohne Groß und Klein */
  const [filterText, setFilterText] = useState('')
  /** zählt jedes Neuladen */
  const [loadCount, setLoadCount] = useState(0)
  // Sicherheitsabfrage vor dem Löschen: null, solange keine offen ist
  const [responseMappingToDelete, setResponseMappingToDelete] = useState<ResponseMappingWithLines | null>(null)

  useEffect(() => {
    let current = true
    const unknownError = t('common.unknownError')
    getAllResponseMappings()
      .then((result) => {
        if (current) {
          setResponseMappings(result.data)
          setErrorMessage(null)
        }
      })
      .catch((error: unknown) => {
        if (current) {
          setErrorMessage(error instanceof RequestError ? error.message : unknownError)
        }
      })
    return () => {
      current = false
    }
  }, [loadCount, t])

  function editResponseMapping(responseMapping: ResponseMappingWithLines) {
    const state: EditResponseMappingState = { ...ORIGIN_STATE, responseMappingId: responseMapping.responseMapping.id ?? 0 }
    navigate(`${MAPPING_PATH}/edit`, { state })
  }

  const filter = filterText.trim().toLowerCase()
  const shownResponseMappings =
    responseMappings === null
      ? null
      : responseMappings.filter(
          (candidate) =>
            filter === '' ||
            [
              candidate.responseMapping.key,
              candidate.responseMapping.targetKey ?? '',
              candidate.responseMapping.responseObjectType,
              ...candidate.lines.map((line) => line.key),
            ].some((text) => text.toLowerCase().includes(filter)),
        )

  const columns: TableColumn<ResponseMappingWithLines>[] = [
    { header: t('common.key'), cell: (candidate) => <strong>{candidate.responseMapping.key}</strong> },
    {
      header: t('mapping.overview.columns.responseObjectType'),
      cell: (candidate) => {
        const conditions = matchConditionsOf(candidate.responseMapping)
        return (
          <span className="d-inline-flex flex-column">
            <code>{candidate.responseMapping.responseObjectType}</code>
            {conditions.map((condition) => (
              <code key={condition.path} className="small text-body-secondary">
                {condition.path} = {condition.equals}
              </code>
            ))}
          </span>
        )
      },
    },
    {
      header: t('mapping.overview.columns.target'),
      cell: (candidate) => candidate.responseMapping.targetKey ?? <span className="text-body-secondary">{t('mapping.overview.noTarget')}</span>,
      unimportant: true,
    },
    {
      header: t('mapping.overview.columns.lines'),
      cell: (candidate) => (
        <span className="d-inline-flex flex-wrap gap-1">
          {candidate.lines.map((line) => (
            <span key={line.id ?? line.position} className="badge text-bg-secondary fw-normal">
              {line.key}
            </span>
          ))}
        </span>
      ),
    },
    {
      header: t('mapping.overview.columns.ignored'),
      cell: (candidate) => ignoredPathsOf(candidate.responseMapping).length,
      unimportant: true,
      width: '6rem',
    },
  ]

  let footer
  if (errorMessage !== null) {
    footer = <span className="text-danger">{errorMessage}</span>
  } else if (responseMappings === null || shownResponseMappings === null) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else if (filter !== '') {
    footer = <span>{t('mapping.overview.filteredCount', { shown: shownResponseMappings.length, count: responseMappings.length })}</span>
  } else {
    footer = <span>{t('mapping.overview.count', { count: responseMappings.length })}</span>
  }

  const filterInput = (
    <input
      type="search"
      className="form-control form-control-sm"
      value={filterText}
      placeholder={t('mapping.overview.filterPlaceholder')}
      aria-label={t('mapping.overview.filterPlaceholder')}
      onChange={(event) => setFilterText(event.target.value)}
    />
  )

  return (
    <>
      <ContentBox title={t('mapping.overview.title')} actions={filterInput} footer={footer}>
        <p>{t('mapping.overview.intro')}</p>
        {shownResponseMappings !== null && (
          <Table
            columns={columns}
            rows={shownResponseMappings}
            rowKey={(candidate) => candidate.responseMapping.key}
            detailTitle={(candidate) => candidate.responseMapping.key}
            emptyText={t('mapping.overview.empty')}
            actions={(candidate) => (
              <>
                <EditButton label={t('common.edit')} onClick={() => editResponseMapping(candidate)} />
                <DeleteButton label={t('common.deleteAction')} onClick={() => setResponseMappingToDelete(candidate)} />
              </>
            )}
          />
        )}
      </ContentBox>
      <DeletePopup
        open={responseMappingToDelete !== null}
        title={t('mapping.overview.deleteTitle')}
        onConfirm={() => deleteResponseMapping(responseMappingToDelete?.responseMapping.id ?? 0)}
        onDeleted={() => setLoadCount((count) => count + 1)}
        onClose={() => setResponseMappingToDelete(null)}
      >
        <Trans
          i18nKey="mapping.overview.deleteQuestion"
          values={{ key: responseMappingToDelete?.responseMapping.key ?? '' }}
          components={{ strong: <strong /> }}
        />
      </DeletePopup>
    </>
  )
}
