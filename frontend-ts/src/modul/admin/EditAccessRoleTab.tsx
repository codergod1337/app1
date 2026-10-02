import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { DEFAULT_LANGUAGE, type LanguageCode } from '../../branding/languages.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { useOrigin, type OriginState } from '../../components/Origin.ts'
import { updateAccessRole, type NewAccessRoleData } from '../accessrole/AccessRole.ts'
import { AccessRoleForm } from '../accessrole/AccessRoleForm.tsx'

const FORM_ID = 'edit-access-role-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/admin/accessroles'

/**
 * State beim Öffnen: der origin und der key der Rolle. Der key steht nicht in der URL, dort sind nur guid und
 * Long-ID erlaubt.
 */
export interface EditAccessRoleState extends OriginState {
  accessRoleKey: string
}

/** Eine AccessRole bearbeiten. Abbrechen und Speichern springen zum origin. */
export function EditAccessRoleTab() {
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const accessRoleKey = (useLocation().state as Partial<EditAccessRoleState> | null)?.accessRoleKey
  // Die Rollen liegen in den Stammdaten: die gesuchte heraussuchen
  const { accessRoles, errorMessage: loadErrorMessage, reloadMasterData } = useMasterData()
  const accessRole = accessRoles?.find((candidate) => candidate.key === accessRoleKey) ?? null
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(DEFAULT_LANGUAGE)

  function saveAccessRole(changedAccessRoleData: NewAccessRoleData) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateAccessRole(changedAccessRoleData)
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
  if (errorMessage === null && accessRoles !== null && accessRole === null) {
    errorMessage = 'keine AccessRole gewählt oder nicht gefunden'
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {accessRoles === null && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
      )}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={() => navigate(originPath)}>
          Abbrechen
        </button>
        <button type="submit" form={FORM_ID} className="button-save" disabled={saving || accessRole === null}>
          Speichern
        </button>
      </span>
    </>
  )

  return (
    <ContentBox
      title={accessRole ? `AccessRole bearbeiten: ${accessRole.key}` : 'AccessRole bearbeiten'}
      footer={footer}
      language={{ value: language, onChange: setLanguage }}
    >
      {/* Erst mit den geladenen Daten anzeigen, damit das Formular mit ihnen startet */}
      {accessRole !== null && (
        <AccessRoleForm
          formId={FORM_ID}
          initialAccessRole={accessRole}
          language={language}
          disabled={saving}
          onSubmit={saveAccessRole}
        />
      )}
    </ContentBox>
  )
}
