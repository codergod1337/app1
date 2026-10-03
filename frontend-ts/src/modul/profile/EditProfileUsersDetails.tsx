import { useCallback, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { useLoad } from '../../api/useLoad.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { useLanguage } from '../../components/languageContext.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { MultilingualInput } from '../../components/MultilingualInput.tsx'
import { useOrigin } from '../../components/Origin.ts'
import { useSession } from '../login/sessionContext.ts'
import { getUsersDetailsByUsersGuid, updateUsersDetails, type UsersDetails, type UsersDetailsData } from '../users/UsersDetails.ts'

const FORM_ID = 'edit-profile-users-details-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/profile'

/** Die Kontaktfelder mit ihrer Länge wie in UsersDetails.java */
const CONTACT_FIELDS: { field: 'webseite' | 'mobile' | 'steam' | 'discord' | 'insta'; label: string; maxLength: number }[] = [
  { field: 'webseite', label: 'Webseite', maxLength: 255 },
  { field: 'mobile', label: 'Mobil', maxLength: 50 },
  { field: 'discord', label: 'Discord', maxLength: 100 },
  { field: 'steam', label: 'Steam', maxLength: 100 },
  { field: 'insta', label: 'Instagram', maxLength: 100 },
]

interface ProfileUsersDetailsFormProps {
  usersDetails: UsersDetails
  /** Sprache von info und profilText, gewählt über die Flaggen der ContentBox */
  language: LanguageCode
  disabled: boolean
  onSubmit: (usersDetailsData: UsersDetailsData) => void
}

/** Die Felder, startet mit den geladenen Werten */
function ProfileUsersDetailsForm({ usersDetails, language, disabled, onSubmit }: ProfileUsersDetailsFormProps) {
  const [usersDetailsData, setUsersDetailsData] = useState<UsersDetailsData>({
    info: usersDetails.info,
    profilText: usersDetails.profilText,
    webseite: usersDetails.webseite,
    mobile: usersDetails.mobile,
    steam: usersDetails.steam,
    discord: usersDetails.discord,
    insta: usersDetails.insta,
  })

  function change<K extends keyof UsersDetailsData>(field: K, value: UsersDetailsData[K]) {
    setUsersDetailsData((current) => ({ ...current, [field]: value }))
  }

  function submitUsersDetails(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit(usersDetailsData)
  }

  return (
    <form id={FORM_ID} onSubmit={submitUsersDetails}>
      <fieldset className="row g-3" disabled={disabled}>
        <div className="col-12">
          <label className="form-label" htmlFor={`${FORM_ID}-info`}>
            Info
          </label>
          <MultilingualInput
            id={`${FORM_ID}-info`}
            value={usersDetailsData.info}
            language={language}
            maxLength={200}
            onChange={(info) => change('info', info)}
          />
          <div className="form-text">Ein Satz, steht unter deinem Namen.</div>
        </div>
        <div className="col-12">
          <label className="form-label" htmlFor={`${FORM_ID}-profil-text`}>
            Über mich
          </label>
          <MultilingualInput
            id={`${FORM_ID}-profil-text`}
            value={usersDetailsData.profilText}
            language={language}
            multiline
            onChange={(profilText) => change('profilText', profilText)}
          />
        </div>
        {CONTACT_FIELDS.map(({ field, label, maxLength }) => (
          <div key={field} className="col-md-6">
            <label className="form-label" htmlFor={`${FORM_ID}-${field}`}>
              {label}
            </label>
            <input
              id={`${FORM_ID}-${field}`}
              className="form-control"
              maxLength={maxLength}
              value={usersDetailsData[field] ?? ''}
              // leer heißt geleert
              onChange={(event) => change(field, event.target.value === '' ? null : event.target.value)}
            />
          </div>
        ))}
      </fieldset>
    </form>
  )
}

/**
 * Der User ändert info, profilText (beide mehrsprachig, Sprache über die Flaggen) und seine Kontaktdaten.
 * Kunden- und Lieferantennummer setzt nur der Admin. Abbrechen und Speichern springen zum origin.
 */
export function EditProfileUsersDetails() {
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const usersGuid = useSession().sessionInfo?.usersGuid ?? ''
  const loadUsersDetails = useCallback(() => getUsersDetailsByUsersGuid(usersGuid), [usersGuid])
  const { data: usersDetails, errorMessage: loadErrorMessage } = useLoad(loadUsersDetails)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)

  function saveUsersDetails(changedUsersDetailsData: UsersDetailsData) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateUsersDetails(usersGuid, changedUsersDetailsData)
      .then(() => navigate(originPath))
      .catch((error: unknown) => {
        setSaveErrorMessage(error instanceof RequestError ? error.message : 'Unbekannter Fehler')
        setSaving(false)
      })
  }

  const errorMessage = loadErrorMessage ?? saveErrorMessage
  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {usersDetails === null && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
      )}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={() => navigate(originPath)}>
          Abbrechen
        </button>
        <button type="submit" form={FORM_ID} className="button-save" disabled={saving || usersDetails === null}>
          Speichern
        </button>
      </span>
    </>
  )

  return (
    <ContentBox title="Über mich und Kontakt bearbeiten" footer={footer} language={{ value: language, onChange: setLanguage }}>
      {/* Erst mit den geladenen Daten anzeigen, damit das Formular mit ihnen startet */}
      {usersDetails !== null && (
        <ProfileUsersDetailsForm
          usersDetails={usersDetails}
          language={language}
          disabled={saving}
          onSubmit={saveUsersDetails}
        />
      )}
    </ContentBox>
  )
}
