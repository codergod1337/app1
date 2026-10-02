import { useState, type FormEvent } from 'react'
import type { LanguageCode } from '../../branding/languages.ts'
import { KeyInput } from '../../components/KeyInput.tsx'
import { MultilingualInput } from '../../components/MultilingualInput.tsx'
import { SYMBOL_SHAPES, type FileExtensionCollection, type SymbolShape } from './FileExtensionCollection.ts'
import { FileShape } from './FileShape.tsx'

/** Startwerte einer neuen Gruppe */
const NEW_FILE_EXTENSION_COLLECTION: FileExtensionCollection = {
  key: '',
  displayName: '',
  symbolShape: null,
  listingPosition: 0,
}

interface FileExtensionCollectionFormProps {
  /** id des form-Elements, damit ein Knopf außerhalb (z. B. im contentbox-footer) es abschicken kann */
  formId: string
  /** Startwerte beim Bearbeiten. Fehlen sie, ist das Formular leer (neue Gruppe). */
  initialFileExtensionCollection?: FileExtensionCollection
  /** Sprache des Namens, gewählt über die Flaggen der ContentBox */
  language: LanguageCode
  disabled: boolean
  onSubmit: (fileExtensionCollection: FileExtensionCollection) => void
}

/**
 * Eingabefelder einer Gruppe von Endungen, zum Anlegen und zum Bearbeiten: Key, Name, Form und Position. Beim
 * Bearbeiten ist der key fest. In der Form wird später jede Datei mit einer Endung dieser Gruppe gezeichnet.
 * Speichert nichts selbst: die Daten gehen an onSubmit.
 */
export function FileExtensionCollectionForm({
  formId,
  initialFileExtensionCollection,
  language,
  disabled,
  onSubmit,
}: FileExtensionCollectionFormProps) {
  const editing = initialFileExtensionCollection !== undefined
  const [fileExtensionCollection, setFileExtensionCollection] = useState<FileExtensionCollection>(
    () => initialFileExtensionCollection ?? NEW_FILE_EXTENSION_COLLECTION,
  )

  function change<K extends keyof FileExtensionCollection>(field: K, value: FileExtensionCollection[K]) {
    setFileExtensionCollection((current) => ({ ...current, [field]: value }))
  }

  function submitFileExtensionCollection(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit(fileExtensionCollection)
  }

  return (
    <form id={formId} onSubmit={submitFileExtensionCollection}>
      <fieldset className="row g-3" disabled={disabled}>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-key`}>
            Key
          </label>
          <KeyInput
            id={`${formId}-key`}
            required
            // der key ändert sich nie, sonst zeigten alle Endungen der Gruppe ins Leere
            disabled={disabled || editing}
            value={fileExtensionCollection.key}
            onChange={(key) => change('key', key)}
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-display-name`}>
            Name
          </label>
          <MultilingualInput
            id={`${formId}-display-name`}
            required
            disabled={disabled}
            language={language}
            value={fileExtensionCollection.displayName || null}
            onChange={(displayName) => change('displayName', displayName ?? '')}
          />
        </div>
        <div className="col-md-4 col-lg-3">
          <label className="form-label" htmlFor={`${formId}-shape`}>
            Form
          </label>
          <div className="d-flex align-items-center gap-2">
            <select
              id={`${formId}-shape`}
              className="form-select"
              value={fileExtensionCollection.symbolShape ?? ''}
              onChange={(event) =>
                change('symbolShape', event.target.value === '' ? null : (event.target.value as SymbolShape))
              }
            >
              <option value="">Standard</option>
              {SYMBOL_SHAPES.map(({ shape, label }) => (
                <option key={shape} value={shape}>
                  {label}
                </option>
              ))}
            </select>
            <span className="fs-4 d-inline-flex">
              <FileShape shape={fileExtensionCollection.symbolShape} />
            </span>
          </div>
        </div>
        <div className="col-md-2">
          <label className="form-label" htmlFor={`${formId}-position`}>
            Position
          </label>
          <input
            id={`${formId}-position`}
            className="form-control"
            type="number"
            value={fileExtensionCollection.listingPosition}
            onChange={(event) => change('listingPosition', Number(event.target.value))}
          />
        </div>
      </fieldset>
    </form>
  )
}
