import { useEffect, useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { getAllUsers, type Users } from '../users/Users.ts'
import {
  expireGrants,
  getAllHookGrants,
  grantHooksToUsers,
  revokeHookGrant,
  usersDisplayLabel,
  type SolrHookGrantAccess,
} from './Grant.ts'
import {
  grantDateTimeOrUnlimited,
  grantEntityStateDisplayName,
  isGrantInForce,
  validUntilLocalInputToIso,
} from './grantDisplay.ts'

/**
 * Tab „Hook-Freigaben“ (Vorgabe 2026-10-07), serverweit: oben die neue Freigabe (Empfänger, alle Hooks zum Ankreuzen
 * ohne die, die der User schon hat, Ablauf). Darunter das Register, neueste zuerst, entziehen nur bei aktiven Zeilen.
 * Gelöscht wird nichts. Eine Hook-Freigabe ist ein Auflöserecht: Der Empfänger darf nach dem Hook auflösen, obwohl er
 * HOOK_FULL nicht hat, in jedem Kern, in dem er die Tür <KERN> hat, denn jeder Kern trägt sämtliche Hooks. Der Hook
 * wird damit auch lesbar. Hier sitzt auch der Knopf, der abgelaufene Hook- und Feldfreigaben aufräumt.
 */
export function HookGrantsTab() {
  const { t, i18n } = useTranslation()
  const { solrHooks, entityStates } = useMasterData()
  const [grants, setGrants] = useState<SolrHookGrantAccess[] | null>(null)
  const [users, setUsers] = useState<Users[] | null>(null)
  const [loadErrorMessage, setLoadErrorMessage] = useState<string | null>(null)
  const [granteeUsersGuid, setGranteeUsersGuid] = useState('')
  const [chosenHookKeys, setChosenHookKeys] = useState<string[]>([])
  const [validUntil, setValidUntil] = useState('')
  const [busy, setBusy] = useState(false)
  const [formErrorMessage, setFormErrorMessage] = useState<string | null>(null)
  const [formInfo, setFormInfo] = useState<string | null>(null)
  const [expiryInfo, setExpiryInfo] = useState<string | null>(null)
  // Sicherheitsabfrage vor dem Entziehen: null, solange keine offen ist
  const [grantToRevoke, setGrantToRevoke] = useState<SolrHookGrantAccess | null>(null)

  const unlimited = t('grant.common.unlimited')

  function loadGrants() {
    getAllHookGrants()
      .then(({ data }) => setGrants(data))
      .catch((error: unknown) =>
        setLoadErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError')),
      )
  }

  useEffect(() => {
    loadGrants()
    getAllUsers()
      .then(({ data }) => setUsers(data))
      .catch((error: unknown) =>
        setLoadErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError')),
      )
    // nur beim ersten Zeichnen
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /** Was dieser User schon hat: aktive, nicht abgelaufene Zeilen */
  const alreadyGrantedHookKeys = new Set(
    (grants ?? [])
      .filter((grant) => grant.granteeUsersGuid === granteeUsersGuid && isGrantInForce(grant))
      .map((grant) => grant.hookKey),
  )

  const grantableHooks = (solrHooks ?? []).filter((hook) => !alreadyGrantedHookKeys.has(hook.key))

  const canGrant = !busy && granteeUsersGuid !== '' && chosenHookKeys.length > 0

  function hookLabel(hookKey: string): string {
    const hook = solrHooks?.find((candidate) => candidate.key === hookKey)
    const name = hook ? translate(hook.displayName) : ''
    return name !== '' ? `${name} (${hookKey})` : hookKey
  }

  function toggleHookKey(hookKey: string) {
    setChosenHookKeys((current) =>
      current.includes(hookKey) ? current.filter((entry) => entry !== hookKey) : [...current, hookKey],
    )
    setFormInfo(null)
  }

  function runGrant() {
    setBusy(true)
    setFormErrorMessage(null)
    setFormInfo(null)
    grantHooksToUsers({ granteeUsersGuid, hookKeys: chosenHookKeys, validUntil: validUntilLocalInputToIso(validUntil) })
      .then(({ data }) => {
        setFormInfo(t('grant.hooks.granted', { count: data.length }))
        setChosenHookKeys([])
        loadGrants()
      })
      .catch((error: unknown) =>
        setFormErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError')),
      )
      .finally(() => setBusy(false))
  }

  /** Der Ablauf der Freigaben in Postgres: Hook- und Feldfreigaben. Dateifreigaben laufen je Kern im Tab Dateifreigaben ab. */
  function runExpiry() {
    setBusy(true)
    setExpiryInfo(null)
    expireGrants()
      .then(({ data }) => {
        setExpiryInfo(
          t('grant.common.expired', { hookGrants: data.expiredHookGrants, fieldGrants: data.expiredFieldGrants }),
        )
        loadGrants()
      })
      .catch((error: unknown) =>
        setExpiryInfo(error instanceof RequestError ? error.message : t('common.unknownError')),
      )
      .finally(() => setBusy(false))
  }

  const columns: TableColumn<SolrHookGrantAccess>[] = [
    { header: t('grant.common.grantee'), cell: (grant) => usersDisplayLabel(users, grant.granteeUsersGuid) },
    { header: t('grant.hooks.hook'), cell: (grant) => hookLabel(grant.hookKey) },
    {
      header: t('grant.common.grantedBy'),
      cell: (grant) =>
        `${usersDisplayLabel(users, grant.grantedByUsersGuid)}, ${grantDateTimeOrUnlimited(grant.grantedAt, i18n.language, unlimited)}`,
      unimportant: true,
    },
    {
      header: t('grant.common.validUntil'),
      cell: (grant) => grantDateTimeOrUnlimited(grant.validUntil, i18n.language, unlimited),
      unimportant: true,
    },
    { header: t('grant.common.status'), cell: (grant) => grantEntityStateDisplayName(entityStates, grant.status) },
  ]

  let listFooter
  if (loadErrorMessage !== null) {
    listFooter = <span className="text-danger">{loadErrorMessage}</span>
  } else if (grants === null) {
    listFooter = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else {
    listFooter = (
      <>
        <span>{t('grant.hooks.count', { count: grants.length })}</span>
        {expiryInfo !== null && <span className="ms-3">{expiryInfo}</span>}
      </>
    )
  }

  const formFooter = (
    <>
      {formErrorMessage !== null && <span className="text-danger">{formErrorMessage}</span>}
      {formInfo !== null && <span className="text-success">{formInfo}</span>}
      <span className="ms-auto">
        <button type="button" className="button-save" disabled={!canGrant} onClick={runGrant}>
          {t('grant.hooks.grant')}
        </button>
      </span>
    </>
  )

  return (
    <>
      <ContentBox title={t('grant.hooks.newTitle')} footer={formFooter}>
        <p>{t('grant.hooks.intro')}</p>
        <div className="row g-3 mb-3">
          <div className="col-12 col-md-4">
            <label className="form-label" htmlFor="hook-grant-user">
              {t('grant.common.grantee')}
            </label>
            <select
              id="hook-grant-user"
              className="form-select form-select-sm"
              value={granteeUsersGuid}
              disabled={busy || users === null}
              onChange={(event) => {
                setGranteeUsersGuid(event.target.value)
                setChosenHookKeys([])
                setFormInfo(null)
              }}
            >
              <option value="">{t('grant.common.chooseUser')}</option>
              {(users ?? []).map((user) => (
                <option key={user.guid} value={user.guid}>
                  {usersDisplayLabel(users, user.guid)}
                </option>
              ))}
            </select>
          </div>
          <div className="col-12 col-md-4">
            <label className="form-label" htmlFor="hook-grant-valid-until">
              {t('grant.common.validUntil')}
            </label>
            <input
              id="hook-grant-valid-until"
              type="datetime-local"
              className="form-control form-control-sm"
              value={validUntil}
              disabled={busy}
              onChange={(event) => setValidUntil(event.target.value)}
            />
            <div className="form-text">{t('grant.common.validUntilHint')}</div>
          </div>
        </div>
        <p className="mb-2">
          <strong>{t('grant.hooks.hooks')}</strong>
        </p>
        {grantableHooks.length === 0 ? (
          <p className="mb-0 text-muted">{t('grant.hooks.noHooks')}</p>
        ) : (
          <div className="d-flex flex-wrap gap-3">
            {grantableHooks.map((hook) => (
              <div key={hook.key} className="form-check">
                <input
                  id={`hook-grant-${hook.key}`}
                  className="form-check-input"
                  type="checkbox"
                  disabled={busy || granteeUsersGuid === ''}
                  checked={chosenHookKeys.includes(hook.key)}
                  onChange={() => toggleHookKey(hook.key)}
                />
                <label className="form-check-label" htmlFor={`hook-grant-${hook.key}`}>
                  {hookLabel(hook.key)}
                </label>
              </div>
            ))}
          </div>
        )}
      </ContentBox>

      <ContentBox
        title={t('grant.hooks.title')}
        actions={
          <button type="button" className="button-other" disabled={busy} onClick={runExpiry}>
            {t('grant.common.expire')}
          </button>
        }
        footer={listFooter}
      >
        {grants !== null && (
          <Table
            columns={columns}
            rows={grants}
            rowKey={(grant) => String(grant.id)}
            detailTitle={(grant) => `${usersDisplayLabel(users, grant.granteeUsersGuid)} · ${grant.hookKey}`}
            emptyText={t('grant.hooks.empty')}
            actions={(grant) =>
              isGrantInForce(grant) ? (
                <DeleteButton label={t('grant.common.revoke')} onClick={() => setGrantToRevoke(grant)} />
              ) : null
            }
          />
        )}
      </ContentBox>

      <DeletePopup
        open={grantToRevoke !== null}
        title={t('grant.hooks.revokeTitle')}
        onConfirm={() => revokeHookGrant(grantToRevoke?.id ?? 0)}
        onDeleted={loadGrants}
        onClose={() => setGrantToRevoke(null)}
      >
        <Trans
          i18nKey="grant.hooks.revokeQuestion"
          values={{
            user: usersDisplayLabel(users, grantToRevoke?.granteeUsersGuid ?? ''),
            hook: grantToRevoke?.hookKey ?? '',
          }}
          components={{ strong: <strong /> }}
        />
      </DeletePopup>
    </>
  )
}
