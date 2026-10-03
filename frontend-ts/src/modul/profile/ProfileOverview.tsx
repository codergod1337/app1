import { useCallback, useState, type CSSProperties, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { useLoad } from '../../api/useLoad.ts'
import type { IconName } from '../../branding/icons.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { useLanguage } from '../../components/languageContext.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { entityStatusName } from '../../components/entityStatus.ts'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import type { OriginState } from '../../components/Origin.ts'
import { PasswordButton } from '../../components/PasswordButton.tsx'
import { AccessRoleBadge } from '../accessrole/AccessRoleBadge.tsx'
import { useSession } from '../login/sessionContext.ts'
import { ChangeUsersPasswordPopup } from '../users/ChangeUsersPasswordPopup.tsx'
import { SoftDeleteUsersPopup } from '../users/SoftDeleteUsersPopup.tsx'
import { getUsersByGuid } from '../users/Users.ts'
import { getUsersDetailsByUsersGuid } from '../users/UsersDetails.ts'
import { ProfileGuestbook } from './ProfileGuestbook.tsx'
import { ProfileHero } from './ProfileHero.tsx'
import { toWebUrl, usersAvatarBackground, usersInitials } from './profileDisplay.ts'

/** Beim Wechsel auf eine Bearbeiten-Seite mitgeben, damit der header „zurück zu Profil“ anbietet */
const ORIGIN_STATE: OriginState = { origin: { path: '/profile', label: 'Profil' } }

/** Eine Zeile unter „Kontakt“ */
interface ContactRow {
  icon: IconName
  label: string
  value: string
  /** Link, falls die Angabe eine Adresse ist */
  href: string | null
  /** Markenfarben, in jedem Theme gleich */
  tint: string
  ink: string
}

/** Kopiert den Wert, z. B. die Handynummer */
function CopyButton({ label, value }: { label: string; value: string }) {
  return (
    <Hoverlay text={label} alignRight>
      <button
        type="button"
        className="button-other"
        aria-label={label}
        onClick={() => navigator.clipboard?.writeText(value).catch(() => undefined)}
      >
        <Icon name="copy" />
      </button>
    </Hoverlay>
  )
}

/** Eine Bezeichnung mit Wert in den Listen von Konto und Kontakt */
function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <>
      <dt>{label}</dt>
      <dd>{children}</dd>
    </>
  )
}

/**
 * Das eigene Profil: oben der Profilkopf, darunter links Kontakt, Konto und Sicherheit, rechts „Über mich“, die Rechte
 * und das Gästebuch. Bearbeitet wird je Entity auf einer eigenen Seite (Users oder UsersDetails).
 */
export function ProfileOverview() {
  const navigate = useNavigate()
  const { sessionInfo } = useSession()
  const { accessRoles } = useMasterData()
  const usersGuid = sessionInfo?.usersGuid ?? ''
  const loadUsers = useCallback(() => getUsersByGuid(usersGuid), [usersGuid])
  const { data: users, errorMessage: usersErrorMessage } = useLoad(loadUsers)
  const loadUsersDetails = useCallback(() => getUsersDetailsByUsersGuid(usersGuid), [usersGuid])
  const { data: usersDetails, errorMessage: usersDetailsErrorMessage } = useLoad(loadUsersDetails)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)
  const [changingPassword, setChangingPassword] = useState(false)
  const [deletingUsers, setDeletingUsers] = useState(false)

  // Die AR kommen aus dem Token, Namen und Farben aus den Stammdaten
  const ownAccessRoles = accessRoles?.filter((accessRole) => sessionInfo?.accessRoleKeys.includes(accessRole.key)) ?? null

  const contactRows: ContactRow[] = []
  if (usersDetails !== null) {
    const { webseite, mobile, discord, steam, insta } = usersDetails
    if (webseite !== null) {
      contactRows.push({ icon: 'website', label: 'Webseite', value: webseite, href: toWebUrl(webseite), tint: '#e0f2fe', ink: '#0369a1' })
    }
    if (mobile !== null) {
      contactRows.push({ icon: 'mobile', label: 'Mobil', value: mobile, href: null, tint: '#dcfce7', ink: '#15803d' })
    }
    if (discord !== null) {
      contactRows.push({ icon: 'discord', label: 'Discord', value: discord, href: null, tint: '#e0e7ff', ink: '#5865f2' })
    }
    if (steam !== null) {
      contactRows.push({ icon: 'steam', label: 'Steam', value: steam, href: toWebUrl(steam), tint: '#e2e8f0', ink: '#171a21' })
    }
    if (insta !== null) {
      contactRows.push({
        icon: 'instagram',
        label: 'Instagram',
        value: insta,
        // Instagram-Namen enthalten oft Punkte und sähen sonst wie eine Webadresse aus
        href: /^https?:\/\//i.test(insta) ? insta : `https://www.instagram.com/${encodeURIComponent(insta.replace(/^@/, ''))}`,
        tint: '#fce7f3',
        ink: '#c13584',
      })
    }
  }

  const profilText = translate(usersDetails?.profilText ?? null, language)
  const loadingSpinner = <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />

  return (
    <div className="profile">
      <ProfileHero
        users={users}
        usersDetails={usersDetails}
        sessionNumber={sessionInfo?.sessionNumber ?? 0}
        accessRoleCount={ownAccessRoles?.length ?? null}
        errorMessage={usersErrorMessage}
        onEdit={() => navigate('/profile/edit/users', { state: ORIGIN_STATE })}
      />

      <div className="profile-grid">
        <div className="profile-column">
          <div className="profile-box-contact">
            <ContentBox
              title="Kontakt"
              actions={
                <EditButton label="Kontakt bearbeiten" onClick={() => navigate('/profile/edit/details', { state: ORIGIN_STATE })} />
              }
              footer={usersDetailsErrorMessage !== null ? <span className="text-danger">{usersDetailsErrorMessage}</span> : 'Andere sehen das erst mit den fremden Profilen.'}
            >
              {usersDetails === null && usersDetailsErrorMessage === null && loadingSpinner}
              {usersDetails !== null && contactRows.length === 0 && <p className="mb-0">Noch keine Kontaktdaten.</p>}
              {contactRows.length > 0 && (
                <ul className="profile-contact-list">
                  {contactRows.map((row) => (
                    <li key={row.label}>
                      <span className="profile-contact-icon" style={{ '--profile-contact-tint': row.tint, '--profile-contact-ink': row.ink } as CSSProperties}>
                        <Icon name={row.icon} />
                      </span>
                      <span className="profile-contact-text">
                        <span className="profile-field-label">{row.label}</span>
                        {row.href !== null ? (
                          <a className="profile-contact-value" href={row.href} target="_blank" rel="noopener noreferrer">
                            {row.value}
                          </a>
                        ) : (
                          <span className="profile-contact-value">{row.value}</span>
                        )}
                      </span>
                      {row.href === null && <CopyButton label={`${row.label} kopieren`} value={row.value} />}
                    </li>
                  ))}
                </ul>
              )}
              {usersDetails !== null && (usersDetails.kundenNummer !== null || usersDetails.lieferantenNummer !== null) && (
                <div className="profile-admin-managed">
                  <div className="profile-admin-managed-title">
                    <Icon name="locked" /> Vom Admin verwaltet
                  </div>
                  <dl className="profile-fields">
                    <Field label="Kundennummer">{usersDetails.kundenNummer ?? 'keine'}</Field>
                    <Field label="Lieferantennummer">{usersDetails.lieferantenNummer ?? 'keine'}</Field>
                  </dl>
                </div>
              )}
            </ContentBox>
          </div>

          <div className="profile-box-account">
            <ContentBox
              title="Konto"
              actions={
                <span className="profile-private-chip">
                  <Icon name="private" /> nur für dich
                </span>
              }
              footer="Mit der E-Mail meldest du dich an. Ändern kann sie nur der Admin."
            >
              {users === null ? (
                loadingSpinner
              ) : (
                <dl className="profile-fields">
                  <Field label="E-Mail">{users.email}</Field>
                  <Field label="guid">
                    <span className="profile-guid">
                      <span className="font-monospace small">{users.guid}</span>
                      <CopyButton label="guid kopieren" value={users.guid} />
                    </span>
                  </Field>
                  <Field label="Angelegt">{new Date(users.createdAt).toLocaleString('de-DE')}</Field>
                  <Field label="Status">{entityStatusName(users.status)}</Field>
                </dl>
              )}
            </ContentBox>
          </div>

          <div className="profile-box-security">
            <ContentBox title="Sicherheit" footer="Passwortwechsel und Löschen beenden alle Sitzungen, auch diese.">
              <div className="profile-security">
                <div className="profile-security-row">
                  <span className="profile-security-text">
                    <strong>Passwort</strong>
                    <span className="small">Danach meldest du dich auf allen Geräten neu an.</span>
                  </span>
                  <PasswordButton label="Passwort ändern" onClick={() => setChangingPassword(true)} />
                </div>
                <div className="profile-danger-zone">
                  <span className="profile-security-text">
                    <strong>Konto löschen</strong>
                    <span className="small">Dein Konto wird deaktiviert. Anmelden geht danach nicht mehr.</span>
                  </span>
                  <DeleteButton label="Konto löschen" onClick={() => setDeletingUsers(true)}>
                    Löschen
                  </DeleteButton>
                </div>
              </div>
            </ContentBox>
          </div>
        </div>

        <div className="profile-column">
          <div className="profile-box-about">
            <ContentBox
              title="Über mich"
              actions={
                <EditButton label="Über mich bearbeiten" onClick={() => navigate('/profile/edit/details', { state: ORIGIN_STATE })} />
              }
              language={{ value: language, onChange: setLanguage }}
            >
              {usersDetails === null && usersDetailsErrorMessage === null && loadingSpinner}
              {usersDetails !== null && (profilText !== '' ? <p className="profile-text">{profilText}</p> : <p className="mb-0">Noch kein Text.</p>)}
            </ContentBox>
          </div>

          <div className="profile-box-rights">
            <ContentBox
              title={
                <>
                  Rechte {ownAccessRoles !== null && <span className="profile-count-chip">{ownAccessRoles.length}</span>}
                </>
              }
              footer="Rechte vergibt der Admin."
            >
              {ownAccessRoles === null && loadingSpinner}
              {ownAccessRoles !== null && ownAccessRoles.length === 0 && <p className="mb-0">Keine AccessRoles.</p>}
              {ownAccessRoles !== null && ownAccessRoles.length > 0 && (
                <div className="profile-showcase">
                  {ownAccessRoles.map((accessRole) => (
                    <div key={accessRole.key} className="profile-showcase-item">
                      <AccessRoleBadge badge={accessRole} size="compact" />
                      <span className="profile-showcase-text">
                        <span className="profile-showcase-name">{translate(accessRole.displayName)}</span>
                        {accessRole.description !== null && <span className="small">{translate(accessRole.description)}</span>}
                        <span className="profile-showcase-key">{accessRole.key}</span>
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </ContentBox>
          </div>

          <div className="profile-box-guestbook">
            <ProfileGuestbook
              ownInitials={users ? usersInitials(users) : ''}
              ownAvatarBackground={usersAvatarBackground(usersGuid)}
            />
          </div>
        </div>
      </div>

      <ChangeUsersPasswordPopup open={changingPassword} usersGuid={usersGuid} onClose={() => setChangingPassword(false)} />
      <SoftDeleteUsersPopup open={deletingUsers} usersGuid={usersGuid} onClose={() => setDeletingUsers(false)} />
    </div>
  )
}
