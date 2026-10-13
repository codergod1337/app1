import { useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate, useParams } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { useLoad } from '../../api/useLoad.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import { useOrigin } from '../../components/Origin.ts'
import {
  changeAccessRoleUsersAssignments,
  getAccessRoleKeysByUsersGuid,
} from '../accessrole/AccessRoleUsersAssignment.ts'
import { BadgeToggleList } from '../accessrole/BadgeToggleList.tsx'
import { SolrCoreEmblem } from '../solrmanager/SolrCoreEmblem.tsx'
import { solrFieldLabel } from '../solrmanager/SolrField.ts'
import { SolrHookChip } from '../solrmanager/SolrHookChip.tsx'
import { ChangeUsersGuidPopup } from '../users/ChangeUsersGuidPopup.tsx'
import { getUsersByGuid, updateUsers, type Users, type UsersData } from '../users/Users.ts'
import { deleteUsersDebuffs, getUsersDebuffsByUsersGuid } from '../users/UsersDebuffs.ts'
import { UsersForm } from '../users/UsersForm.tsx'
import { UsersDebuffsPopup } from './UsersDebuffsPopup.tsx'

const FORM_ID = 'edit-users-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/admin/users'

/**
 * Admin bearbeitet einen User, die guid steht im Pfad. guid und createdAt stehen nur zur Anzeige da, die guid
 * ändert ein eigenes Popup. Darunter die AccessRoles des Users zum Anklicken, gespeichert mit demselben Knopf.
 * Darunter die Debuffs des Users (UsersDebuffs): die Liste mit Löschknopf je Zeile, das Plus öffnet das Popup für
 * einen neuen; jede Änderung speichert sofort, ohne den Knopf unten. Abbrechen und Speichern springen zum origin.
 */
export function EditUsersTab() {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const guid = useParams().guid ?? ''
  const navigate = useNavigate()
  const location = useLocation()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const loadUsers = useCallback(() => getUsersByGuid(guid), [guid])
  const { data: users, errorMessage: loadErrorMessage } = useLoad(loadUsers)
  const { accessRoles, solrCores, solrFields, solrHooks, errorMessage: accessRolesErrorMessage } = useMasterData()
  const loadAccessRoleKeys = useCallback(() => getAccessRoleKeysByUsersGuid(guid), [guid])
  const { data: loadedAccessRoleKeys, errorMessage: accessRoleKeysErrorMessage } = useLoad(loadAccessRoleKeys)
  const loadUsersDebuffs = useCallback(() => getUsersDebuffsByUsersGuid(guid), [guid])
  const { data: usersDebuffs, errorMessage: usersDebuffsErrorMessage, reload: reloadUsersDebuffs } = useLoad(loadUsersDebuffs)
  // null, solange der Admin keine Badge angeklickt hat: dann gilt der geladene Stand
  const [selectedAccessRoleKeys, setSelectedAccessRoleKeys] = useState<string[] | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  const [changingGuid, setChangingGuid] = useState(false)
  const [debuffPopupOpen, setDebuffPopupOpen] = useState(false)
  const [debuffErrorMessage, setDebuffErrorMessage] = useState<string | null>(null)

  const shownAccessRoleKeys = selectedAccessRoleKeys ?? loadedAccessRoleKeys

  /** Ein Debuff weg, danach die Liste neu. Die Sitzungen des Users beendet das Backend. */
  function removeUsersDebuffs(id: number) {
    setDebuffErrorMessage(null)
    deleteUsersDebuffs(id)
      .then(() => reloadUsersDebuffs())
      .catch((error: unknown) => {
        setDebuffErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
      })
  }

  /** Das Feld eines Debuffs: Hook als Chip, Feld mit Emblem seines Kerns und Anzeigename, Unbekanntes nur als Name */
  function debuffField(solrFieldName: string) {
    const solrHook = (solrHooks ?? []).find((candidate) => candidate.key === solrFieldName)
    if (solrHook) {
      return <SolrHookChip solrHook={solrHook} language={language} />
    }
    const solrField = (solrFields ?? []).find((candidate) => candidate.name === solrFieldName)
    if (!solrField) {
      return <span className="font-monospace">{solrFieldName}</span>
    }
    const solrCore = (solrCores ?? []).find((candidate) => candidate.key === solrField.coreKey)
    return (
      <span className="d-inline-flex align-items-center gap-2">
        {solrCore && <SolrCoreEmblem solrCore={solrCore} language={language} />}
        <strong>{solrFieldLabel(solrField, language)}</strong>
        <span className="text-body-secondary font-monospace">{solrField.name}</span>
      </span>
    )
  }

  /** Erst den User, dann die AR-Zuordnungen, aber nur, wenn an den Badges etwas angeklickt wurde */
  function saveUsers(changedUsersData: UsersData) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateUsers(guid, changedUsersData)
      .then(() =>
        selectedAccessRoleKeys === null ? undefined : changeAccessRoleUsersAssignments(guid, selectedAccessRoleKeys),
      )
      .then(() => navigate(originPath))
      .catch((error: unknown) => {
        setSaveErrorMessage(error instanceof RequestError ? error.message : 'Unbekannter Fehler')
        setSaving(false)
      })
  }

  /** Nach dem guid-Wechsel dieselbe Maske unter der neuen guid, der origin bleibt */
  function showChangedUsers(changedUsers: Users) {
    navigate(`/admin/users/edit/${changedUsers.guid}`, { replace: true, state: location.state })
  }

  const errorMessage =
    loadErrorMessage ??
    accessRolesErrorMessage ??
    accessRoleKeysErrorMessage ??
    usersDebuffsErrorMessage ??
    debuffErrorMessage ??
    saveErrorMessage
  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {users === null && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
      )}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={() => navigate(originPath)}>
          Abbrechen
        </button>
        <button type="submit" form={FORM_ID} className="button-save" disabled={saving || users === null}>
          Speichern
        </button>
      </span>
    </>
  )

  return (
    <ContentBox title={users ? `User bearbeiten: ${users.email}` : 'User bearbeiten'} footer={footer}>
      {/* Erst mit den geladenen Daten anzeigen, damit das Formular mit ihnen startet */}
      {users !== null && (
        <>
          <div className="row g-3 mb-3">
            <div className="col-md-6">
              <label className="form-label" htmlFor={`${FORM_ID}-guid`}>
                guid
              </label>
              <div className="d-flex align-items-center gap-2">
                <input id={`${FORM_ID}-guid`} className="form-control font-monospace" readOnly value={users.guid} />
                <EditButton label="guid ändern" onClick={() => setChangingGuid(true)} />
              </div>
            </div>
            <div className="col-md-6">
              <label className="form-label" htmlFor={`${FORM_ID}-created-at`}>
                Angelegt
              </label>
              <input
                id={`${FORM_ID}-created-at`}
                className="form-control"
                readOnly
                value={new Date(users.createdAt).toLocaleString('de-DE')}
              />
            </div>
          </div>
          <UsersForm
            formId={FORM_ID}
            initialUsersData={{
              email: users.email,
              username: users.username ?? undefined,
              vorname: users.vorname ?? undefined,
              nachname: users.nachname ?? undefined,
              serviceAccount: users.serviceAccount,
              status: users.status,
            }}
            disabled={saving}
            onSubmit={saveUsers}
          />
          <div className="mt-3">
            <label className="form-label">AccessRoles</label>
            {accessRoles !== null && shownAccessRoleKeys !== null ? (
              <BadgeToggleList
                badges={accessRoles}
                selectedKeys={shownAccessRoleKeys}
                shape="rect"
                disabled={saving}
                onChange={setSelectedAccessRoleKeys}
              />
            ) : (
              <span className="spinner-border spinner-border-sm d-block" role="status" aria-label="lädt" />
            )}
          </div>
          <div className="mt-3">
            <div className="d-flex align-items-center justify-content-between">
              <label className="form-label mb-0">{t('admin.debuffs.title')}</label>
              <NewEntityButton entity="UsersDebuffs" disabled={saving} onClick={() => setDebuffPopupOpen(true)} />
            </div>
            <p className="text-body-secondary small">{t('admin.debuffs.intro')}</p>
            {usersDebuffs === null ? (
              <span className="spinner-border spinner-border-sm d-block" role="status" aria-label={t('common.loading')} />
            ) : usersDebuffs.length === 0 ? (
              <span className="text-body-secondary">{t('admin.debuffs.empty')}</span>
            ) : (
              usersDebuffs.map((debuff) => (
                <div key={debuff.id} className="d-flex align-items-center flex-wrap gap-2 mb-1">
                  <span className="badge text-bg-secondary">{t(`admin.debuffs.types.${debuff.type}`)}</span>
                  {debuffField(debuff.solrFieldName)}
                  <span className="values-list">
                    {debuff.values.map((value) => (
                      <span key={value} className="values-input-value">
                        {value}
                      </span>
                    ))}
                  </span>
                  <DeleteButton
                    label={t('admin.debuffs.deleteLabel', { field: debuff.solrFieldName })}
                    disabled={saving}
                    onClick={() => removeUsersDebuffs(debuff.id)}
                  />
                </div>
              ))
            )}
          </div>
          <UsersDebuffsPopup
            open={debuffPopupOpen}
            usersGuid={guid}
            onCreated={() => {
              setDebuffPopupOpen(false)
              reloadUsersDebuffs()
            }}
            onClose={() => setDebuffPopupOpen(false)}
          />
          <ChangeUsersGuidPopup
            open={changingGuid}
            users={users}
            onChanged={showChangedUsers}
            onClose={() => setChangingGuid(false)}
          />
        </>
      )}
    </ContentBox>
  )
}
