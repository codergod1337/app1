import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { KeyInput } from '../../components/KeyInput.tsx'
import { MultilingualInput } from '../../components/MultilingualInput.tsx'
import type { FileSubClassDestination } from './FileSubClassDestination.ts'

/** Startwerte eines neuen Anzeigeorts */
const NEW_FILE_SUB_CLASS_DESTINATION: FileSubClassDestination = {
  key: '',
  displayName: '',
  description: null,
  listingPosition: 0,
}

interface FileSubClassDestinationFormProps {
  /** id des form-Elements, damit ein Knopf außerhalb (z. B. im contentbox-footer) es abschicken kann */
  formId: string
  /** Startwerte beim Bearbeiten. Fehlen sie, ist das Formular leer (neuer Ort). */
  initialFileSubClassDestination?: FileSubClassDestination
  /** Sprache der mehrsprachigen Felder, gewählt über die Flaggen der ContentBox */
  language: LanguageCode
  /** sperrt alle Felder, z. B. während gespeichert wird */
  disabled: boolean
  onSubmit: (fileSubClassDestinationData: FileSubClassDestination) => void
}

/**
 * Eingabefelder eines Anzeigeorts, zum Anlegen und zum Bearbeiten. Beim Bearbeiten ist der key fest, Dateiarten und
 * Aufrufe tragen ihn. Speichert nichts selbst: die Daten gehen an onSubmit.
 */
export function FileSubClassDestinationForm({ formId, initialFileSubClassDestination, language, disabled, onSubmit }: FileSubClassDestinationFormProps) {
  const { t } = useTranslation()
  const editing = initialFileSubClassDestination !== undefined
  const [destination, setDestination] = useState<FileSubClassDestination>(initialFileSubClassDestination ?? NEW_FILE_SUB_CLASS_DESTINATION)

  function change<K extends keyof FileSubClassDestination>(field: K, value: FileSubClassDestination[K]) {
    setDestination((current) => ({ ...current, [field]: value }))
  }

  function submitDestination(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit(destination)
  }

  return (
    <form id={formId} onSubmit={submitDestination}>
      <fieldset className="row g-3" disabled={disabled}>
        <div className="col-md-3">
          <label className="form-label" htmlFor={`${formId}-key`}>
            {t('common.key')}
          </label>
          <KeyInput
            id={`${formId}-key`}
            required
            // der key ändert sich nie, Dateiarten und Aufrufe tragen ihn
            disabled={disabled || editing}
            value={destination.key}
            onChange={(key) => change('key', key)}
          />
          <div className="form-text">{t('filesubclassdestination.keyHint')}</div>
        </div>
        <div className="col-md-4">
          <label className="form-label" htmlFor={`${formId}-display-name`}>
            {t('common.displayName')}
          </label>
          <MultilingualInput
            id={`${formId}-display-name`}
            required
            disabled={disabled}
            language={language}
            value={destination.displayName || null}
            onChange={(displayName) => change('displayName', displayName ?? '')}
          />
        </div>
        <div className="col-md-3">
          <label className="form-label" htmlFor={`${formId}-description`}>
            {t('filesubclassdestination.description')}
          </label>
          <MultilingualInput
            id={`${formId}-description`}
            disabled={disabled}
            language={language}
            value={destination.description}
            onChange={(description) => change('description', description)}
          />
        </div>
        <div className="col-md-2">
          <label className="form-label" htmlFor={`${formId}-position`}>
            {t('common.position')}
          </label>
          <input
            id={`${formId}-position`}
            className="form-control"
            type="number"
            value={destination.listingPosition}
            onChange={(event) => change('listingPosition', Number(event.target.value))}
          />
        </div>
      </fieldset>
    </form>
  )
}
