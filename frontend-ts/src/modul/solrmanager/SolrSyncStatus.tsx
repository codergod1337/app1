import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { useLoad } from '../../api/useLoad.ts'
import { useLanguage } from '../../components/languageContext.ts'
import { getSolrSyncReports, runSolrSync, type SolrCoreSyncReport } from './SolrSync.ts'

interface SolrSyncStatusProps {
  coreKey: string
}

/**
 * Der Abgleich dieses Kerns mit Solr: der letzte Bericht (beim Start und nach jeder Änderung läuft er von selbst) und
 * ein Knopf, ihn jetzt zu machen. Was nur gemeldet wird (Felder nur in Solr, Typkonflikte), steht hier, damit es
 * jemand sieht: Der Abgleich löscht nie und ändert keinen Typ.
 */
export function SolrSyncStatus({ coreKey }: SolrSyncStatusProps) {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const { data: loadedReports, errorMessage: loadErrorMessage } = useLoad(getSolrSyncReports)
  const [freshReports, setFreshReports] = useState<SolrCoreSyncReport[] | null>(null)
  const [running, setRunning] = useState(false)
  const [runErrorMessage, setRunErrorMessage] = useState<string | null>(null)

  const report = (freshReports ?? loadedReports)?.find((candidate) => candidate.coreKey === coreKey) ?? null

  function run() {
    setRunning(true)
    setRunErrorMessage(null)
    runSolrSync(coreKey)
      .then((result) => setFreshReports(result.data))
      .catch((error: unknown) => {
        setRunErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
      })
      .finally(() => setRunning(false))
  }

  const list = (entries: string[]) => (entries.length === 0 ? t('solrmanager.sync.none') : entries.join(', '))

  return (
    <div className="d-flex flex-column gap-2">
      <div className="d-flex flex-wrap align-items-center gap-2">
        <button type="button" className="button-other" disabled={running} onClick={run}>
          {running ? t('solrmanager.sync.running') : t('solrmanager.sync.run')}
        </button>
        {(loadErrorMessage ?? runErrorMessage) !== null && (
          <span className="text-danger">{loadErrorMessage ?? runErrorMessage}</span>
        )}
      </div>

      {report === null && loadErrorMessage === null && (
        <span className="text-secondary">{t('solrmanager.sync.noReport')}</span>
      )}

      {report !== null && !report.solrReachable && (
        <span className="text-danger">{t('solrmanager.sync.unreachable', { error: report.error ?? '' })}</span>
      )}
      {report !== null && report.solrReachable && !report.coreExists && (
        <span className="text-warning">{t('solrmanager.sync.missingCore')}</span>
      )}
      {report !== null && report.solrReachable && report.coreExists && report.error !== null && (
        <span className="text-danger">{t('solrmanager.sync.failed', { error: report.error })}</span>
      )}

      {report !== null && report.solrReachable && report.coreExists && (
        <dl className="details-list">
          <div>
            <dt>{t('solrmanager.sync.fieldsInSolr')}</dt>
            <dd>{report.fieldsInSolr}</dd>
          </div>
          <div>
            <dt>{t('solrmanager.sync.added')}</dt>
            <dd>{list(report.added)}</dd>
          </div>
          <div>
            <dt>{t('solrmanager.sync.replaced')}</dt>
            <dd>{list(report.replaced)}</dd>
          </div>
          <div>
            <dt>{t('solrmanager.sync.copyFieldsAdded')}</dt>
            <dd>{list(report.copyFieldsAdded)}</dd>
          </div>
          <div>
            <dt>{t('solrmanager.sync.maxBooleanClauses')}</dt>
            <dd>
              {report.maxBooleanClauses}
              {report.maxBooleanClausesChanged && ` (${t('solrmanager.sync.maxBooleanClausesSet')})`}
            </dd>
          </div>
          <div>
            <dt>{t('solrmanager.sync.onlyInSolr')}</dt>
            <dd className={report.onlyInSolr.length > 0 ? 'text-warning' : undefined}>{list(report.onlyInSolr)}</dd>
          </div>
          <div>
            <dt>{t('solrmanager.sync.typeConflicts')}</dt>
            <dd className={report.typeConflicts.length > 0 ? 'text-danger' : undefined}>{list(report.typeConflicts)}</dd>
          </div>
          <div>
            <dt>{t('solrmanager.sync.syncedAt')}</dt>
            <dd>
              {new Date(report.syncedAt).toLocaleString(language)}, {t('dms.result.ms', { count: report.durationMs })}
            </dd>
          </div>
          {report.error === null &&
            report.onlyInSolr.length === 0 &&
            report.typeConflicts.length === 0 && (
              <div>
                <dt />
                <dd className="text-success">{t('solrmanager.sync.ok')}</dd>
              </div>
            )}
        </dl>
      )}
    </div>
  )
}
