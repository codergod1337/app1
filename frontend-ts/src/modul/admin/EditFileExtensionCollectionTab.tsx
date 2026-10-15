import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { useLanguage } from '../../components/languageContext.ts'
import { ConfirmCancelButtons } from '../../components/ConfirmCancelButtons.tsx'
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
  const { t } = useTranslation()
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

  // dasselbe Paar im Kopf und im Footer
  const buttons = (
    <ConfirmCancelButtons
      onCancel={() => navigate(originPath)}
      confirmLabel={t('common.save')}
      confirmForm={FORM_ID}
      disabled={fileExtensionCollection === null}
      busy={saving}
    />
  )
  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {fileExtensionCollections === null && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
      )}
      {buttons}
    </>
  )

  return (
    <ContentBox
      title={
        fileExtensionCollection
          ? `Gruppe bearbeiten: ${translate(fileExtensionCollection.displayName)}`
          : 'Gruppe bearbeiten'
      }
      actions={buttons}
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
