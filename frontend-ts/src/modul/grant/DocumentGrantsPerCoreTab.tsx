import { useEffect, useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { SolrExactResolveFieldValuesInput } from '../../components/SolrExactResolveFieldValuesInput.tsx'
import { SolrResolvedDocumentsPageTable } from '../../components/SolrResolvedDocumentsPageTable.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import {
  exactResolveFieldsOfCore,
  exactResolveFieldValuesForBackend,
  exactResolveFieldValuesFromRegisterJson,
  type SolrExactResolveField,
  type SolrExactResolveFieldValuesByFieldName,
} from '../solrmanager/SolrExactResolve.ts'
import { getAllUsers, type Users } from '../users/Users.ts'
import { useDocumentGrantPrefill } from './DocumentGrantPrefill.ts'
import {
  documentGrantAdminPathOf,
  expireDocumentGrants,
  getAllDocumentGrants,
  grantDocumentsToUsers,
  previewGrantableDocumentsPage,
  revokeDocumentGrant,
  usersDisplayLabel,
  type GrantableDocumentsPreviewPage,
  type SolrDocumentGrantedDocsPerCore,
} from './Grant.ts'
import {
  grantDateTimeOrUnlimited,
  grantEntityStateDisplayName,
  isGrantInForce,
  validUntilLocalInputToIso,
} from './grantDisplay.ts'

/** Treffer je Seite der Vorschau */
const PREVIEW_PAGE_SIZE = 25

/**
 * Tab „Dateifreigaben“: Kern wählen, exakte Feldwerte setzen wie beim Auflösen, auflösen. Die Trefferliste zeigt, was
 * der Freigebende schreiben darf, also freigeben kann. Erst dann Empfänger und Ablauf, und „Diese Treffer freigeben“
 * schreibt die guid in jeden Treffer der Bedingungen, auch auf weiteren Seiten, und legt die Registerzeile an. Nur mit
 * Bedingung und mit weniger als 500 Treffern, sonst muss ein weiterer Filter die Treffer enger fassen. Darunter das
 * Register aller Kerne, neueste zuerst, entziehen nur bei aktiven Zeilen. Gelöscht wird nichts.
 *
 * Setzen, Entziehen und Ablauf laufen je Kern über dessen eigenen Endpunkt (Vorgabe 2026-10-07): Ein Kern ohne
 * Freigabe-Service lässt sich hier nur ansehen, nicht freigeben.
 *
 * Kommt der Tab mit einer Vorbelegung (DocumentGrantPrefill im Router-State, z. B. von der Dokumentseite des DMS mit
 * Kern und id), stehen Kern und Bedingungen schon drin und das Auflösen läuft von selbst, sobald die Stammdaten da sind.
 */
export function DocumentGrantsPerCoreTab() {
  const { t, i18n } = useTranslation()
  const { solrCores, solrFields, solrHooks, entityStates } = useMasterData()
  const prefill = useDocumentGrantPrefill()
  const [grants, setGrants] = useState<SolrDocumentGrantedDocsPerCore[] | null>(null)
  const [users, setUsers] = useState<Users[] | null>(null)
  const [loadErrorMessage, setLoadErrorMessage] = useState<string | null>(null)
  const [coreKey, setCoreKey] = useState(prefill?.coreKey ?? '')
  const [fieldValuesByFieldName, setFieldValuesByFieldName] = useState<SolrExactResolveFieldValuesByFieldName>(
    prefill?.fieldValuesByFieldName ?? {},
  )
  // das Auflösen der Vorbelegung steht noch aus, bis Felder und Hooks aus den Stammdaten da sind
  const [prefillPreviewPending, setPrefillPreviewPending] = useState(prefill !== null)
  const [preview, setPreview] = useState<GrantableDocumentsPreviewPage | null>(null)
  const [granteeUsersGuid, setGranteeUsersGuid] = useState('')
  const [validUntil, setValidUntil] = useState('')
  const [busy, setBusy] = useState(false)
  const [formErrorMessage, setFormErrorMessage] = useState<string | null>(null)
  const [formInfo, setFormInfo] = useState<string | null>(null)
  const [expiryInfo, setExpiryInfo] = useState<string | null>(null)
  // Sicherheitsabfrage vor dem Entziehen: null, solange keine offen ist
  const [grantToRevoke, setGrantToRevoke] = useState<SolrDocumentGrantedDocsPerCore | null>(null)

  const unlimited = t('grant.common.unlimited')

  function loadGrants() {
    getAllDocumentGrants()
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

  // der erste Kern, sobald die Stammdaten da sind
  useEffect(() => {
    if (coreKey === '' && solrCores !== null && solrCores.length > 0) {
      setCoreKey(solrCores[0].key)
    }
  }, [coreKey, solrCores])

  const fields: SolrExactResolveField[] =
    coreKey === '' ? [] : exactResolveFieldsOfCore(solrFields ?? [], solrHooks ?? [], coreKey)
  const fieldValuesForBackend = exactResolveFieldValuesForBackend(fields, fieldValuesByFieldName)
  const hasConditions = Object.keys(fieldValuesForBackend).length > 0
  // je Kern sein eigener Freigabe-Service; ohne den gibt es hier nur das Register zu sehen
  const coreHasGrantService = coreKey !== '' && documentGrantAdminPathOf(coreKey) !== null
  // ohne Bedingung kein Auflösen: Es gäbe alles aus, das Backend lehnt es ebenso ab
  const canResolve = !busy && coreHasGrantService && hasConditions
  const canGrant = !busy && preview !== null && preview.grantAllowed && granteeUsersGuid !== ''

  function labelOf(field: SolrExactResolveField): string {
    if (!field.hook) {
      return field.name
    }
    const hook = solrHooks?.find((candidate) => candidate.key === field.name)
    const name = hook ? translate(hook.displayName) : ''
    return name !== '' ? `${name} (${field.name})` : field.name
  }

  function changeFieldValues(next: SolrExactResolveFieldValuesByFieldName) {
    setFieldValuesByFieldName(next)
    setPreview(null)
    setFormInfo(null)
  }

  function changeCore(next: string) {
    setCoreKey(next)
    setFieldValuesByFieldName({})
    setPreview(null)
    setFormInfo(null)
  }

  function runPreview(page: number) {
    setBusy(true)
    setFormErrorMessage(null)
    setFormInfo(null)
    previewGrantableDocumentsPage(coreKey, fieldValuesForBackend, page, PREVIEW_PAGE_SIZE)
      .then(({ data }) => setPreview(data))
      .catch((error: unknown) => {
        setPreview(null)
        setFormErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
      })
      .finally(() => setBusy(false))
  }

  function runGrant() {
    setBusy(true)
    setFormErrorMessage(null)
    setFormInfo(null)
    grantDocumentsToUsers(coreKey, {
      granteeUsersGuid,
      fieldValuesByFieldName: fieldValuesForBackend,
      validUntil: validUntilLocalInputToIso(validUntil),
    })
      .then(({ data }) => {
        setFormInfo(t('grant.documents.granted', { count: data.documentCount }))
        setFieldValuesByFieldName({})
        setPreview(null)
        loadGrants()
      })
      .catch((error: unknown) =>
        setFormErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError')),
      )
      .finally(() => setBusy(false))
  }

  // die Vorbelegung auflösen, einmal, sobald die Felder des Kerns bekannt sind, damit die Werte ihren Typ bekommen
  useEffect(() => {
    if (prefillPreviewPending && coreHasGrantService && solrFields !== null && solrHooks !== null && !busy) {
      setPrefillPreviewPending(false)
      runPreview(0)
    }
    // runPreview liest den Stand dieses Zeichnens, mehr Abhängigkeiten liefen ins Leere
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [prefillPreviewPending, coreHasGrantService, solrFields, solrHooks, busy])

  /** Der Ablauf der Dateifreigaben des gewählten Kerns, über dessen eigenen Endpunkt */
  function runExpiry() {
    setBusy(true)
    setExpiryInfo(null)
    expireDocumentGrants(coreKey)
      .then(({ data }) => {
        setExpiryInfo(t('grant.documents.expired', { count: data.expiredDocumentGrants, core: coreKey }))
        loadGrants()
      })
      .catch((error: unknown) =>
        setExpiryInfo(error instanceof RequestError ? error.message : t('common.unknownError')),
      )
      .finally(() => setBusy(false))
  }

  /** Warum eine Vorschau nicht freigebbar ist, oder was eine Freigabe tun würde */
  function previewVerdict(current: GrantableDocumentsPreviewPage): { text: string; className: string } {
    if (!hasConditions) {
      return { text: t('grant.documents.noConditions'), className: 'text-warning' }
    }
    if (current.totalFound === 0) {
      return { text: t('grant.documents.noHits'), className: 'text-warning' }
    }
    if (current.totalFound >= current.maxDocumentsPerGrant) {
      return {
        text: t('grant.documents.tooMany', { count: current.totalFound, limit: current.maxDocumentsPerGrant }),
        className: 'text-danger',
      }
    }
    return { text: t('grant.documents.grantableHint', { count: current.totalFound }), className: 'text-success' }
  }

  function fieldValuesOf(grant: SolrDocumentGrantedDocsPerCore) {
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

  const columns: TableColumn<SolrDocumentGrantedDocsPerCore>[] = [
    { header: t('grant.common.grantee'), cell: (grant) => usersDisplayLabel(users, grant.granteeUsersGuid) },
    { header: t('grant.common.core'), cell: (grant) => grant.coreKey },
    { header: t('grant.documents.conditions'), cell: fieldValuesOf, unimportant: true },
    { header: t('grant.documents.documents'), cell: (grant) => grant.documentCount, unimportant: true },
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
        <span>{t('grant.documents.count', { count: grants.length })}</span>
        {expiryInfo !== null && <span className="ms-3">{expiryInfo}</span>}
      </>
    )
  }

  const verdict = preview !== null ? previewVerdict(preview) : null

  const formFooter = (
    <>
      {formErrorMessage !== null && <span className="text-danger">{formErrorMessage}</span>}
      {formInfo !== null && <span className="text-success">{formInfo}</span>}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-other" disabled={!canResolve} onClick={() => runPreview(0)}>
          {t('grant.documents.resolve')}
        </button>
        <button type="button" className="button-save" disabled={!canGrant} onClick={runGrant}>
          {t('grant.documents.grantHits')}
        </button>
      </span>
    </>
  )

  return (
    <>
      <ContentBox title={t('grant.documents.newTitle')} footer={formFooter}>
        <p>{t('grant.documents.intro')}</p>
        <div className="row g-3 mb-3">
          <div className="col-12 col-md-4">
            <label className="form-label" htmlFor="document-grant-core">
              {t('grant.common.core')}
            </label>
            <select
              id="document-grant-core"
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
        </div>
        {coreKey !== '' && !coreHasGrantService && (
          <p className="text-warning">{t('grant.documents.noGrantServiceForCore', { core: coreKey })}</p>
        )}
        <p className="mb-2">
          <strong>{t('grant.documents.conditions')}</strong>
        </p>
        <SolrExactResolveFieldValuesInput
          fields={fields}
          fieldValuesByFieldName={fieldValuesByFieldName}
          onChange={changeFieldValues}
          labelOf={labelOf}
          disabled={busy || !coreHasGrantService}
        />

        {preview !== null && verdict !== null && (
          <div className="mt-3">
            <p className={`mb-2 ${verdict.className}`}>
              <strong>{verdict.text}</strong>
            </p>
            <SolrResolvedDocumentsPageTable
              coreKey={coreKey}
              resolvedPage={preview}
              readableFieldAndHookNames={preview.readableFieldAndHookNamesOfCoreForCurrentUser}
              onPage={runPreview}
              busy={busy}
            />
          </div>
        )}

        {preview !== null && preview.grantAllowed && (
          <div className="row g-3 mt-2">
            <div className="col-12 col-md-4">
              <label className="form-label" htmlFor="document-grant-user">
                {t('grant.common.grantee')}
              </label>
              <select
                id="document-grant-user"
                className="form-select form-select-sm"
                value={granteeUsersGuid}
                disabled={busy || users === null}
                onChange={(event) => setGranteeUsersGuid(event.target.value)}
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
              <label className="form-label" htmlFor="document-grant-valid-until">
                {t('grant.common.validUntil')}
              </label>
              <input
                id="document-grant-valid-until"
                type="datetime-local"
                className="form-control form-control-sm"
                value={validUntil}
                disabled={busy}
                onChange={(event) => setValidUntil(event.target.value)}
              />
              <div className="form-text">{t('grant.common.validUntilHint')}</div>
            </div>
          </div>
        )}
      </ContentBox>

      <ContentBox
        title={t('grant.documents.title')}
        actions={
          <button type="button" className="button-other" disabled={busy || !coreHasGrantService} onClick={runExpiry}>
            {t('grant.documents.expire', { core: coreKey })}
          </button>
        }
        footer={listFooter}
      >
        {grants !== null && (
          <Table
            columns={columns}
            rows={grants}
            rowKey={(grant) => String(grant.id)}
            detailTitle={(grant) => `${usersDisplayLabel(users, grant.granteeUsersGuid)} · ${grant.coreKey}`}
            emptyText={t('grant.documents.empty')}
            actions={(grant) =>
              // entziehen geht nur über den Service des Kerns, den die Zeile trägt
              isGrantInForce(grant) && documentGrantAdminPathOf(grant.coreKey) !== null ? (
                <DeleteButton label={t('grant.common.revoke')} onClick={() => setGrantToRevoke(grant)} />
              ) : null
            }
          />
        )}
      </ContentBox>

      <DeletePopup
        open={grantToRevoke !== null}
        title={t('grant.documents.revokeTitle')}
        onConfirm={() => revokeDocumentGrant(grantToRevoke?.coreKey ?? '', grantToRevoke?.id ?? 0)}
        onDeleted={loadGrants}
        onClose={() => setGrantToRevoke(null)}
      >
        <Trans
          i18nKey="grant.documents.revokeQuestion"
          values={{
            user: usersDisplayLabel(users, grantToRevoke?.granteeUsersGuid ?? ''),
            core: grantToRevoke?.coreKey ?? '',
            count: grantToRevoke?.documentCount ?? 0,
          }}
          components={{ strong: <strong /> }}
        />
      </DeletePopup>
    </>
  )
}
