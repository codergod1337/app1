import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import type { OriginState } from '../../components/Origin.ts'
import { SolrResolvedDocumentsPageTable } from '../../components/SolrResolvedDocumentsPageTable.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import type { SolrAdvancedFieldGrantAccessPerCore, SolrDocumentGrantedDocsPerCore } from '../grant/Grant.ts'
import { grantDateTimeOrUnlimited, grantEntityStateDisplayName } from '../grant/grantDisplay.ts'
import { exactResolveFieldValuesFromRegisterJson } from '../solrmanager/SolrExactResolve.ts'
import {
  DMS_CORE_KEY,
  getCurrentUserDmsAccess,
  getCurrentUsersDmsGrants,
  resolveDmsDocumentsPageGrantedToCurrentUser,
  type CurrentUserDmsAccessForClient,
  type DmsCurrentUsersGrantsForClient,
  type DmsResolvedDocumentsPage,
} from './Dms.ts'
import { DmsSolrDocumentActionButtons } from './DmsSolrDocumentActionButtons.tsx'

const PAGE_SIZE = 25

/** Beim Verlassen des Tabs mitgeben, damit Details und Viewer hierher zurückführen. Das label ist ein Schlüssel des Katalogs. */
const ORIGIN_STATE: OriginState = { origin: { path: '/dms/my-grants', label: 'dms.tabs.myGrants' } }

/**
 * Tab „Meine Freigaben“ (Vorgabe 2026-10-04): der Weg eines eingeschränkten Users zu seinen Dokumenten, denn mit der AR
 * DMS allein löst er nur nach id auf. Oben seine Dateifreigaben aus dem Register (Bedingungen, Anzahl, von wem, gültig
 * bis, Status) und seine Feldfreigaben (wonach er zusätzlich auflösen darf), darunter die Trefferliste aller
 * DMS-Dokumente, die seine guid in userGrantedAccess tragen, mit den bekannten Aktionen. Die Bedingung setzt das
 * Backend aus dem Token, der Client schickt nur Seite und Größe.
 */
export function DmsMyGrantsTab() {
  const { t, i18n } = useTranslation()
  const { entityStates } = useMasterData()
  const [grants, setGrants] = useState<DmsCurrentUsersGrantsForClient | null>(null)
  const [access, setAccess] = useState<CurrentUserDmsAccessForClient | null>(null)
  const [resolvedPage, setResolvedPage] = useState<DmsResolvedDocumentsPage | null>(null)
  const [busy, setBusy] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const unlimited = t('grant.common.unlimited')

  function loadPage(page: number) {
    setBusy(true)
    resolveDmsDocumentsPageGrantedToCurrentUser(page, PAGE_SIZE)
      .then(({ data }) => setResolvedPage(data))
      .catch((error: unknown) =>
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError')),
      )
      .finally(() => setBusy(false))
  }

  useEffect(() => {
    getCurrentUsersDmsGrants()
      .then(({ data }) => setGrants(data))
      .catch((error: unknown) =>
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError')),
      )
    getCurrentUserDmsAccess()
      .then(({ data }) => setAccess(data))
      .catch((error: unknown) =>
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError')),
      )
    loadPage(0)
    // nur beim ersten Zeichnen
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const nameOf = (guid: string) => grants?.usersDisplayNameByGuid[guid] ?? guid

  function conditionsOf(grant: SolrDocumentGrantedDocsPerCore) {
    const stored = exactResolveFieldValuesFromRegisterJson(grant.fieldValues)
    return (
      <>
        {Object.entries(stored).map(([name, values]) => (
          <div key={name}>
            <strong>{name}</strong>: {values.join(', ')}
          </div>
        ))}
      </>
    )
  }

  const documentGrantColumns: TableColumn<SolrDocumentGrantedDocsPerCore>[] = [
    { header: t('grant.documents.conditions'), cell: conditionsOf },
    { header: t('grant.documents.documents'), cell: (grant) => grant.documentCount, unimportant: true },
    {
      header: t('grant.common.grantedBy'),
      cell: (grant) => `${nameOf(grant.grantedByUsersGuid)}, ${grantDateTimeOrUnlimited(grant.grantedAt, i18n.language, unlimited)}`,
      unimportant: true,
    },
    {
      header: t('grant.common.validUntil'),
      cell: (grant) => grantDateTimeOrUnlimited(grant.validUntil, i18n.language, unlimited),
      unimportant: true,
    },
    { header: t('grant.common.status'), cell: (grant) => grantEntityStateDisplayName(entityStates, grant.status) },
  ]

  const fieldGrantColumns: TableColumn<SolrAdvancedFieldGrantAccessPerCore>[] = [
    { header: t('grant.fields.field'), cell: (grant) => <code>{grant.fieldName}</code> },
    {
      header: t('grant.common.grantedBy'),
      cell: (grant) => `${nameOf(grant.grantedByUsersGuid)}, ${grantDateTimeOrUnlimited(grant.grantedAt, i18n.language, unlimited)}`,
      unimportant: true,
    },
    {
      header: t('grant.common.validUntil'),
      cell: (grant) => grantDateTimeOrUnlimited(grant.validUntil, i18n.language, unlimited),
      unimportant: true,
    },
    { header: t('grant.common.status'), cell: (grant) => grantEntityStateDisplayName(entityStates, grant.status) },
  ]

  const loading = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />

  return (
    <>
      <ContentBox
        title={t('dms.myGrants.documentGrantsTitle')}
        footer={errorMessage !== null ? <span className="text-danger">{errorMessage}</span> : undefined}
      >
        <p>{t('dms.myGrants.intro')}</p>
        {grants === null ? (
          loading
        ) : (
          <Table
            columns={documentGrantColumns}
            rows={grants.documentGrants}
            rowKey={(grant) => String(grant.id)}
            detailTitle={(grant) => `${grant.coreKey} · ${grantDateTimeOrUnlimited(grant.grantedAt, i18n.language, unlimited)}`}
            emptyText={t('dms.myGrants.noDocumentGrants')}
          />
        )}
      </ContentBox>

      <ContentBox title={t('dms.myGrants.fieldGrantsTitle')}>
        {grants === null ? (
          loading
        ) : (
          <Table
            columns={fieldGrantColumns}
            rows={grants.fieldGrants}
            rowKey={(grant) => String(grant.id)}
            detailTitle={(grant) => grant.fieldName}
            emptyText={t('dms.myGrants.noFieldGrants')}
          />
        )}
      </ContentBox>

      <ContentBox title={t('dms.myGrants.documentsTitle', { count: resolvedPage?.totalFound ?? 0 })}>
        {resolvedPage === null || access === null ? (
          loading
        ) : (
          <SolrResolvedDocumentsPageTable
            coreKey={DMS_CORE_KEY}
            resolvedPage={resolvedPage}
            readableFieldAndHookNames={access.readableFieldAndHookNamesOfCoreForCurrentUser}
            onPage={loadPage}
            busy={busy}
            rowActions={(document) => <DmsSolrDocumentActionButtons document={document} origin={ORIGIN_STATE} />}
          />
        )}
      </ContentBox>
    </>
  )
}
