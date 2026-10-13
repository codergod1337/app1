import { useEffect, useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { showToast } from '../../components/toastStore.ts'
import { UsersGuidName } from '../../components/UsersGuidName.tsx'
import { ADMIN_ACCESS_ROLE_KEY } from '../accessrole/AccessRole.ts'
import { useSession } from '../login/sessionContext.ts'
import { dmsSolrDocumentPath } from './Dms.ts'
import {
  deleteDmsShareToken,
  deleteDmsUserGrant,
  expireDmsShareToken,
  getAllDmsShareTokens,
  getAllDmsUserGrants,
  revokeDmsDocumentFromUser,
  type DmsShareToken,
  type DmsUserGrant,
} from './DmsShare.ts'

/**
 * Das Modul „Freigaben“ (User 2026-10-10): alle Abholscheine des DMS, jüngste zuerst, auch abgelaufene. Angelegt wird
 * ein Schein auf der Dokumentseite, hier steht die Übersicht mit dem Weg zum Dokument und dem Knopf zum Ablaufen
 * lassen. ADMIN darf einen Schein außerdem endgültig löschen, samt History, nach Sicherheitsabfrage (User 2026-10-10).
 * Die Tür ADMIN oder DMS_PUBLIC prüft das Backend, seine Absage steht dann statt der Liste.
 */
export function DmsShareOverviewPage() {
  const { t, i18n } = useTranslation()
  const { hasAccessRole } = useSession()
  const admin = hasAccessRole(ADMIN_ACCESS_ROLE_KEY)
  const [tokens, setTokens] = useState<DmsShareToken[] | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [expiringId, setExpiringId] = useState<number | null>(null)
  /** der Schein in der Sicherheitsabfrage vor dem Löschen */
  const [tokenToDelete, setTokenToDelete] = useState<DmsShareToken | null>(null)
  /** die User-Freigaben aus dem Register: wer wann wem welches Dokument, auch zurückgenommene */
  const [userGrants, setUserGrants] = useState<DmsUserGrant[] | null>(null)
  const [userGrantsError, setUserGrantsError] = useState<string | null>(null)
  const [revoking, setRevoking] = useState(false)
  const [userGrantToDelete, setUserGrantToDelete] = useState<DmsUserGrant | null>(null)

  function load() {
    setErrorMessage(null)
    getAllDmsShareTokens()
      .then(({ data }) => setTokens(data))
      .catch((error: unknown) => {
        setTokens([])
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
      })
    setUserGrantsError(null)
    getAllDmsUserGrants()
      .then(({ data }) => setUserGrants(data))
      .catch((error: unknown) => {
        setUserGrants([])
        setUserGrantsError(error instanceof RequestError ? error.message : t('common.unknownError'))
      })
  }

  function revokeUserGrant(userGrant: DmsUserGrant) {
    setRevoking(true)
    revokeDmsDocumentFromUser(userGrant.documentId, userGrant.granteeUsersGuid)
      .then(load)
      .catch((error: unknown) =>
        showToast({
          kind: 'error',
          title: t('dms.share.user.revoke'),
          text: error instanceof RequestError ? error.message : t('common.unknownError'),
        }),
      )
      .finally(() => setRevoking(false))
  }

  useEffect(() => {
    load()
    // einmal beim Betreten
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function expire(shareToken: DmsShareToken) {
    setExpiringId(shareToken.id)
    expireDmsShareToken(shareToken.id)
      .then(load)
      .catch((error: unknown) =>
        showToast({
          kind: 'error',
          title: t('dms.share.expire'),
          text: error instanceof RequestError ? error.message : t('common.unknownError'),
        }),
      )
      .finally(() => setExpiringId(null))
  }

  function dateTime(value: string): string {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString(i18n.language)
  }

  const columns: TableColumn<DmsShareToken>[] = [
    { header: t('dms.share.columns.document'), width: '9rem', cell: (row) => documentLink(row.documentId) },
    { header: t('dms.share.columns.kind'), width: '7rem', cell: (row) => t(`dms.share.kindShort.${row.kind}`) },
    {
      header: t('dms.share.columns.status'),
      width: '7rem',
      cell: (row) => (
        <span className={row.status === 'ACTIVE' ? 'text-success' : 'text-muted'}>{t(`dms.share.status.${row.status}`)}</span>
      ),
    },
    {
      header: t('dms.share.columns.uses'),
      width: '6rem',
      cell: (row) =>
        row.redemptions.length === 0 ? (
          `${row.uses} / ${row.maxUses}`
        ) : (
          <Hoverlay
            text={
              <span className="d-flex flex-column">
                {row.redemptions.map((entry) => (
                  <span key={entry}>{entry}</span>
                ))}
              </span>
            }
          >
            <span>
              {row.uses} / {row.maxUses}
            </span>
          </Hoverlay>
        ),
    },
    { header: t('dms.share.columns.expiresAt'), width: '11rem', cell: (row) => dateTime(row.expiresAt) },
    { header: t('dms.share.columns.recipient'), unimportant: true, cell: (row) => row.recipient ?? '—' },
    {
      header: t('dms.share.columns.grantedBy'),
      unimportant: true,
      cell: (row) => <UsersGuidName guid={row.grantedByUsersGuid} />,
    },
  ]

  function documentLink(documentId: string) {
    return (
      <Hoverlay text={<code>{documentId}</code>}>
        <Link to={dmsSolrDocumentPath(documentId)} aria-label={t('dms.share.openDocument', { id: documentId })}>
          <code>{documentId.slice(0, 12)}…</code>
        </Link>
      </Hoverlay>
    )
  }

  const userGrantColumns: TableColumn<DmsUserGrant>[] = [
    { header: t('dms.share.columns.document'), width: '9rem', cell: (row) => documentLink(row.documentId) },
    { header: t('dms.share.user.columns.grantee'), cell: (row) => <UsersGuidName guid={row.granteeUsersGuid} /> },
    {
      header: t('dms.share.user.columns.grantedBy'),
      unimportant: true,
      cell: (row) => <UsersGuidName guid={row.grantedByUsersGuid} />,
    },
    { header: t('dms.share.user.columns.createdAt'), width: '11rem', cell: (row) => dateTime(row.createdAt) },
    {
      header: t('dms.share.columns.status'),
      width: '9rem',
      cell: (row) =>
        row.status === 'ACTIVE' ? (
          <span className="text-success">{t('dms.share.user.status.ACTIVE')}</span>
        ) : (
          <Hoverlay
            text={
              <span>
                {row.revokedAt !== null ? dateTime(row.revokedAt) : ''}{' '}
                {row.revokedByUsersGuid !== null && <UsersGuidName guid={row.revokedByUsersGuid} />}
              </span>
            }
          >
            <span className="text-muted">{t('dms.share.user.status.REVOKED')}</span>
          </Hoverlay>
        ),
    },
  ]

  return (
    <div className="main-content">
      <ContentBox title={t('dms.share.user.overviewTitle')}>
        <div className="d-flex flex-column gap-2">
          <p className="mb-0 text-secondary small">{t('dms.share.user.overviewIntro')}</p>
          {userGrantsError !== null && <p className="text-danger mb-0">{userGrantsError}</p>}
          {userGrants === null && userGrantsError === null && (
            <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
          )}
          {userGrants !== null && (
            <Table
              columns={userGrantColumns}
              rows={userGrants}
              rowKey={(row) => String(row.id)}
              detailTitle={(row) => dateTime(row.createdAt)}
              emptyText={t('dms.share.user.overviewEmpty')}
              actions={(row) => (
                <span className="d-inline-flex align-items-center gap-2">
                  {row.status === 'ACTIVE' && (
                    <button type="button" className="button-cancel" disabled={revoking} onClick={() => revokeUserGrant(row)}>
                      {t('dms.share.user.revoke')}
                    </button>
                  )}
                  {admin && (
                    <DeleteButton
                      label={t('dms.share.user.delete', { id: row.id })}
                      disabled={revoking}
                      onClick={() => setUserGrantToDelete(row)}
                    />
                  )}
                </span>
              )}
              actionsWidth="12rem"
            />
          )}
        </div>
      </ContentBox>

      <DeletePopup
        open={userGrantToDelete !== null}
        title={t('dms.share.user.deleteTitle')}
        onConfirm={() => deleteDmsUserGrant(userGrantToDelete?.id ?? 0)}
        onDeleted={load}
        onClose={() => setUserGrantToDelete(null)}
      >
        <Trans
          i18nKey="dms.share.user.deleteQuestion"
          values={{ document: userGrantToDelete?.documentId.slice(0, 12) ?? '' }}
          components={{ strong: <strong /> }}
        />
      </DeletePopup>

      <ContentBox title={t('dms.share.overviewTitle')}>
        <div className="d-flex flex-column gap-2">
          <p className="mb-0 text-secondary small">{t('dms.share.overviewIntro')}</p>
          {errorMessage !== null && <p className="text-danger mb-0">{errorMessage}</p>}
          {tokens === null && errorMessage === null && (
            <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
          )}
          {tokens !== null && (
            <Table
              columns={columns}
              rows={tokens}
              rowKey={(row) => String(row.id)}
              detailTitle={(row) => `${t(`dms.share.kindShort.${row.kind}`)} · ${dateTime(row.createdAt)}`}
              emptyText={t('dms.share.overviewEmpty')}
              actions={(row) => (
                <span className="d-inline-flex align-items-center gap-2">
                  {row.status === 'ACTIVE' && (
                    <button
                      type="button"
                      className="button-cancel"
                      disabled={expiringId !== null}
                      onClick={() => expire(row)}
                    >
                      {t('dms.share.expire')}
                    </button>
                  )}
                  {admin && (
                    <DeleteButton
                      label={t('dms.share.delete', { id: row.id })}
                      disabled={expiringId !== null}
                      onClick={() => setTokenToDelete(row)}
                    />
                  )}
                </span>
              )}
              actionsWidth="12rem"
            />
          )}
        </div>
      </ContentBox>

      <DeletePopup
        open={tokenToDelete !== null}
        title={t('dms.share.deleteTitle')}
        onConfirm={() => deleteDmsShareToken(tokenToDelete?.id ?? 0)}
        onDeleted={load}
        onClose={() => setTokenToDelete(null)}
      >
        <Trans
          i18nKey="dms.share.deleteQuestion"
          values={{ kind: t(`dms.share.kindShort.${tokenToDelete?.kind ?? 'BINARY'}`), recipient: tokenToDelete?.recipient ?? '—' }}
          components={{ strong: <strong /> }}
        />
      </DeletePopup>
    </div>
  )
}
