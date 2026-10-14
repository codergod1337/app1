import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { ColorField } from '../../components/ColorField.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { KeyInput } from '../../components/KeyInput.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { MultilingualInput } from '../../components/MultilingualInput.tsx'
import { SymbolPicker } from '../../components/SymbolPicker.tsx'
import { ValuesInput } from '../../components/ValuesInput.tsx'
import { BadgeToggleList } from '../accessrole/BadgeToggleList.tsx'
import { fileSubClassDestinationNameOf } from '../filesubclass/FileSubClassDestination.ts'
import { FileSubClassSymbol } from '../filesubclass/FileSubClassSymbol.tsx'
import { SolrHookKeysInput } from '../solrmanager/SolrHookKeysInput.tsx'
import type { MessageSubClass } from './MessageSubClass.ts'

/** Startwerte einer neuen Nachrichtenart */
const NEW_MESSAGE_SUB_CLASS: MessageSubClass = {
  key: '',
  displayName: '',
  description: null,
  listingPosition: 0,
  symbol: null,
  color: null,
  readAccessRoleKeys: [],
  writeAccessRoleKeys: [],
  displayContextKeys: [],
  mailboxEmailAddresses: [],
  triggerWords: [],
  triggerHookKeys: [],
}

interface MessageSubClassFormProps {
  /** id des form-Elements, damit ein Knopf außerhalb (z. B. im contentbox-footer) es abschicken kann */
  formId: string
  /** Startwerte beim Bearbeiten. Fehlen sie, ist das Formular leer (neue Nachrichtenart). */
  initialMessageSubClass?: MessageSubClass
  /** Sprache der mehrsprachigen Felder, gewählt über die Flaggen der ContentBox */
  language: LanguageCode
  /** sperrt alle Felder, z. B. während gespeichert wird */
  disabled: boolean
  onSubmit: (messageSubClassData: MessageSubClass) => void
}

/**
 * Eingabefelder einer Nachrichtenart (User 2026-10-13), nach dem Formular der Dateiart, zum Anlegen und Bearbeiten.
 * Beim Bearbeiten ist der key fest. Hookpflege schließt Lesen ein: Wer sie bekommt, bekommt auch Lesen, wer Lesen
 * verliert, verliert auch die Hookpflege. Die Anzeigeorte kommen aus dem Katalog der Dateiarten, die Postfachadressen
 * sind frei, das Backend prüft ihre Form und dass keine an zwei Arten hängt. Speichert nichts selbst.
 */
export function MessageSubClassForm({ formId, initialMessageSubClass, language, disabled, onSubmit }: MessageSubClassFormProps) {
  const editing = initialMessageSubClass !== undefined
  const { t } = useTranslation()
  const { accessRoles, fileSubClassDestinations } = useMasterData()
  const [messageSubClass, setMessageSubClass] = useState<MessageSubClass>(initialMessageSubClass ?? NEW_MESSAGE_SUB_CLASS)

  const readAccessRoleKeys = messageSubClass.readAccessRoleKeys ?? []
  const writeAccessRoleKeys = messageSubClass.writeAccessRoleKeys ?? []
  const displayContextKeys = messageSubClass.displayContextKeys ?? []

  function change<K extends keyof MessageSubClass>(field: K, value: MessageSubClass[K]) {
    setMessageSubClass((current) => ({ ...current, [field]: value }))
  }

  /** Einen Anzeigeort an- oder abhaken; die Orte kommen aus den Stammdaten, das Backend lehnt jeden anderen Key ab */
  function toggleDisplayContextKey(destinationKey: string, checked: boolean) {
    change(
      'displayContextKeys',
      checked ? [...displayContextKeys.filter((key) => key !== destinationKey), destinationKey] : displayContextKeys.filter((key) => key !== destinationKey),
    )
  }

  /** Wer Lesen verliert, verliert auch die Hookpflege */
  function changeReadAccessRoleKeys(newReadAccessRoleKeys: string[]) {
    setMessageSubClass((current) => ({
      ...current,
      readAccessRoleKeys: newReadAccessRoleKeys,
      writeAccessRoleKeys: (current.writeAccessRoleKeys ?? []).filter((key) => newReadAccessRoleKeys.includes(key)),
    }))
  }

  /** Wer die Hookpflege bekommt, bekommt auch Lesen */
  function changeWriteAccessRoleKeys(newWriteAccessRoleKeys: string[]) {
    setMessageSubClass((current) => {
      const currentReadAccessRoleKeys = current.readAccessRoleKeys ?? []
      return {
        ...current,
        writeAccessRoleKeys: newWriteAccessRoleKeys,
        readAccessRoleKeys: [...currentReadAccessRoleKeys, ...newWriteAccessRoleKeys.filter((key) => !currentReadAccessRoleKeys.includes(key))],
      }
    })
  }

  function submitMessageSubClass(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit(messageSubClass)
  }

  return (
    <form id={formId} onSubmit={submitMessageSubClass}>
      <fieldset className="row g-3" disabled={disabled}>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-key`}>
            {t('messagesubclass.form.key')}
          </label>
          <KeyInput
            id={`${formId}-key`}
            required
            // der key ändert sich nie, sonst zeigten alle Nachrichten ins Leere
            disabled={disabled || editing}
            value={messageSubClass.key}
            onChange={(key) => change('key', key)}
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-display-name`}>
            {t('common.displayName')}
          </label>
          <MultilingualInput
            id={`${formId}-display-name`}
            required
            disabled={disabled}
            language={language}
            value={messageSubClass.displayName || null}
            onChange={(displayName) => change('displayName', displayName ?? '')}
          />
        </div>
        <div className="col-12">
          <label className="form-label" htmlFor={`${formId}-description`}>
            {t('common.description')}
          </label>
          <MultilingualInput
            id={`${formId}-description`}
            disabled={disabled}
            language={language}
            value={messageSubClass.description}
            onChange={(description) => change('description', description)}
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
            value={messageSubClass.listingPosition}
            onChange={(event) => change('listingPosition', Number(event.target.value))}
          />
        </div>
        <div className="col-md-9">
          <span className="form-label d-block">{t('common.preview')}</span>
          <span className="d-inline-flex align-items-center gap-2">
            <FileSubClassSymbol fileSubClass={{ ...messageSubClass, key: messageSubClass.key || 'KEY' }} />
            <span>{translate(messageSubClass.displayName || null, language) || t('common.displayName')}</span>
          </span>
        </div>
        <div className="col-md-6">
          <span className="form-label d-block">{t('messagesubclass.form.symbol')}</span>
          <SymbolPicker value={messageSubClass.symbol} disabled={disabled} onChange={(symbol) => change('symbol', symbol)} />
        </div>
        <div className="col-md-6">
          <ColorField
            id={`${formId}-color`}
            label={t('messagesubclass.form.color')}
            value={messageSubClass.color}
            disabled={disabled}
            onChange={(color) => change('color', color)}
          />
        </div>
        <div className="col-12">
          <span className="form-label d-block">{t('messagesubclass.form.displayContextKeys')}</span>
          {fileSubClassDestinations === null ? (
            <span className="spinner-border spinner-border-sm d-block" role="status" aria-label={t('common.loading')} />
          ) : fileSubClassDestinations.length === 0 ? (
            <span className="text-body-secondary small">{t('filesubclass.form.noDestinations')}</span>
          ) : (
            <div className="d-flex flex-wrap gap-3">
              {fileSubClassDestinations.map((destination) => (
                <Hoverlay key={destination.key} text={translate(destination.description, language)}>
                  <label className="form-check form-check-inline mb-0">
                    <input
                      className="form-check-input"
                      type="checkbox"
                      disabled={disabled}
                      checked={displayContextKeys.includes(destination.key)}
                      onChange={(event) => toggleDisplayContextKey(destination.key, event.target.checked)}
                    />
                    <span className="form-check-label">
                      {fileSubClassDestinationNameOf(destination, language)} <code className="small">{destination.key}</code>
                    </span>
                  </label>
                </Hoverlay>
              ))}
            </div>
          )}
          <div className="form-text">{t('messagesubclass.form.displayContextKeysHint')}</div>
        </div>
        <div className="col-12">
          <span className="form-label d-block">{t('messagesubclass.form.mailboxEmailAddresses')}</span>
          <ValuesInput
            values={messageSubClass.mailboxEmailAddresses ?? []}
            disabled={disabled}
            placeholder={t('messagesubclass.form.mailboxEmailAddressesPlaceholder')}
            ariaLabel={t('messagesubclass.form.mailboxEmailAddresses')}
            onChange={(addresses) => change('mailboxEmailAddresses', addresses)}
          />
          <div className="form-text">{t('messagesubclass.form.mailboxEmailAddressesHint')}</div>
        </div>
        <div className="col-12">
          <span className="form-label d-block">{t('messagesubclass.form.triggerWords')}</span>
          <ValuesInput
            values={messageSubClass.triggerWords ?? []}
            disabled={disabled}
            placeholder={t('messagesubclass.form.triggerWordsPlaceholder')}
            ariaLabel={t('messagesubclass.form.triggerWords')}
            onChange={(triggerWords) => change('triggerWords', triggerWords)}
          />
        </div>
        <div className="col-12">
          <span className="form-label d-block">{t('messagesubclass.form.triggerHookKeys')}</span>
          <SolrHookKeysInput
            value={messageSubClass.triggerHookKeys ?? []}
            language={language}
            disabled={disabled}
            onChange={(triggerHookKeys) => change('triggerHookKeys', triggerHookKeys)}
          />
          <div className="form-text">{t('messagesubclass.form.triggerHint')}</div>
        </div>
        <div className="col-12">
          <span className="form-label d-block">{t('messagesubclass.form.read')}</span>
          {accessRoles === null ? (
            <span className="spinner-border spinner-border-sm d-block" role="status" aria-label={t('common.loading')} />
          ) : (
            <BadgeToggleList badges={accessRoles} selectedKeys={readAccessRoleKeys} shape="rect" disabled={disabled} onChange={changeReadAccessRoleKeys} />
          )}
        </div>
        <div className="col-12">
          <span className="form-label d-block">{t('messagesubclass.form.write')}</span>
          {accessRoles === null ? (
            <span className="spinner-border spinner-border-sm d-block" role="status" aria-label={t('common.loading')} />
          ) : (
            <BadgeToggleList badges={accessRoles} selectedKeys={writeAccessRoleKeys} shape="rect" disabled={disabled} onChange={changeWriteAccessRoleKeys} />
          )}
        </div>
      </fieldset>
    </form>
  )
}
