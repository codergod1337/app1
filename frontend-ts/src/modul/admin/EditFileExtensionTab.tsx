import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { DEFAULT_LANGUAGE, type LanguageCode } from '../../branding/languages.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { useOrigin, type OriginState } from '../../components/Origin.ts'
import { updateFileExtension, type FileExtension } from '../fileextension/FileExtension.ts'
import { FileExtensionForm } from '../fileextension/FileExtensionForm.tsx'

const FORM_ID = 'edit-file-extension-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/admin/fileextensions'

/** State beim Öffnen: der origin und die Endung. Sie steht nicht in der URL, dort sind nur guid und Long-ID erlaubt. */
export interface EditFileExtensionState extends OriginState {
  fileExtension: string
}

/** Eine Endung bearbeiten. Abbrechen und Speichern springen zum origin. */
export function EditFileExtensionTab() {
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const extension = (useLocation().state as Partial<EditFileExtensionState> | null)?.fileExtension
  // Die Endungen liegen in den Stammdaten: die gesuchte heraussuchen
  const { fileExtensions, errorMessage: loadErrorMessage, reloadMasterData } = useMasterData()
  const fileExtension = fileExtensions?.find((candidate) => candidate.extension === extension) ?? null
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(DEFAULT_LANGUAGE)

  function saveFileExtension(changedFileExtensionData: FileExtension) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateFileExtension(changedFileExtensionData)
      .then(() => {
        reloadMasterData()
        navigate(originPath)
      })
      .catch((error: unknown) => {
        setSaveErrorMessage(error instanceof RequestError ? error.message : 'Unbekannter Fehler')
        setSaving(false)
      })
  }

  let errorMessage = loadErrorMessage ?? saveErrorMessage
  if (errorMessage === null && fileExtensions !== null && fileExtension === null) {
    errorMessage = 'keine Endung gewählt oder nicht gefunden'
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {fileExtensions === null && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
      )}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={() => navigate(originPath)}>
          Abbrechen
        </button>
        <button type="submit" form={FORM_ID} className="button-save" disabled={saving || fileExtension === null}>
          Speichern
        </button>
      </span>
    </>
  )

  return (
    <ContentBox
      title={fileExtension ? `Endung bearbeiten: .${fileExtension.extension}` : 'Endung bearbeiten'}
      footer={footer}
      language={{ value: language, onChange: setLanguage }}
    >
      {/* Erst mit den geladenen Daten anzeigen, damit das Formular mit ihnen startet */}
      {fileExtension !== null && (
        <FileExtensionForm
          formId={FORM_ID}
          initialFileExtension={fileExtension}
          language={language}
          disabled={saving}
          onSubmit={saveFileExtension}
        />
      )}
    </ContentBox>
  )
}
