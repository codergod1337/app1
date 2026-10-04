import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { SolrExactResolveFieldValuesInput } from '../../components/SolrExactResolveFieldValuesInput.tsx'
import { SolrResolvedDocumentsPageTable } from '../../components/SolrResolvedDocumentsPageTable.tsx'
import {
  exactResolveFieldsOfCore,
  exactResolveFieldValuesForBackend,
  type SolrExactResolveField,
  type SolrExactResolveFieldValuesByFieldName,
} from '../solrmanager/SolrExactResolve.ts'
import type { OriginState } from '../../components/Origin.ts'
import {
  DMS_CORE_KEY,
  getCurrentUserDmsAccess,
  resolveDmsDocumentsPageByExactFieldValues,
  type CurrentUserDmsAccessForClient,
  type DmsResolvedDocumentsPage,
} from './Dms.ts'
import { DmsSolrDocumentActionButtons } from './DmsSolrDocumentActionButtons.tsx'

const PAGE_SIZES = [10, 25, 50, 100]

/** Beim Verlassen des Tabs mitgeben, damit Details und Viewer hierher zurückführen. Das label ist ein Schlüssel des Katalogs. */
const ORIGIN_STATE: OriginState = { origin: { path: '/dms/resolve', label: 'dms.tabs.resolve' } }

/**
 * Tab „Auflösen“ des DMS: zeigt, was der current user im Kern darf (Vollsicht oder nur Freigaben, Suche oder nicht),
 * nach welchen Feldern er auflösen darf, und löst damit exakt auf. Die Treffer kommen mit genau den Feldern, die er
 * lesen darf. So lässt sich der Zugriff eines eingeschränkten Users prüfen, indem man sich als er anmeldet.
 */
export function DmsExactResolveTab() {
  const { t } = useTranslation()
  const { solrFields, solrHooks } = useMasterData()
  const [access, setAccess] = useState<CurrentUserDmsAccessForClient | null>(null)
  const [accessErrorMessage, setAccessErrorMessage] = useState<string | null>(null)
  const [fieldValuesByFieldName, setFieldValuesByFieldName] = useState<SolrExactResolveFieldValuesByFieldName>({})
  const [size, setSize] = useState(25)
  const [sortFieldName, setSortFieldName] = useState('')
  const [sortDescending, setSortDescending] = useState(false)
  const [resolvedPage, setResolvedPage] = useState<DmsResolvedDocumentsPage | null>(null)
  const [running, setRunning] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    getCurrentUserDmsAccess()
      .then(({ data }) => setAccess(data))
      .catch((error: unknown) =>
        setAccessErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError')),
      )
    // nur beim ersten Zeichnen
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const fields: SolrExactResolveField[] =
    access === null
      ? []
      : exactResolveFieldsOfCore(
          solrFields ?? [],
          solrHooks ?? [],
          DMS_CORE_KEY,
          access.exactResolveAllowedFieldAndHookNamesOfCoreForCurrentUser,
        )
  const readableFieldAndHookNames = access?.readableFieldAndHookNamesOfCoreForCurrentUser ?? []
  // ohne mindestens eine Bedingung mit Wert kein Auflösen: Es gibt nie alles aus, das Backend lehnt es ebenso ab
  const hasConditions = Object.keys(exactResolveFieldValuesForBackend(fields, fieldValuesByFieldName)).length > 0

  function labelOf(field: SolrExactResolveField): string {
    if (!field.hook) {
      return field.name
    }
    const hook = solrHooks?.find((candidate) => candidate.key === field.name)
    const name = hook ? translate(hook.displayName) : ''
    return name !== '' ? `${name} (${field.name})` : field.name
  }

  function run(page: number) {
    setRunning(true)
    setErrorMessage(null)
    resolveDmsDocumentsPageByExactFieldValues({
      fieldValuesByFieldName: exactResolveFieldValuesForBackend(fields, fieldValuesByFieldName),
      page,
      size,
      sortFieldName: sortFieldName === '' ? null : sortFieldName,
      sortDescending,
    })
      .then(({ data }) => setResolvedPage(data))
      .catch((error: unknown) => {
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
      })
      .finally(() => setRunning(false))
  }

  let accessBox
  if (accessErrorMessage !== null) {
    accessBox = <p className="text-danger mb-0">{accessErrorMessage}</p>
  } else if (access === null) {
    accessBox = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else {
    accessBox = (
      <>
        <p className="mb-2">
          <span
            className={`badge ${access.everyFieldAndHookOfCoreReadableByCurrentUser ? 'bg-success' : 'bg-secondary'} me-2`}
          >
            {access.everyFieldAndHookOfCoreReadableByCurrentUser
              ? t('dms.resolve.fullFields')
              : t('dms.resolve.limitedFields')}
          </span>
          <span className={`badge ${access.freeTextSearchAllowed ? 'bg-success' : 'bg-secondary'}`}>
            {access.freeTextSearchAllowed ? t('dms.resolve.searchAllowed') : t('dms.resolve.searchNotAllowed')}
          </span>
        </p>
        <p className="mb-1">
          <strong>{t('dms.resolve.readableFields', { count: readableFieldAndHookNames.length })}</strong>
        </p>
        <p className="mb-0 d-flex flex-wrap gap-1">
          {readableFieldAndHookNames.map((name) => (
            <span
              key={name}
              className={`badge ${
                access.exactResolveAllowedFieldAndHookNamesOfCoreForCurrentUser.includes(name)
                  ? 'bg-primary'
                  : 'bg-secondary'
              }`}
            >
              {name}
            </span>
          ))}
        </p>
        <p className="form-text mb-0">{t('dms.resolve.resolvableHint')}</p>
      </>
    )
  }

  const resolveFooter = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {errorMessage === null && !hasConditions && <span className="text-secondary">{t('dms.resolve.noConditions')}</span>}
      <span className="ms-auto">
        <button
          type="button"
          className="button-save"
          disabled={running || access === null || !hasConditions}
          onClick={() => run(0)}
        >
          {t('dms.resolve.run')}
        </button>
      </span>
    </>
  )

  return (
    <>
      <ContentBox title={t('dms.resolve.title')} footer={resolveFooter}>
        <p>{t('dms.resolve.intro')}</p>
        <div className="mb-3">{accessBox}</div>
        <p className="mb-2">
          <strong>{t('dms.resolve.conditions')}</strong>
        </p>
        <SolrExactResolveFieldValuesInput
          fields={fields}
          fieldValuesByFieldName={fieldValuesByFieldName}
          onChange={setFieldValuesByFieldName}
          labelOf={labelOf}
          disabled={running || access === null}
        />
        <div className="d-flex flex-wrap align-items-center gap-3 mt-3">
          <label className="d-flex align-items-center gap-2">
            {t('dms.resolve.pageSize')}
            <select
              className="form-select form-select-sm w-auto"
              value={size}
              disabled={running}
              onChange={(event) => setSize(Number(event.target.value))}
            >
              {PAGE_SIZES.map((candidate) => (
                <option key={candidate} value={candidate}>
                  {candidate}
                </option>
              ))}
            </select>
          </label>
          <label className="d-flex align-items-center gap-2">
            {t('dms.resolve.sort')}
            <select
              className="form-select form-select-sm w-auto"
              value={sortFieldName}
              disabled={running}
              onChange={(event) => setSortFieldName(event.target.value)}
            >
              <option value="">{t('dms.resolve.sortNone')}</option>
              {readableFieldAndHookNames.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
          </label>
          <div className="form-check mb-0">
            <input
              id="dms-resolve-sort-descending"
              className="form-check-input"
              type="checkbox"
              disabled={running || sortFieldName === ''}
              checked={sortDescending}
              onChange={(event) => setSortDescending(event.target.checked)}
            />
            <label className="form-check-label" htmlFor="dms-resolve-sort-descending">
              {t('dms.resolve.descending')}
            </label>
          </div>
        </div>
      </ContentBox>

      {resolvedPage !== null && (
        <ContentBox title={t('resolve.total', { count: resolvedPage.totalFound })}>
          <SolrResolvedDocumentsPageTable
            coreKey={DMS_CORE_KEY}
            resolvedPage={resolvedPage}
            readableFieldAndHookNames={readableFieldAndHookNames}
            onPage={run}
            busy={running}
            rowActions={(document) => <DmsSolrDocumentActionButtons document={document} origin={ORIGIN_STATE} />}
          />
        </ContentBox>
      )}
    </>
  )
}
