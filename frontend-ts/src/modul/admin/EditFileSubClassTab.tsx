import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { useLanguage } from '../../components/languageContext.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { useOrigin, type OriginState } from '../../components/Origin.ts'
import { updateFileSubClass, type FileSubClassData } from '../filesubclass/FileSubClass.ts'
import { FileSubClassForm } from '../filesubclass/FileSubClassForm.tsx'

const FORM_ID = 'edit-file-sub-class-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/admin/filesubclasses'

/**
 * State beim Öffnen: der origin und der key der Dateiart. Der key steht nicht in der URL, dort sind nur guid und
 * Long-ID erlaubt.
 */
export interface EditFileSubClassState extends OriginState {
  fileSubClassKey: string
}

/** Eine Dateiart bearbeiten. Abbrechen und Speichern springen zum origin. */
export function EditFileSubClassTab() {
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const fileSubClassKey = (useLocation().state as Partial<EditFileSubClassState> | null)?.fileSubClassKey
  // Die Dateiarten liegen in den Stammdaten: die gesuchte heraussuchen
  const { fileSubClasses, errorMessage: loadErrorMessage, reloadMasterData } = useMasterData()
  const fileSubClass = fileSubClasses?.find((candidate) => candidate.key === fileSubClassKey) ?? null
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)

  function saveFileSubClass(changedFileSubClassData: FileSubClassData) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateFileSubClass(changedFileSubClassData)
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
  if (errorMessage === null && fileSubClasses !== null && fileSubClass === null) {
    errorMessage = 'keine Dateiart gewählt oder nicht gefunden'
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {fileSubClasses === null && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
      )}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={() => navigate(originPath)}>
          Abbrechen
        </button>
        <button type="submit" form={FORM_ID} className="button-save" disabled={saving || fileSubClass === null}>
          Speichern
        </button>
      </span>
    </>
  )

  return (
    <ContentBox
      title={fileSubClass ? `Dateiart bearbeiten: ${fileSubClass.key}` : 'Dateiart bearbeiten'}
      footer={footer}
      language={{ value: language, onChange: setLanguage }}
    >
      {/* Erst mit den geladenen Daten anzeigen, damit das Formular mit ihnen startet */}
      {fileSubClass !== null && (
        <FileSubClassForm
          formId={FORM_ID}
          initialFileSubClass={fileSubClass}
          language={language}
          disabled={saving}
          onSubmit={saveFileSubClass}
        />
      )}
    </ContentBox>
  )
}
