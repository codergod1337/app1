import { useEffect, useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { getAllUsers, type Users } from '../users/Users.ts'
import {
  getAllFieldGrants,
  grantAdvancedFieldsToUsers,
  revokeFieldGrant,
  usersDisplayLabel,
  type SolrAdvancedFieldGrantAccessPerCore,
} from './Grant.ts'
import {
  grantDateTimeOrUnlimited,
  grantEntityStateDisplayName,
  isGrantInForce,
  validUntilLocalInputToIso,
} from './grantDisplay.ts'

/**
 * Tab „Feldfreigaben“: oben die neue Freigabe (Empfänger, Kern, dann alle indexierten Felder des Kerns außer id zum
 * Ankreuzen, ohne die, die der User schon hat, Ablauf). Darunter das Register, neueste zuerst, entziehen nur bei
 * aktiven Zeilen. Gelöscht wird nichts. Eine Feldfreigabe ist ein Auflöserecht (Vorgabe 2026-10-04): Der Empfänger darf
 * nach dem Feld auflösen, obwohl er <KERN>_SUCHE nicht hat; ein erweitertes Feld wird damit auch lesbar. Nach id löst
 * jeder auf, sie steht nicht zur Wahl. Hooks gibt der Tab „Hook-Freigaben“ frei, serverweit (Vorgabe 2026-10-07).
 */
export function AdvancedFieldGrantsPerCoreTab() {
  const { t, i18n } = useTranslation()
  const { solrCores, solrFields, entityStates } = useMasterData()
  const [grants, setGrants] = useState<SolrAdvancedFieldGrantAccessPerCore[] | null>(null)
  const [users, setUsers] = useState<Users[] | null>(null)
  const [loadErrorMessage, setLoadErrorMessage] = useState<string | null>(null)
  const [granteeUsersGuid, setGranteeUsersGuid] = useState('')
  const [coreKey, setCoreKey] = useState('')
  const [chosenFieldNames, setChosenFieldNames] = useState<string[]>([])
  const [validUntil, setValidUntil] = useState('')
  const [busy, setBusy] = useState(false)
  const [formErrorMessage, setFormErrorMessage] = useState<string | null>(null)
  const [formInfo, setFormInfo] = useState<string | null>(null)
  // Sicherheitsabfrage vor dem Entziehen: null, solange keine offen ist
  const [grantToRevoke, setGrantToRevoke] = useState<SolrAdvancedFieldGrantAccessPerCore | null>(null)

  const unlimited = t('grant.common.unlimited')

  function loadGrants() {
    getAllFieldGrants()
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

  useEffect(() => {
    if (coreKey === '' && solrCores !== null && solrCores.length > 0) {
      setCoreKey(solrCores[0].key)
    }
  }, [coreKey, solrCores])

  /** Was dieser User in diesem Kern schon hat: aktive, nicht abgelaufene Zeilen */
  const alreadyGrantedFieldNames = new Set(
    (grants ?? [])
      .filter(
        (grant) => grant.granteeUsersGuid === granteeUsersGuid && grant.coreKey === coreKey && isGrantInForce(grant),
      )
      .map((grant) => grant.fieldName),
  )

  /** Was sich freigeben lässt: jedes indexierte Feld des Kerns außer id, das der User noch nicht hat */
  const grantableFieldNames = (solrFields ?? [])
    .filter((field) => field.coreKey === coreKey && field.indexed && field.name !== 'id')
    .map((field) => field.name)
    .filter((name) => !alreadyGrantedFieldNames.has(name))

  const canGrant = !busy && granteeUsersGuid !== '' && coreKey !== '' && chosenFieldNames.length > 0

  function toggleFieldName(name: string) {
    setChosenFieldNames((current) =>
      current.includes(name) ? current.filter((entry) => entry !== name) : [...current, name],
    )
    setFormInfo(null)
  }

  function changeCore(next: string) {
    setCoreKey(next)
    setChosenFieldNames([])
    setFormInfo(null)
  }

  function runGrant() {
    setBusy(true)
    setFormErrorMessage(null)
    setFormInfo(null)
    grantAdvancedFieldsToUsers({
      granteeUsersGuid,
      coreKey,
      fieldNames: chosenFieldNames,
      validUntil: validUntilLocalInputToIso(validUntil),
    })
      .then(({ data }) => {
        setFormInfo(t('grant.fields.granted', { count: data.length }))
        setChosenFieldNames([])
        loadGrants()
      })
      .catch((error: unknown) =>
        setFormErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError')),
      )
      .finally(() => setBusy(false))
  }

  const columns: TableColumn<SolrAdvancedFieldGrantAccessPerCore>[] = [
    { header: t('grant.common.grantee'), cell: (grant) => usersDisplayLabel(users, grant.granteeUsersGuid) },
    { header: t('grant.common.core'), cell: (grant) => grant.coreKey },
    { header: t('grant.fields.field'), cell: (grant) => grant.fieldName },
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
    listFooter = <span>{t('grant.fields.count', { count: grants.length })}</span>
  }

  const formFooter = (
    <>
      {formErrorMessage !== null && <span className="text-danger">{formErrorMessage}</span>}
      {formInfo !== null && <span className="text-success">{formInfo}</span>}
      <span className="ms-auto">
        <button type="button" className="button-save" disabled={!canGrant} onClick={runGrant}>
          {t('grant.fields.grant')}
        </button>
      </span>
    </>
  )

  return (
    <>
      <ContentBox title={t('grant.fields.newTitle')} footer={formFooter}>
        <p>{t('grant.fields.intro')}</p>
        <div className="row g-3 mb-3">
          <div className="col-12 col-md-4">
            <label className="form-label" htmlFor="field-grant-user">
              {t('grant.common.grantee')}
            </label>
            <select
              id="field-grant-user"
              className="form-select form-select-sm"
              value={granteeUsersGuid}
              disabled={busy || users === null}
              onChange={(event) => {
                setGranteeUsersGuid(event.target.value)
                setChosenFieldNames([])
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
            <label className="form-label" htmlFor="field-grant-core">
              {t('grant.common.core')}
            </label>
            <select
              id="field-grant-core"
              className="form-select form-select-sm"
              value={coreKey}
              disabled={busy || solrCores === null}
              onChange={(event) => changeCore(event.target.value)}
            >
              {coreKey === '' && <option value="">{t('grant.common.chooseCore')}</option>}
              {(solrCores ?? []).map((core) => (
                <option key={core.key} value={core.key}>
                  {core.key}
                </option>
              ))}
            </select>
          </div>
          <div className="col-12 col-md-4">
            <label className="form-label" htmlFor="field-grant-valid-until">
              {t('grant.common.validUntil')}
            </label>
            <input
              id="field-grant-valid-until"
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
          <strong>{t('grant.fields.fields')}</strong>
        </p>
        {grantableFieldNames.length === 0 ? (
          <p className="mb-0 text-muted">{t('grant.fields.noFields')}</p>
        ) : (
          <div className="d-flex flex-wrap gap-3">
            {grantableFieldNames.map((name) => (
              <div key={name} className="form-check">
                <input
                  id={`field-grant-${name}`}
                  className="form-check-input"
                  type="checkbox"
                  disabled={busy || granteeUsersGuid === ''}
                  checked={chosenFieldNames.includes(name)}
                  onChange={() => toggleFieldName(name)}
                />
                <label className="form-check-label" htmlFor={`field-grant-${name}`}>
                  {name}
                </label>
              </div>
            ))}
          </div>
        )}
      </ContentBox>

      <ContentBox title={t('grant.fields.title')} footer={listFooter}>
        {grants !== null && (
          <Table
            columns={columns}
            rows={grants}
            rowKey={(grant) => String(grant.id)}
            detailTitle={(grant) =>
              `${usersDisplayLabel(users, grant.granteeUsersGuid)} · ${grant.coreKey} · ${grant.fieldName}`
            }
            emptyText={t('grant.fields.empty')}
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
        title={t('grant.fields.revokeTitle')}
        onConfirm={() => revokeFieldGrant(grantToRevoke?.id ?? 0)}
        onDeleted={loadGrants}
        onClose={() => setGrantToRevoke(null)}
      >
        <Trans
          i18nKey="grant.fields.revokeQuestion"
          values={{
            user: usersDisplayLabel(users, grantToRevoke?.granteeUsersGuid ?? ''),
            core: grantToRevoke?.coreKey ?? '',
            field: grantToRevoke?.fieldName ?? '',
          }}
          components={{ strong: <strong /> }}
        />
      </DeletePopup>
    </>
  )
}
