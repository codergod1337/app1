import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { ConfirmCancelButtons } from '../../components/ConfirmCancelButtons.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { useOrigin, type OriginState } from '../../components/Origin.ts'
import { type FileSubClassDestination, updateFileSubClassDestination } from '../filesubclass/FileSubClassDestination.ts'
import { FileSubClassDestinationForm } from '../filesubclass/FileSubClassDestinationForm.tsx'

const FORM_ID = 'edit-file-sub-class-destination-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/admin/filesubclassdestinations'

/** State beim Öffnen: der origin und der key des Ortes. Der key steht nicht in der URL, dort sind nur guid und Long-ID erlaubt. */
export interface EditFileSubClassDestinationState extends OriginState {
  fileSubClassDestinationKey: string
}

/** Einen Anzeigeort bearbeiten. Abbrechen und Speichern springen zum origin. */
export function EditFileSubClassDestinationTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const destinationKey = (useLocation().state as Partial<EditFileSubClassDestinationState> | null)?.fileSubClassDestinationKey
  // Die Orte liegen in den Stammdaten: den gesuchten heraussuchen
  const { fileSubClassDestinations, errorMessage: loadErrorMessage, reloadMasterData } = useMasterData()
  const destination = fileSubClassDestinations?.find((candidate) => candidate.key === destinationKey) ?? null
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)

  function saveDestination(changedDestinationData: FileSubClassDestination) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateFileSubClassDestination(changedDestinationData)
      .then(() => {
        reloadMasterData()
        navigate(originPath)
      })
      .catch((error: unknown) => {
        setSaveErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setSaving(false)
      })
  }

  let errorMessage = loadErrorMessage ?? saveErrorMessage
  if (errorMessage === null && fileSubClassDestinations !== null && destination === null) {
    errorMessage = t('filesubclassdestination.notFound')
  }

  // dasselbe Paar im Kopf und im Footer
  const buttons = (
    <ConfirmCancelButtons
      onCancel={() => navigate(originPath)}
      confirmLabel={t('common.save')}
      confirmForm={FORM_ID}
      disabled={destination === null}
      busy={saving}
    />
  )
  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {fileSubClassDestinations === null && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
      )}
      {buttons}
    </>
  )

  return (
    <ContentBox
      title={destination ? t('filesubclassdestination.editTitle', { key: destination.key }) : t('filesubclassdestination.editTitleNone')}
      actions={buttons}
      footer={footer}
      language={{ value: language, onChange: setLanguage }}
    >
      {/* Erst mit den geladenen Daten anzeigen, damit das Formular mit ihnen startet */}
      {destination !== null && (
        <FileSubClassDestinationForm formId={FORM_ID} initialFileSubClassDestination={destination} language={language} disabled={saving} onSubmit={saveDestination} />
      )}
    </ContentBox>
  )
}
