import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { SolrResolvedDocumentsPageTable } from '../../components/SolrResolvedDocumentsPageTable.tsx'
import {
  DMS_CORE_KEY,
  getCurrentUserDmsAccess,
  getDmsDocumentsGrantedToMe,
  type CurrentUserDmsAccessForClient,
  type DmsResolvedDocumentsPage,
} from './Dms.ts'
import { DmsSolrDocumentActionButtons } from './DmsSolrDocumentActionButtons.tsx'

const ORIGIN_STATE: OriginState = { origin: { path: '/dms/my-grants', label: 'dms.tabs.myGrants' } }

/**
 * Der Tab „Meine Freigaben“ (User 2026-10-10): die Dokumente, die die guid des angemeldeten Users in userGrantedAccess
 * tragen, also die, die ihm jemand auf der Dokumentseite freigegeben hat. Die Bedingung setzt das Backend, die Liste
 * ist dieselbe Trefferliste wie beim Auflösen, mit den Feldern, die der User lesen darf, und den Wegen zum Dokument.
 */
export function DmsMyGrantsTab() {
  const { t } = useTranslation()
  const [access, setAccess] = useState<CurrentUserDmsAccessForClient | null>(null)
  const [resolvedPage, setResolvedPage] = useState<DmsResolvedDocumentsPage | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [running, setRunning] = useState(false)

  function load(page: number) {
    setRunning(true)
    setErrorMessage(null)
    getDmsDocumentsGrantedToMe(page)
      .then(({ data }) => setResolvedPage(data))
      .catch((error: unknown) => {
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
      })
      .finally(() => setRunning(false))
  }

  useEffect(() => {
    getCurrentUserDmsAccess()
      .then(({ data }) => setAccess(data))
      .catch(() => setAccess(null))
    load(0)
    // einmal beim Betreten
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <ContentBox title={t('dms.myGrants.title', { count: resolvedPage?.totalFound ?? 0 })}>
      <div className="d-flex flex-column gap-2">
        <p className="mb-0 text-secondary small">{t('dms.myGrants.intro')}</p>
        {errorMessage !== null && <p className="text-danger mb-0">{errorMessage}</p>}
        {resolvedPage === null && errorMessage === null && (
          <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
        )}
        {resolvedPage !== null && resolvedPage.totalFound === 0 && (
          <p className="mb-0 text-muted">{t('dms.myGrants.empty')}</p>
        )}
        {resolvedPage !== null && resolvedPage.totalFound > 0 && (
          <SolrResolvedDocumentsPageTable
            coreKey={DMS_CORE_KEY}
            resolvedPage={resolvedPage}
            readableFieldAndHookNames={access?.readableFieldAndHookNamesOfCoreForCurrentUser ?? []}
            onPage={load}
            busy={running}
            rowActions={(document) => <DmsSolrDocumentActionButtons document={document} origin={ORIGIN_STATE} />}
          />
        )}
      </div>
    </ContentBox>
  )
}
