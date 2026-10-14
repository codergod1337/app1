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
import { SolrHookKeysInput } from '../solrmanager/SolrHookKeysInput.tsx'
import { type FileSubClass, type FileSubClassData } from './FileSubClass.ts'
import { fileSubClassDestinationNameOf } from './FileSubClassDestination.ts'
import { FileSubClassSymbol } from './FileSubClassSymbol.tsx'

/** Startwerte einer neuen Dateiart */
const NEW_FILE_SUB_CLASS: FileSubClassData = {
  key: '',
  displayName: '',
  description: null,
  listingPosition: 0,
  symbol: null,
  color: null,
  readAccessRoleKeys: [],
  writeAccessRoleKeys: [],
  displayContextKeys: [],
  triggerWords: [],
  triggerHookKeys: [],
}

interface FileSubClassFormProps {
  /** id des form-Elements, damit ein Knopf außerhalb (z. B. im contentbox-footer) es abschicken kann */
  formId: string
  /** Startwerte beim Bearbeiten. Fehlen sie, ist das Formular leer (neue Dateiart). */
  initialFileSubClass?: FileSubClass
  /** Sprache der mehrsprachigen Felder, gewählt über die Flaggen der ContentBox */
  language: LanguageCode
  /** sperrt alle Felder, z. B. während gespeichert wird */
  disabled: boolean
  onSubmit: (fileSubClassData: FileSubClassData) => void
}

/**
 * Eingabefelder einer Dateiart, zum Anlegen und zum Bearbeiten. Beim Bearbeiten ist der key fest.
 * Schreiben schließt Lesen ein: Wer Schreiben bekommt, bekommt auch Lesen, wer Lesen verliert, verliert auch Schreiben.
 * Die Endungen stehen nicht hier, die pflegt die Freischalt-Matrix. Speichert nichts selbst: die Daten gehen an onSubmit.
 */
export function FileSubClassForm({ formId, initialFileSubClass, language, disabled, onSubmit }: FileSubClassFormProps) {
  const editing = initialFileSubClass !== undefined
  const { t } = useTranslation()
  const { accessRoles, fileSubClassDestinations } = useMasterData()
  const [fileSubClass, setFileSubClass] = useState<FileSubClassData>(() => {
    if (!initialFileSubClass) {
      return NEW_FILE_SUB_CLASS
    }
    // ohne die Endungen, sie gehören nicht zum Speichern
    const { key, displayName, description, listingPosition, symbol, color, readAccessRoleKeys, writeAccessRoleKeys, displayContextKeys, triggerWords, triggerHookKeys } =
      initialFileSubClass
    return { key, displayName, description, listingPosition, symbol, color, readAccessRoleKeys, writeAccessRoleKeys, displayContextKeys, triggerWords, triggerHookKeys }
  })

  const readAccessRoleKeys = fileSubClass.readAccessRoleKeys ?? []
  const writeAccessRoleKeys = fileSubClass.writeAccessRoleKeys ?? []
  const displayContextKeys = fileSubClass.displayContextKeys ?? []

  /** Einen Anzeigeort an- oder abhaken; die Orte kommen aus den Stammdaten, das Backend lehnt jeden anderen Key ab */
  function toggleDisplayContextKey(destinationKey: string, checked: boolean) {
    change(
      'displayContextKeys',
      checked ? [...displayContextKeys.filter((key) => key !== destinationKey), destinationKey] : displayContextKeys.filter((key) => key !== destinationKey),
    )
  }

  function change<K extends keyof FileSubClassData>(field: K, value: FileSubClassData[K]) {
    setFileSubClass((current) => ({ ...current, [field]: value }))
  }

  /** Wer Lesen verliert, verliert auch Schreiben */
  function changeReadAccessRoleKeys(newReadAccessRoleKeys: string[]) {
    setFileSubClass((current) => ({
      ...current,
      readAccessRoleKeys: newReadAccessRoleKeys,
      writeAccessRoleKeys: (current.writeAccessRoleKeys ?? []).filter((key) => newReadAccessRoleKeys.includes(key)),
    }))
  }

  /** Wer Schreiben bekommt, bekommt auch Lesen */
  function changeWriteAccessRoleKeys(newWriteAccessRoleKeys: string[]) {
    setFileSubClass((current) => {
      const currentReadAccessRoleKeys = current.readAccessRoleKeys ?? []
      return {
        ...current,
        writeAccessRoleKeys: newWriteAccessRoleKeys,
        readAccessRoleKeys: [
          ...currentReadAccessRoleKeys,
          ...newWriteAccessRoleKeys.filter((key) => !currentReadAccessRoleKeys.includes(key)),
        ],
      }
    })
  }

  function submitFileSubClass(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit(fileSubClass)
  }

  return (
    <form id={formId} onSubmit={submitFileSubClass}>
      <fieldset className="row g-3" disabled={disabled}>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-key`}>
            Key
          </label>
          <KeyInput
            id={`${formId}-key`}
            required
            // der key ändert sich nie, sonst zeigten alle Dateien ins Leere
            disabled={disabled || editing}
            value={fileSubClass.key}
            onChange={(key) => change('key', key)}
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-display-name`}>
            Anzeigename
          </label>
          <MultilingualInput
            id={`${formId}-display-name`}
            required
            disabled={disabled}
            language={language}
            value={fileSubClass.displayName || null}
            onChange={(displayName) => change('displayName', displayName ?? '')}
          />
        </div>
        <div className="col-12">
          <label className="form-label" htmlFor={`${formId}-description`}>
            Beschreibung
          </label>
          <MultilingualInput
            id={`${formId}-description`}
            disabled={disabled}
            language={language}
            value={fileSubClass.description}
            onChange={(description) => change('description', description)}
          />
        </div>
        <div className="col-md-3">
          <label className="form-label" htmlFor={`${formId}-position`}>
            Position
          </label>
          <input
            id={`${formId}-position`}
            className="form-control"
            type="number"
            value={fileSubClass.listingPosition}
            onChange={(event) => change('listingPosition', Number(event.target.value))}
          />
        </div>
        <div className="col-md-9">
          <span className="form-label d-block">Vorschau</span>
          <span className="d-inline-flex align-items-center gap-2">
            <FileSubClassSymbol fileSubClass={{ ...fileSubClass, key: fileSubClass.key || 'KEY' }} />
            <span>{translate(fileSubClass.displayName || null, language) || 'Anzeigename'}</span>
          </span>
        </div>
        <div className="col-md-6">
          <span className="form-label d-block">Symbol</span>
          <SymbolPicker value={fileSubClass.symbol} disabled={disabled} onChange={(symbol) => change('symbol', symbol)} />
        </div>
        <div className="col-md-6">
          <ColorField
            id={`${formId}-color`}
            label="Farbe des Symbols"
            value={fileSubClass.color}
            disabled={disabled}
            onChange={(color) => change('color', color)}
          />
        </div>
        <div className="col-12">
          <span className="form-label d-block">{t('filesubclass.form.displayContextKeys')}</span>
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
          <div className="form-text">{t('filesubclass.form.displayContextKeysHint')}</div>
        </div>
        <div className="col-12">
          <span className="form-label d-block">{t('filesubclass.form.triggerWords')}</span>
          <ValuesInput
            values={fileSubClass.triggerWords ?? []}
            disabled={disabled}
            placeholder={t('filesubclass.form.triggerWordsPlaceholder')}
            ariaLabel={t('filesubclass.form.triggerWords')}
            onChange={(triggerWords) => change('triggerWords', triggerWords)}
          />
          <div className="form-text">{t('filesubclass.form.triggerWordsHint')}</div>
        </div>
        <div className="col-12">
          <span className="form-label d-block">{t('filesubclass.form.triggerHookKeys')}</span>
          <SolrHookKeysInput
            value={fileSubClass.triggerHookKeys ?? []}
            language={language}
            disabled={disabled}
            onChange={(triggerHookKeys) => change('triggerHookKeys', triggerHookKeys)}
          />
          <div className="form-text">{t('filesubclass.form.triggerHookKeysHint')}</div>
        </div>
        <div className="col-12">
          <span className="form-label d-block">Lesen</span>
          {accessRoles === null ? (
            <span className="spinner-border spinner-border-sm d-block" role="status" aria-label="lädt" />
          ) : (
            <BadgeToggleList
              badges={accessRoles}
              selectedKeys={readAccessRoleKeys}
              shape="rect"
              disabled={disabled}
              onChange={changeReadAccessRoleKeys}
            />
          )}
        </div>
        <div className="col-12">
          <span className="form-label d-block">Schreiben (schließt Lesen ein)</span>
          {accessRoles === null ? (
            <span className="spinner-border spinner-border-sm d-block" role="status" aria-label="lädt" />
          ) : (
            <BadgeToggleList
              badges={accessRoles}
              selectedKeys={writeAccessRoleKeys}
              shape="rect"
              disabled={disabled}
              onChange={changeWriteAccessRoleKeys}
            />
          )}
        </div>
      </fieldset>
    </form>
  )
}
