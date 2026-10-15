import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { useLanguage } from '../../components/languageContext.ts'
import { ConfirmCancelButtons } from '../../components/ConfirmCancelButtons.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { useOrigin, type OriginState } from '../../components/Origin.ts'
import {
  updateAccessRoleCollection,
  type NewAccessRoleCollectionData,
} from '../accessrolecollection/AccessRoleCollection.ts'
import { AccessRoleCollectionForm } from '../accessrolecollection/AccessRoleCollectionForm.tsx'

const FORM_ID = 'edit-access-role-collection-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/admin/accessrolecollections'

/**
 * State beim Öffnen: der origin und der key der ARC. Der key steht nicht in der URL, dort sind nur guid und
 * Long-ID erlaubt.
 */
export interface EditAccessRoleCollectionState extends OriginState {
  accessRoleCollectionKey: string
}

/** Eine AccessRoleCollection bearbeiten. Abbrechen und Speichern springen zum origin. */
export function EditAccessRoleCollectionTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const arcKey = (useLocation().state as Partial<EditAccessRoleCollectionState> | null)?.accessRoleCollectionKey
  // Die ARCs liegen in den Stammdaten: die gesuchte heraussuchen
  const { accessRoleCollections, errorMessage: loadErrorMessage, reloadMasterData } = useMasterData()
  const arc = accessRoleCollections?.find((candidate) => candidate.key === arcKey) ?? null
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)

  function saveAccessRoleCollection(changedData: NewAccessRoleCollectionData) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateAccessRoleCollection(changedData)
      .then(() => {
        reloadMasterData()
        navigate(originPath)
      })
      .catch((error: unknown) => {
        setSaveErrorMessage(error instanceof RequestError ? error.message : 'Unbekannter Fehler')
        setSaving(false)
      })
  }

  const loaded = accessRoleCollections !== null
  let errorMessage = loadErrorMessage ?? saveErrorMessage
  if (errorMessage === null && loaded && arc === null) {
    errorMessage = 'keine AccessRoleCollection gewählt oder nicht gefunden'
  }

  // dasselbe Paar im Kopf und im Footer
  const buttons = (
    <ConfirmCancelButtons
      onCancel={() => navigate(originPath)}
      confirmLabel={t('common.save')}
      confirmForm={FORM_ID}
      disabled={arc === null}
      busy={saving}
    />
  )
  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {!loaded && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
      )}
      {buttons}
    </>
  )

  return (
    <ContentBox
      title={arc ? `AccessRoleCollection bearbeiten: ${arc.key}` : 'AccessRoleCollection bearbeiten'}
      actions={buttons}
      footer={footer}
      language={{ value: language, onChange: setLanguage }}
    >
      {/* Erst mit den geladenen Daten anzeigen, damit das Formular mit ihnen startet */}
      {loaded && arc !== null && (
        <AccessRoleCollectionForm
          formId={FORM_ID}
          initialAccessRoleCollection={arc}
          accessRoleCollections={accessRoleCollections}
          language={language}
          disabled={saving}
          onSubmit={saveAccessRoleCollection}
        />
      )}
    </ContentBox>
  )
}
