import { useCallback, useState, type CSSProperties, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { useLoad } from '../../api/useLoad.ts'
import type { IconName } from '../../branding/icons.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { useLanguage } from '../../components/languageContext.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { entityStatusName } from '../../components/entityStatus.ts'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import type { OriginState } from '../../components/Origin.ts'
import { PasswordButton } from '../../components/PasswordButton.tsx'
import { AccessRoleBadge } from '../accessrole/AccessRoleBadge.tsx'
import {
  endAllCurrentUsersSessions,
  endCurrentUsersSession,
  getCurrentUsersSessions,
  type UsersSessionForClient,
} from '../login/Session.ts'
import { useSession } from '../login/sessionContext.ts'
import { ChangeUsersPasswordPopup } from '../users/ChangeUsersPasswordPopup.tsx'
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

/** Zeitpunkte der Sitzungen, einheitlich kurz */
function formatSessionTime(isoTime: string) {
  return new Date(isoTime).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' })
}

interface ProfileOverviewProps {
  /**
   * Die guid eines anderen Users: dann ist es sein Profil (User 2026-10-10), dieselbe Seite ohne das, was nur dem
   * Angemeldeten gehört: kein Bearbeiten, kein Konto, keine Sicherheit, keine Sitzungen, keine Rechte, kein Gästebuch.
   * Fehlt sie oder ist es die eigene, ist es das eigene Profil.
   */
  usersGuid?: string
}

/**
 * Das Profil: oben der Profilkopf, darunter links Kontakt, Konto, Sicherheit und Sitzungen, rechts „Über mich“, die
 * Rechte und das Gästebuch. Bearbeitet wird je Entity auf einer eigenen Seite (Users oder UsersDetails). Mit einer
 * fremden guid dieselbe Seite als fremdes Profil, nur mit dem, was das Backend über andere herausgibt.
 */
export function ProfileOverview({ usersGuid: requestedUsersGuid }: ProfileOverviewProps = {}) {
  const navigate = useNavigate()
  const { sessionInfo, reloadSession } = useSession()
  const { accessRoles } = useMasterData()
  const ownUsersGuid = sessionInfo?.usersGuid ?? ''
  const usersGuid = requestedUsersGuid ?? ownUsersGuid
  const own = usersGuid === ownUsersGuid
  const loadUsers = useCallback(() => getUsersByGuid(usersGuid), [usersGuid])
  const { data: users, errorMessage: usersErrorMessage } = useLoad(loadUsers)
  const loadUsersDetails = useCallback(() => getUsersDetailsByUsersGuid(usersGuid), [usersGuid])
  const { data: usersDetails, errorMessage: usersDetailsErrorMessage } = useLoad(loadUsersDetails)
  const { data: sessions, errorMessage: sessionsErrorMessage, reload: reloadSessions } = useLoad(getCurrentUsersSessions)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)
  const [changingPassword, setChangingPassword] = useState(false)
  // Sicherheitsabfragen vor dem Beenden: null bzw. false, solange keine offen ist
  const [sessionToEnd, setSessionToEnd] = useState<UsersSessionForClient | null>(null)
  const [endingAllSessions, setEndingAllSessions] = useState(false)

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
        onEdit={own ? () => navigate('/profile/edit/users', { state: ORIGIN_STATE }) : undefined}
      />

      <div className="profile-grid">
        <div className="profile-column">
          <div className="profile-box-contact">
            <ContentBox
              title="Kontakt"
              actions={
                own ? (
                  <EditButton label="Kontakt bearbeiten" onClick={() => navigate('/profile/edit/details', { state: ORIGIN_STATE })} />
                ) : undefined
              }
              footer={
                usersDetailsErrorMessage !== null ? (
                  <span className="text-danger">{usersDetailsErrorMessage}</span>
                ) : own ? (
                  'Andere sehen das in deinem Profil, wenn sie die Rolle USER_DETAIL haben.'
                ) : undefined
              }
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
              {own && usersDetails !== null && (usersDetails.kundenNummer !== null || usersDetails.lieferantenNummer !== null) && (
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

          {own && (
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
          )}

          {own && (
          <div className="profile-box-security">
            {/* Nur das Passwort: Ein Konto löscht allein der Admin, nie der User selbst. */}
            <ContentBox title="Sicherheit" footer="Ein Passwortwechsel beendet alle Sitzungen, auch diese.">
              <div className="profile-security">
                <div className="profile-security-row">
                  <span className="profile-security-text">
                    <strong>Passwort</strong>
                    <span className="small">Danach meldest du dich auf allen Geräten neu an.</span>
                  </span>
                  <PasswordButton label="Passwort ändern" onClick={() => setChangingPassword(true)} />
                </div>
              </div>
            </ContentBox>
          </div>
          )}

          {own && (
          <div className="profile-box-sessions">
            <ContentBox
              title={
                <>
                  Sitzungen {sessions !== null && <span className="profile-count-chip">{sessions.length}</span>}
                </>
              }
              footer={
                sessionsErrorMessage !== null ? (
                  <span className="text-danger">{sessionsErrorMessage}</span>
                ) : (
                  'Ein Platz je Gerät. Beendest du dieses Fenster oder alle Sitzungen, landest du auf der Anmeldung.'
                )
              }
            >
              {sessions === null && sessionsErrorMessage === null && loadingSpinner}
              {sessions !== null && sessions.length === 0 && <p className="mb-0">Keine Sitzungen.</p>}
              {sessions !== null && sessions.length > 0 && (
                <div className="profile-security">
                  {sessions.map((session) => (
                    <div key={session.sessionNumber} className="profile-security-row">
                      <span className="profile-security-text">
                        <strong>
                          Platz {session.sessionNumber}
                          {session.current && ' · dieses Fenster'}
                        </strong>
                        <span className="small">
                          {session.area} · angemeldet seit {formatSessionTime(session.loggedInSince)} · zuletzt erneuert{' '}
                          {formatSessionTime(session.lastRefreshedAt)} ({session.refreshCount}-mal) · läuft ab{' '}
                          {formatSessionTime(session.expiresAt)}
                        </span>
                      </span>
                      <DeleteButton label={`Platz ${session.sessionNumber} beenden`} onClick={() => setSessionToEnd(session)}>
                        Beenden
                      </DeleteButton>
                    </div>
                  ))}
                  <div className="profile-danger-zone">
                    <span className="profile-security-text">
                      <strong>Alle Sitzungen beenden</strong>
                      <span className="small">Auf allen Geräten, auch hier. Fremde Fenster fliegen sofort.</span>
                    </span>
                    <DeleteButton label="Alle Sitzungen beenden" onClick={() => setEndingAllSessions(true)}>
                      Alle beenden
                    </DeleteButton>
                  </div>
                </div>
              )}
            </ContentBox>
          </div>
          )}
        </div>

        <div className="profile-column">
          <div className="profile-box-about">
            <ContentBox
              title={own ? 'Über mich' : 'Über'}
              actions={
                own ? (
                  <EditButton label="Über mich bearbeiten" onClick={() => navigate('/profile/edit/details', { state: ORIGIN_STATE })} />
                ) : undefined
              }
              language={{ value: language, onChange: setLanguage }}
            >
              {usersDetails === null && usersDetailsErrorMessage === null && loadingSpinner}
              {usersDetails !== null && (profilText !== '' ? <p className="profile-text">{profilText}</p> : <p className="mb-0">Noch kein Text.</p>)}
            </ContentBox>
          </div>

          {own && (
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
          )}

          {own && (
          <div className="profile-box-guestbook">
            <ProfileGuestbook
              ownInitials={users ? usersInitials(users) : ''}
              ownAvatarBackground={usersAvatarBackground(usersGuid)}
            />
          </div>
          )}
        </div>
      </div>

      <ChangeUsersPasswordPopup open={changingPassword} usersGuid={usersGuid} onClose={() => setChangingPassword(false)} />
      <DeletePopup
        open={sessionToEnd !== null}
        title="Sitzung beenden"
        onConfirm={() => endCurrentUsersSession(sessionToEnd?.sessionNumber ?? 0)}
        onDeleted={() => {
          // die eigene: Die Cookies sind weg, die Sitzung neu laden führt zur Anmeldung. Eine fremde: nur die Liste
          if (sessionToEnd?.current) {
            reloadSession()
          } else {
            reloadSessions()
          }
        }}
        onClose={() => setSessionToEnd(null)}
      >
        Soll <strong>Platz {sessionToEnd?.sessionNumber}</strong>
        {sessionToEnd?.current ? ' (dieses Fenster)' : ''} wirklich beendet werden? Das Gerät muss sich danach neu anmelden.
      </DeletePopup>
      <DeletePopup
        open={endingAllSessions}
        title="Alle Sitzungen beenden"
        onConfirm={endAllCurrentUsersSessions}
        onDeleted={reloadSession}
        onClose={() => setEndingAllSessions(false)}
      >
        Sollen wirklich <strong>alle Sitzungen</strong> beendet werden, auch dieses Fenster? Du landest danach auf der
        Anmeldung, alle anderen Geräte sofort auch.
      </DeletePopup>
    </div>
  )
}
