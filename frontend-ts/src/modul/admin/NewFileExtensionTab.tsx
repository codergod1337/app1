import { useState } from 'react'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { useLanguage } from '../../components/languageContext.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { useOrigin } from '../../components/Origin.ts'
import { createFileExtension, type FileExtension } from '../fileextension/FileExtension.ts'
import { FileExtensionForm } from '../fileextension/FileExtensionForm.tsx'

const FORM_ID = 'new-file-extension-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/admin/fileextensions'

/** Neue Endung anlegen. Abbrechen und Anlegen springen zum origin. */
export function NewFileExtensionTab() {
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const [saving, setSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)
  const { reloadMasterData } = useMasterData()

  function saveNewFileExtension(newFileExtensionData: FileExtension) {
    setSaving(true)
    setErrorMessage(null)
    createFileExtension(newFileExtensionData)
      .then(() => {
        reloadMasterData()
        navigate(originPath)
      })
      .catch((error: unknown) => {
        setErrorMessage(error instanceof RequestError ? error.message : 'Unbekannter Fehler')
        setSaving(false)
      })
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={() => navigate(originPath)}>
          Abbrechen
        </button>
        <button type="submit" form={FORM_ID} className="button-save" disabled={saving}>
          Anlegen
        </button>
      </span>
    </>
  )

  return (
    <ContentBox title="new FileExtension()" footer={footer} language={{ value: language, onChange: setLanguage }}>
      <FileExtensionForm formId={FORM_ID} language={language} disabled={saving} onSubmit={saveNewFileExtension} />
    </ContentBox>
  )
}
