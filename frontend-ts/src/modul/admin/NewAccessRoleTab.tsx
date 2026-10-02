import { useState } from 'react'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { DEFAULT_LANGUAGE, type LanguageCode } from '../../branding/languages.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { useOrigin } from '../../components/Origin.ts'
import { createAccessRole, type NewAccessRoleData } from '../accessrole/AccessRole.ts'
import { AccessRoleForm } from '../accessrole/AccessRoleForm.tsx'

const FORM_ID = 'new-access-role-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/admin/accessroles'

/** Neue AccessRole anlegen. Abbrechen und Anlegen springen zum origin. */
export function NewAccessRoleTab() {
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const [saving, setSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(DEFAULT_LANGUAGE)
  const { reloadMasterData } = useMasterData()

  function saveNewAccessRole(newAccessRoleData: NewAccessRoleData) {
    setSaving(true)
    setErrorMessage(null)
    createAccessRole(newAccessRoleData)
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
    <ContentBox title="new AccessRole()" footer={footer} language={{ value: language, onChange: setLanguage }}>
      <AccessRoleForm formId={FORM_ID} language={language} disabled={saving} onSubmit={saveNewAccessRole} />
    </ContentBox>
  )
}
