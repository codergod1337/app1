import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useMasterData } from '../../components/masterDataContext.ts'
import { BadgeToggleList } from '../accessrole/BadgeToggleList.tsx'
import type { Mailbox } from './Mailbox.ts'

/** Startwerte eines neuen Postfachs */
const NEW_MAILBOX: Mailbox = {
  emailAddress: '',
  displayName: '',
  description: null,
  listingPosition: 0,
  accessRoleCollectionKeys: [],
}

interface MailboxFormProps {
  /** id des form-Elements, damit ein Knopf außerhalb (z. B. im contentbox-footer) es abschicken kann */
  formId: string
  /** Startwerte beim Bearbeiten. Fehlen sie, ist das Formular leer (neues Postfach). */
  initialMailbox?: Mailbox
  /** sperrt alle Felder, z. B. während gespeichert wird */
  disabled: boolean
  onSubmit: (mailboxData: Mailbox) => void
}

/**
 * Eingabefelder eines Postfachs (User 2026-10-13, nach der MailboxBearbeitenSeite der Vorlage), zum Anlegen und
 * Bearbeiten. Beim Bearbeiten ist die Adresse fest. Name und Beschreibung sind Klartext, ein Postfach heißt in jeder
 * Sprache gleich. Die ARC-Positionen als Pillen: Sie dürfen aus dem Postfach archivieren, wer eine archivierte Nachricht
 * lesen darf, entscheidet ihre Nachrichtenart. Speichert nichts selbst.
 */
export function MailboxForm({ formId, initialMailbox, disabled, onSubmit }: MailboxFormProps) {
  const editing = initialMailbox !== undefined
  const { t } = useTranslation()
  const { accessRoleCollections } = useMasterData()
  const [mailbox, setMailbox] = useState<Mailbox>(initialMailbox ?? NEW_MAILBOX)

  function change<K extends keyof Mailbox>(field: K, value: Mailbox[K]) {
    setMailbox((current) => ({ ...current, [field]: value }))
  }

  function submitMailbox(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit(mailbox)
  }

  return (
    <form id={formId} onSubmit={submitMailbox}>
      <fieldset className="row g-3" disabled={disabled}>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-email-address`}>
            {t('mailbox.form.emailAddress')}
          </label>
          <input
            id={`${formId}-email-address`}
            className="form-control"
            // die Form prüft das Backend (genau ein @, kein Leerraum), nicht der Browser
            type="text"
            inputMode="email"
            required
            // die Adresse ist der Schlüssel und ändert sich nie
            disabled={disabled || editing}
            value={mailbox.emailAddress}
            onChange={(event) => change('emailAddress', event.target.value)}
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-display-name`}>
            {t('common.displayName')}
          </label>
          <input
            id={`${formId}-display-name`}
            className="form-control"
            required
            maxLength={200}
            value={mailbox.displayName}
            onChange={(event) => change('displayName', event.target.value)}
          />
        </div>
        <div className="col-12">
          <label className="form-label" htmlFor={`${formId}-description`}>
            {t('common.description')}
          </label>
          <textarea
            id={`${formId}-description`}
            className="form-control"
            rows={2}
            value={mailbox.description ?? ''}
            onChange={(event) => change('description', event.target.value === '' ? null : event.target.value)}
          />
        </div>
        <div className="col-md-3">
          <label className="form-label" htmlFor={`${formId}-position`}>
            {t('common.position')}
          </label>
          <input
            id={`${formId}-position`}
            className="form-control"
            type="number"
            value={mailbox.listingPosition}
            onChange={(event) => change('listingPosition', Number(event.target.value))}
          />
        </div>
        <div className="col-12">
          <span className="form-label d-block">{t('mailbox.form.accessRoleCollectionKeys')}</span>
          {accessRoleCollections === null ? (
            <span className="spinner-border spinner-border-sm d-block" role="status" aria-label={t('common.loading')} />
          ) : (
            <BadgeToggleList
              badges={accessRoleCollections}
              selectedKeys={mailbox.accessRoleCollectionKeys ?? []}
              shape="pill"
              disabled={disabled}
              onChange={(keys) => change('accessRoleCollectionKeys', keys)}
            />
          )}
          <div className="form-text">{t('mailbox.form.accessRoleCollectionKeysHint')}</div>
        </div>
      </fieldset>
    </form>
  )
}
