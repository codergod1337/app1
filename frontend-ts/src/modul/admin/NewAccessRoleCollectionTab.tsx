import { useState } from 'react'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { useLanguage } from '../../components/languageContext.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { useOrigin } from '../../components/Origin.ts'
import {
  createAccessRoleCollection,
  type NewAccessRoleCollectionData,
} from '../accessrolecollection/AccessRoleCollection.ts'
import { AccessRoleCollectionForm } from '../accessrolecollection/AccessRoleCollectionForm.tsx'

const FORM_ID = 'new-access-role-collection-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/admin/accessrolecollections'

/** Neue AccessRoleCollection anlegen. Abbrechen und Anlegen springen zum origin. */
export function NewAccessRoleCollectionTab() {
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const { accessRoleCollections, errorMessage: loadErrorMessage, reloadMasterData } = useMasterData()
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)

  function saveNewAccessRoleCollection(newData: NewAccessRoleCollectionData) {
    setSaving(true)
    setSaveErrorMessage(null)
    createAccessRoleCollection(newData)
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
  const errorMessage = loadErrorMessage ?? saveErrorMessage
  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {!loaded && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
      )}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={() => navigate(originPath)}>
          Abbrechen
        </button>
        <button type="submit" form={FORM_ID} className="button-save" disabled={saving || !loaded}>
          Anlegen
        </button>
      </span>
    </>
  )

  return (
    <ContentBox
      title="new AccessRoleCollection()"
      footer={footer}
      language={{ value: language, onChange: setLanguage }}
    >
      {loaded && (
        <AccessRoleCollectionForm
          formId={FORM_ID}
          accessRoleCollections={accessRoleCollections}
          language={language}
          disabled={saving}
          onSubmit={saveNewAccessRoleCollection}
        />
      )}
    </ContentBox>
  )
}
