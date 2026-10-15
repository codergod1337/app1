import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { Popup } from '../../components/Popup.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { showToast } from '../../components/toastStore.ts'
import { UsersGuidName } from '../../components/UsersGuidName.tsx'
import { UsersPicker } from '../../components/UsersPicker.tsx'
import { dmsFieldLabel, getCurrentUserDmsAccess } from './Dms.ts'
import {
  createDmsShareToken,
  dmsShareUrlOf,
  getDmsUserGrants,
  grantDmsDocumentToUser,
  localDateTimeInputToIso,
  revokeDmsDocumentFromUser,
  type DmsShareTokenKind,
  type DmsUserGrant,
} from './DmsShare.ts'

/** So lange gilt ein neuer Schein, wenn niemand etwas anderes einstellt */
const DEFAULT_VALID_DAYS = 7
/** So viele Einlösungen hat ein neuer Schein, wenn niemand etwas anderes einstellt */
const DEFAULT_MAX_USES = 20

/** Ein Zeitpunkt als Wert für datetime-local: Ortszeit ohne Zone, auf die Minute */
function localInputValue(date: Date): string {
  const pad = (number: number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function defaultExpiresLocal(): string {
  const date = new Date()
  date.setDate(date.getDate() + DEFAULT_VALID_DAYS)
  return localInputValue(date)
}

interface DmsSharePopupProps {
  open: boolean
  id: string
  fileName: string
  onClose: () => void
}

/**
 * Das Freigabe-Popup der Dokumentseite (User 2026-10-10), nur für ADMIN und DMS_PUBLIC, das Backend prüft das selbst:
 * Ein Abholschein ist ein Link an jemanden ohne Konto. Oben die Wahl, was der Link freigibt: nur den Download (Art
 * BINARY, die Felder für den Download holt das Backend selbst) oder das Dokument mit Metadaten (Art DOCUMENT, alle
 * Felder, die der Freigebende lesen darf, oder eine Auswahl, der Download dazu wählbar). Dazu gültig bis und wie viele
 * Einlösungen frei sind. Nach dem Erstellen steht der Link genau einmal mit Kopierknopf: Gespeichert ist nur sein
 * Hash. Darunter alle Scheine dieses Dokuments mit Stand, Einlösungen und dem Knopf zum Ablaufen lassen.
 */
export function DmsSharePopup({ open, id, fileName, onClose }: DmsSharePopupProps) {
  const { t, i18n } = useTranslation()
  const { language } = useLanguage()
  const { solrFields, solrHooks } = useMasterData()
  const [readableFieldNames, setReadableFieldNames] = useState<string[]>([])
  const [kind, setKind] = useState<DmsShareTokenKind>('BINARY')
  const [wholeDocument, setWholeDocument] = useState(true)
  const [chosenFieldNames, setChosenFieldNames] = useState<Set<string>>(() => new Set())
  const [binaryIncluded, setBinaryIncluded] = useState(true)
  const [recipient, setRecipient] = useState('')
  const [expiresLocal, setExpiresLocal] = useState(defaultExpiresLocal)
  // Vorgabe 20 Einlösungen (User 2026-10-10): Ansicht und Download zählen beide, eine wäre nach dem ersten Öffnen weg
  const [maxUses, setMaxUses] = useState(DEFAULT_MAX_USES)
  const [creating, setCreating] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  /** der Klartext des eben erstellten Scheins, nur bis das Popup schließt */
  const [created, setCreated] = useState<{ kind: DmsShareTokenKind; token: string } | null>(null)
  /** die User-Freigaben dieses Dokuments aus dem Register: wer, wann, wem, auch zurückgenommene */
  const [userGrants, setUserGrants] = useState<DmsUserGrant[] | null>(null)
  const [grantedError, setGrantedError] = useState<string | null>(null)
  const [granting, setGranting] = useState(false)
  const [grantError, setGrantError] = useState<string | null>(null)

  function loadGranted() {
    setGrantedError(null)
    getDmsUserGrants(id)
      .then(({ data }) => setUserGrants(data))
      .catch((error: unknown) => {
        setUserGrants([])
        setGrantedError(error instanceof RequestError ? error.message : t('common.unknownError'))
      })
  }

  function grant(guid: string) {
    setGranting(true)
    setGrantError(null)
    grantDmsDocumentToUser(id, guid)
      .then(loadGranted)
      .catch((error: unknown) => setGrantError(error instanceof RequestError ? error.message : t('common.unknownError')))
      .finally(() => setGranting(false))
  }

  function revoke(guid: string) {
    setGranting(true)
    revokeDmsDocumentFromUser(id, guid)
      .then(loadGranted)
      .catch((error: unknown) =>
        showToast({
          kind: 'error',
          title: t('dms.share.user.revoke'),
          text: error instanceof RequestError ? error.message : t('common.unknownError'),
        }),
      )
      .finally(() => setGranting(false))
  }

  useEffect(() => {
    if (!open) {
      return
    }
    setCreated(null)
    setFormError(null)
    setGrantError(null)
    loadGranted()
    getCurrentUserDmsAccess()
      .then(({ data }) => setReadableFieldNames(data.readableFieldAndHookNamesOfCoreForCurrentUser))
      .catch(() => setReadableFieldNames([]))
    // neu laden, wenn das Popup aufgeht oder ein anderes Dokument zeigt
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, id])

  function labelOf(fieldName: string): string {
    const hook = solrHooks?.find((candidate) => candidate.key === fieldName)
    if (hook) {
      return translate(hook.displayName, language) || hook.key
    }
    return dmsFieldLabel(solrFields, fieldName, fieldName, language)
  }

  function toggleField(fieldName: string) {
    setChosenFieldNames((current) => {
      const next = new Set(current)
      if (next.has(fieldName)) {
        next.delete(fieldName)
      } else {
        next.add(fieldName)
      }
      return next
    })
  }

  function create() {
    const expiresAt = localDateTimeInputToIso(expiresLocal)
    if (expiresAt === null) {
      setFormError(t('dms.share.expiresAtMissing'))
      return
    }
    const includedFieldNames = kind === 'BINARY' ? [] : wholeDocument ? readableFieldNames : [...chosenFieldNames]
    if (kind === 'DOCUMENT' && includedFieldNames.length === 0) {
      setFormError(t('dms.share.noFieldChosen'))
      return
    }
    setCreating(true)
    setFormError(null)
    createDmsShareToken({
      id,
      kind,
      includedFieldNames,
      binaryIncluded: kind === 'BINARY' || binaryIncluded,
      recipient: recipient.trim() === '' ? null : recipient.trim(),
      maxUses,
      expiresAt,
    })
      .then(({ data }) => setCreated({ kind: data.shareToken.kind, token: data.token }))
      .catch((error: unknown) =>
        setFormError(error instanceof RequestError ? error.message : t('common.unknownError')),
      )
      .finally(() => setCreating(false))
  }

  function copyLink(url: string) {
    navigator.clipboard
      .writeText(url)
      .then(() => showToast({ kind: 'info', title: t('dms.share.copied'), text: url }))
      .catch(() => showToast({ kind: 'error', title: t('common.unknownError'), text: url }))
  }

  function dateTime(value: string): string {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString(i18n.language)
  }

  const userGrantColumns: TableColumn<DmsUserGrant>[] = [
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

  const createdUrl = created === null ? null : dmsShareUrlOf(created.kind, created.token)

  return (
    <Popup open={open} title={t('dms.share.title', { name: fileName })} onClose={onClose}>
      <div className="d-flex flex-column gap-3">
        {/* Freigabe an User per guid (User 2026-10-10): die guid kommt in userGrantedAccess, der User sieht das Dokument
            danach wie über die readAcl und findet es unter „Meine Freigaben“ */}
        <div className="fw-bold">
          {t('dms.share.user.title', { count: userGrants?.filter((grant) => grant.status === 'ACTIVE').length ?? 0 })}
        </div>
        {grantedError !== null && <p className="text-danger mb-0">{grantedError}</p>}
        {userGrants !== null && userGrants.length === 0 && grantedError === null && (
          <p className="mb-0 text-muted">{t('dms.share.user.empty')}</p>
        )}
        {userGrants !== null && userGrants.length > 0 && (
          <Table
            columns={userGrantColumns}
            rows={userGrants}
            rowKey={(row) => String(row.id)}
            detailTitle={(row) => dateTime(row.createdAt)}
            emptyText={t('dms.share.user.empty')}
            actions={(row) =>
              row.status === 'ACTIVE' ? (
                <button
                  type="button"
                  className="button-other"
                  disabled={granting}
                  aria-label={t('dms.share.user.revokeOf', { guid: row.granteeUsersGuid })}
                  onClick={() => revoke(row.granteeUsersGuid)}
                >
                  {t('dms.share.user.revoke')}
                </button>
              ) : null
            }
            actionsWidth="9rem"
          />
        )}
        <div>
          <label className="form-label small mb-1" htmlFor="dms-share-user-email">
            {t('dms.share.user.emailLabel')}
          </label>
          <UsersPicker
            id="dms-share-user-email"
            disabled={granting}
            excludedGuids={userGrants?.filter((grant) => grant.status === 'ACTIVE').map((grant) => grant.granteeUsersGuid) ?? []}
            onPick={(users) => grant(users.guid)}
          />
          {grantError !== null && <div className="text-danger small">{grantError}</div>}
        </div>

        <hr className="dms-section-divider" />

        <div className="fw-bold">{t('dms.share.newTitle')}</div>
        <fieldset className="d-flex flex-column gap-2">
          <legend className="fs-6 mb-0">{t('dms.share.kindLabel')}</legend>
          <div className="form-check">
            <input
              id="dms-share-kind-binary"
              className="form-check-input"
              type="radio"
              checked={kind === 'BINARY'}
              disabled={creating}
              onChange={() => setKind('BINARY')}
            />
            <label className="form-check-label" htmlFor="dms-share-kind-binary">
              {t('dms.share.kindBinary')} <span className="text-muted small">{t('dms.share.kindBinaryHint')}</span>
            </label>
          </div>
          <div className="form-check">
            <input
              id="dms-share-kind-document"
              className="form-check-input"
              type="radio"
              checked={kind === 'DOCUMENT'}
              disabled={creating}
              onChange={() => setKind('DOCUMENT')}
            />
            <label className="form-check-label" htmlFor="dms-share-kind-document">
              {t('dms.share.kindDocument')} <span className="text-muted small">{t('dms.share.kindDocumentHint')}</span>
            </label>
          </div>
        </fieldset>

        {kind === 'DOCUMENT' && (
          <div className="ms-4 d-flex flex-column gap-2">
            <div className="form-check">
              <input
                id="dms-share-whole"
                className="form-check-input"
                type="radio"
                checked={wholeDocument}
                disabled={creating}
                onChange={() => setWholeDocument(true)}
              />
              <label className="form-check-label" htmlFor="dms-share-whole">
                {t('dms.share.wholeDocument', { count: readableFieldNames.length })}
              </label>
            </div>
            <div className="form-check">
              <input
                id="dms-share-chosen"
                className="form-check-input"
                type="radio"
                checked={!wholeDocument}
                disabled={creating}
                onChange={() => setWholeDocument(false)}
              />
              <label className="form-check-label" htmlFor="dms-share-chosen">
                {t('dms.share.chooseFields')}
              </label>
            </div>
            {!wholeDocument && (
              <div className="ms-4 d-flex flex-wrap gap-2">
                {readableFieldNames.map((fieldName) => (
                  <div key={fieldName} className="form-check">
                    <input
                      id={`dms-share-field-${fieldName}`}
                      className="form-check-input"
                      type="checkbox"
                      checked={chosenFieldNames.has(fieldName)}
                      disabled={creating}
                      onChange={() => toggleField(fieldName)}
                    />
                    <label className="form-check-label" htmlFor={`dms-share-field-${fieldName}`}>
                      {labelOf(fieldName)}
                    </label>
                  </div>
                ))}
              </div>
            )}
            <div className="form-check">
              <input
                id="dms-share-binary-included"
                className="form-check-input"
                type="checkbox"
                checked={binaryIncluded}
                disabled={creating}
                onChange={(event) => setBinaryIncluded(event.target.checked)}
              />
              <label className="form-check-label" htmlFor="dms-share-binary-included">
                {t('dms.share.binaryIncluded')}
              </label>
            </div>
          </div>
        )}

        <div className="row g-2">
          <div className="col-12 col-md-4">
            <label className="form-label small mb-1" htmlFor="dms-share-expires">
              {t('dms.share.expiresAt')}
            </label>
            <input
              id="dms-share-expires"
              className="form-control form-control-sm"
              type="datetime-local"
              value={expiresLocal}
              disabled={creating}
              onChange={(event) => setExpiresLocal(event.target.value)}
            />
          </div>
          <div className="col-12 col-md-3">
            <label className="form-label small mb-1" htmlFor="dms-share-max-uses">
              {t('dms.share.maxUses')}
            </label>
            <input
              id="dms-share-max-uses"
              className="form-control form-control-sm"
              type="number"
              min={1}
              value={maxUses}
              disabled={creating}
              onChange={(event) => setMaxUses(Math.max(1, Number(event.target.value) || 1))}
            />
            <div className="form-text">{t('dms.share.maxUsesHint')}</div>
          </div>
          <div className="col-12 col-md-5">
            <label className="form-label small mb-1" htmlFor="dms-share-recipient">
              {t('dms.share.recipient')}
            </label>
            <input
              id="dms-share-recipient"
              className="form-control form-control-sm"
              type="text"
              maxLength={200}
              value={recipient}
              disabled={creating}
              onChange={(event) => setRecipient(event.target.value)}
            />
          </div>
        </div>

        <div className="d-flex align-items-center gap-2">
          <button type="button" className="button-save" disabled={creating} onClick={create}>
            <Icon name="share" /> {t('dms.share.create')}
          </button>
          {formError !== null && <span className="text-danger">{formError}</span>}
        </div>

        {createdUrl !== null && (
          <div className="dms-share-created">
            <div className="fw-bold">{t('dms.share.createdTitle')}</div>
            <div className="small text-muted">{t('dms.share.createdHint')}</div>
            <div className="d-flex align-items-center gap-2 mt-1">
              <code className="text-break">{createdUrl}</code>
              <button
                type="button"
                className="button-other"
                aria-label={t('dms.share.copy')}
                onClick={() => copyLink(createdUrl)}
              >
                <Icon name="copy" />
              </button>
            </div>
          </div>
        )}
      </div>
    </Popup>
  )
}
