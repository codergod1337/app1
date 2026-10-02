import { useState, type CSSProperties } from 'react'
import { ContentBox } from '../../components/ContentBox.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { translate } from '../../components/multilingual.ts'
import type { Users } from '../users/Users.ts'
import type { UsersDetails } from '../users/UsersDetails.ts'
import { ProfileCover } from './ProfileCover.tsx'
import { membershipLength, usersAvatarBackground, usersDisplayName, usersInitials } from './profileDisplay.ts'

/** Farbe des Rings um die Jahre, wechselt mit jedem Jahr, ab dem fünften bleibt sie */
const YEARS_RING_COLORS = ['#cbd5e1', '#a5b4fc', '#67e8f9', '#86efac', '#fde047', '#fbbf24']

interface ProfileHeroProps {
  /** null, solange er lädt */
  users: Users | null
  /** null, solange sie laden */
  usersDetails: UsersDetails | null
  sessionNumber: number
  /** null, solange die Stammdaten laden */
  accessRoleCount: number | null
  errorMessage: string | null
  onEdit: () => void
}

/**
 * Der Kopf des Profils als ContentBox: oben Titel und Bearbeiten, in der Mitte Titelbild, Profilbild, Name und info,
 * unten die Kennzahlen.
 */
export function ProfileHero({ users, usersDetails, sessionNumber, accessRoleCount, errorMessage, onEdit }: ProfileHeroProps) {
  // einmal beim Öffnen, für die Dienstjahre genügt das
  const [now] = useState(() => new Date())
  const createdAt = users ? new Date(users.createdAt) : null
  const membership = createdAt ? membershipLength(createdAt, now) : null
  const info = translate(usersDetails?.info ?? null)

  let footer
  if (errorMessage !== null) {
    footer = <span className="text-danger">{errorMessage}</span>
  } else if (users === null || createdAt === null) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
  } else {
    footer = (
      <div className="profile-stats">
        <div className="profile-stat">
          <Icon name="calendar" />
          <span className="profile-stat-text">
            <span className="profile-stat-label">Dabei seit</span>
            <span className="profile-stat-value">{createdAt.toLocaleDateString('de-DE')}</span>
          </span>
        </div>
        <div className="profile-stat">
          <Icon name="accessRoles" />
          <span className="profile-stat-text">
            <span className="profile-stat-label">Rechte</span>
            <span className="profile-stat-value">{accessRoleCount ?? '…'} AccessRoles</span>
          </span>
        </div>
        <div className="profile-stat">
          <Icon name="session" />
          <span className="profile-stat-text">
            <span className="profile-stat-label">Sitzungsplatz</span>
            <span className="profile-stat-value">#{sessionNumber} von 3</span>
          </span>
        </div>
      </div>
    )
  }

  return (
    <div className="profile-hero">
      <ContentBox title="Dein Profil" actions={<EditButton label="Username und Namen bearbeiten" onClick={onEdit} />} footer={footer}>
        <div className="profile-cover">
          {users && <ProfileCover seed={users.guid} />}
          {createdAt && membership && (
            <Hoverlay
              text={`Dabei seit ${createdAt.toLocaleDateString('de-DE')}: ${membership.years} ${membership.years === 1 ? 'Jahr' : 'Jahre'} und ${membership.months} ${membership.months === 1 ? 'Monat' : 'Monate'}`}
              alignRight
            >
              <span className="profile-years" tabIndex={0}>
                <span
                  className="profile-years-ring"
                  style={{ '--profile-years-ring': YEARS_RING_COLORS[Math.min(membership.years, YEARS_RING_COLORS.length - 1)] } as CSSProperties}
                >
                  {membership.years > 0 ? membership.years : membership.months}
                </span>
                <span className="profile-years-label">
                  {membership.years > 0
                    ? `${membership.years === 1 ? 'Jahr' : 'Jahre'} dabei`
                    : `${membership.months === 1 ? 'Monat' : 'Monate'} dabei`}
                </span>
              </span>
            </Hoverlay>
          )}
        </div>
        <div className="profile-identity">
          <div
            className="profile-avatar"
            style={users ? ({ '--profile-avatar-bg': usersAvatarBackground(users.guid) } as CSSProperties) : undefined}
            aria-hidden="true"
          >
            {users && usersInitials(users)}
          </div>
          <div className="profile-identity-text">
            <h1 className="profile-name">{users ? usersDisplayName(users) : ' '}</h1>
            <div className="profile-meta">
              {users?.username && <span>@{users.username}</span>}
              <span className="profile-online">
                <span className="profile-online-dot" />
                online · Session #{sessionNumber}
              </span>
            </div>
            {info !== '' && <p className="profile-info">{info}</p>}
          </div>
        </div>
      </ContentBox>
    </div>
  )
}
