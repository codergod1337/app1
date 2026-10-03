import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { useLanguage } from '../../components/languageContext.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { useOrigin, type OriginState } from '../../components/Origin.ts'
import {
  updateFileExtensionCollection,
  type FileExtensionCollection,
} from '../fileextension/FileExtensionCollection.ts'
import { FileExtensionCollectionForm } from '../fileextension/FileExtensionCollectionForm.tsx'

const FORM_ID = 'edit-file-extension-collection-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/admin/fileextensions'

/** State beim Öffnen: der origin und der key der Gruppe */
export interface EditFileExtensionCollectionState extends OriginState {
  fileExtensionCollectionKey: string
}

/** Eine Gruppe von Endungen bearbeiten. Abbrechen und Speichern springen zum origin. */
export function EditFileExtensionCollectionTab() {
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const fileExtensionCollectionKey = (useLocation().state as Partial<EditFileExtensionCollectionState> | null)
    ?.fileExtensionCollectionKey
  // Die Gruppen liegen in den Stammdaten: die gesuchte heraussuchen
  const { fileExtensionCollections, errorMessage: loadErrorMessage, reloadMasterData } = useMasterData()
  const fileExtensionCollection =
    fileExtensionCollections?.find((candidate) => candidate.key === fileExtensionCollectionKey) ?? null
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)

  function saveFileExtensionCollection(changedFileExtensionCollectionData: FileExtensionCollection) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateFileExtensionCollection(changedFileExtensionCollectionData)
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
  if (errorMessage === null && fileExtensionCollections !== null && fileExtensionCollection === null) {
    errorMessage = 'keine Gruppe gewählt oder nicht gefunden'
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {fileExtensionCollections === null && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
      )}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={() => navigate(originPath)}>
          Abbrechen
        </button>
        <button type="submit" form={FORM_ID} className="button-save" disabled={saving || fileExtensionCollection === null}>
          Speichern
        </button>
      </span>
    </>
  )

  return (
    <ContentBox
      title={
        fileExtensionCollection
          ? `Gruppe bearbeiten: ${translate(fileExtensionCollection.displayName)}`
          : 'Gruppe bearbeiten'
      }
      footer={footer}
      language={{ value: language, onChange: setLanguage }}
    >
      {/* Erst mit den geladenen Daten anzeigen, damit das Formular mit ihnen startet */}
      {fileExtensionCollection !== null && (
        <FileExtensionCollectionForm
          formId={FORM_ID}
          initialFileExtensionCollection={fileExtensionCollection}
          language={language}
          disabled={saving}
          onSubmit={saveFileExtensionCollection}
        />
      )}
    </ContentBox>
  )
}
