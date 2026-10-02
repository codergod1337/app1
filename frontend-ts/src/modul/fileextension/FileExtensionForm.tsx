import { useState, type FormEvent } from 'react'
import type { LanguageCode } from '../../branding/languages.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { MultilingualInput } from '../../components/MultilingualInput.tsx'
import type { FileExtension } from './FileExtension.ts'

/** Startwerte einer neuen Endung */
const NEW_FILE_EXTENSION: FileExtension = {
  extension: '',
  description: null,
  fileExtensionCollectionKey: null,
  dangerous: false,
  listingPosition: 0,
  ocr: false,
  ki: false,
  imagePreview: false,
}

/** Die Schalter, was das Format kann, plus das Achtung-Kennzeichen */
const FLAGS: { field: 'dangerous' | 'ocr' | 'ki' | 'imagePreview'; label: string }[] = [
  { field: 'dangerous', label: 'Achtung: heikles Format, z. B. html oder exe (Warnung beim Freischalten)' },
  { field: 'ocr', label: 'OCR: Text aus Bildern und Scans erkennen' },
  { field: 'ki', label: 'KI: für die maschinelle Auswertung' },
  { field: 'imagePreview', label: 'Vorschaubild rechnen' },
]

interface FileExtensionFormProps {
  /** id des form-Elements, damit ein Knopf außerhalb (z. B. im contentbox-footer) es abschicken kann */
  formId: string
  /** Startwerte beim Bearbeiten. Fehlen sie, ist das Formular leer (neue Endung). */
  initialFileExtension?: FileExtension
  /** Sprache der Beschreibung, gewählt über die Flaggen der ContentBox */
  language: LanguageCode
  disabled: boolean
  onSubmit: (fileExtension: FileExtension) => void
}

/**
 * Eingabefelder einer Endung, zum Anlegen und zum Bearbeiten. Die Endung selbst ist beim Bearbeiten fest. Beim
 * Tippen bleiben nur a–z und 0–9, groß wird klein, ein Punkt fällt weg. Tika bekommt jede Datei, dafür gibt es keinen
 * Schalter. Speichert nichts selbst: die Daten gehen an onSubmit.
 */
export function FileExtensionForm({ formId, initialFileExtension, language, disabled, onSubmit }: FileExtensionFormProps) {
  const editing = initialFileExtension !== undefined
  const { fileExtensionCollections } = useMasterData()
  const [fileExtension, setFileExtension] = useState<FileExtension>(() => initialFileExtension ?? NEW_FILE_EXTENSION)

  function change<K extends keyof FileExtension>(field: K, value: FileExtension[K]) {
    setFileExtension((current) => ({ ...current, [field]: value }))
  }

  function submitFileExtension(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit(fileExtension)
  }

  return (
    <form id={formId} onSubmit={submitFileExtension}>
      <fieldset className="row g-3" disabled={disabled}>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-extension`}>
            Endung
          </label>
          <div className="input-group">
            <span className="input-group-text">.</span>
            <input
              id={`${formId}-extension`}
              className="form-control font-monospace"
              required
              // die Endung ändert sich nie, die Dateiarten schalten sie per Text frei
              disabled={disabled || editing}
              maxLength={20}
              autoComplete="off"
              spellCheck={false}
              value={fileExtension.extension}
              onChange={(event) => change('extension', event.target.value.toLowerCase().replace(/[^a-z0-9]/g, ''))}
            />
          </div>
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-collection`}>
            Gruppe
          </label>
          <select
            id={`${formId}-collection`}
            className="form-select"
            value={fileExtension.fileExtensionCollectionKey ?? ''}
            onChange={(event) =>
              change('fileExtensionCollectionKey', event.target.value === '' ? null : event.target.value)
            }
          >
            <option value="">ohne Gruppe</option>
            {(fileExtensionCollections ?? []).map((fileExtensionCollection) => (
              <option key={fileExtensionCollection.key} value={fileExtensionCollection.key}>
                {translate(fileExtensionCollection.displayName)}
              </option>
            ))}
          </select>
        </div>
        <div className="col-12">
          <label className="form-label" htmlFor={`${formId}-description`}>
            Beschreibung
          </label>
          <MultilingualInput
            id={`${formId}-description`}
            disabled={disabled}
            language={language}
            value={fileExtension.description}
            onChange={(description) => change('description', description)}
          />
        </div>
        <div className="col-md-3">
          <label className="form-label" htmlFor={`${formId}-position`}>
            Position in der Gruppe
          </label>
          <input
            id={`${formId}-position`}
            className="form-control"
            type="number"
            value={fileExtension.listingPosition}
            onChange={(event) => change('listingPosition', Number(event.target.value))}
          />
        </div>
        <div className="col-md-9">
          {FLAGS.map(({ field, label }) => (
            <div key={field} className="form-check">
              <input
                id={`${formId}-${field}`}
                className="form-check-input"
                type="checkbox"
                checked={fileExtension[field]}
                onChange={(event) => change(field, event.target.checked)}
              />
              <label className="form-check-label" htmlFor={`${formId}-${field}`}>
                {label}
              </label>
            </div>
          ))}
        </div>
      </fieldset>
    </form>
  )
}
