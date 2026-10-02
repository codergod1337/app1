import { useState } from 'react'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useOrigin } from '../../components/Origin.ts'
import { createUsers, type UsersData } from '../users/Users.ts'
import { UsersForm } from '../users/UsersForm.tsx'

const FORM_ID = 'new-users-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/admin/users'

/** Neuen User anlegen. Abbrechen und Anlegen springen zum origin. */
export function NewUsersTab() {
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const [saving, setSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  function saveNewUsers(newUsersData: UsersData) {
    setSaving(true)
    setErrorMessage(null)
    createUsers(newUsersData)
      .then(() => navigate(originPath))
      .catch((error: unknown) => {
        setErrorMessage(error instanceof RequestError ? error.message : 'Unbekannter Fehler')
        setSaving(false)
      })
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      <span className="ms-auto d-flex gap-2">
        <button
          type="button"
          className="button-cancel"
          disabled={saving}
          onClick={() => navigate(originPath)}
        >
          Abbrechen
        </button>
        <button type="submit" form={FORM_ID} className="button-save" disabled={saving}>
          Anlegen
        </button>
      </span>
    </>
  )

  return (
    <ContentBox title="new Users()" footer={footer}>
      <UsersForm formId={FORM_ID} disabled={saving} onSubmit={saveNewUsers} />
    </ContentBox>
  )
}
