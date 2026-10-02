import type { CSSProperties } from 'react'
import { branding } from '../../branding/branding.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'

/** Ein Eintrag, wie das Gästebuch ihn später zeigen soll */
interface PlaceholderEntry {
  id: number
  /** null: der Verfasser hat sein Konto gelöscht */
  author: string | null
  initials: string
  avatarBackground: string
  /** vom System-User, z. B. die Begrüßung */
  system?: boolean
  time: string
  text: string
}

/** Platzhalter, bis es das Gästebuch gibt */
const PLACEHOLDER_ENTRIES: PlaceholderEntry[] = [
  {
    id: 1,
    author: 'Mara Schulz',
    initials: 'MS',
    avatarBackground: 'linear-gradient(135deg, #14b8a6, #0f766e)',
    time: 'vor 2 Stunden',
    text: 'Danke für die Hilfe beim Einrichten des Pi! Läuft jetzt seit drei Wochen ohne einen einzigen Neustart.',
  },
  {
    id: 2,
    author: 'Tim Köhler',
    initials: 'TK',
    avatarBackground: 'linear-gradient(135deg, #f59e0b, #b45309)',
    time: 'gestern, 21:14',
    text: 'Samstag wieder eine Runde im Koop? Ich bring die Snacks mit.',
  },
  {
    id: 3,
    author: null,
    initials: '',
    avatarBackground: '',
    time: 'vor 3 Monaten',
    text: 'Viel Erfolg mit dem neuen System!',
  },
  {
    id: 4,
    author: 'System',
    initials: '',
    avatarBackground: '',
    system: true,
    time: 'bei der Anmeldung',
    text: `Willkommen bei ${branding.companyName}! Dein Konto ist eingerichtet.`,
  },
]

function EntryAvatar({ entry }: { entry: PlaceholderEntry }) {
  if (entry.system) {
    return (
      <span className="profile-guestbook-avatar profile-guestbook-avatar-system">
        <Icon name="admin" />
      </span>
    )
  }
  if (entry.author === null) {
    return (
      <span className="profile-guestbook-avatar profile-guestbook-avatar-deleted">
        <Icon name="userDeleted" />
      </span>
    )
  }
  return (
    <span className="profile-guestbook-avatar" style={{ '--profile-avatar-bg': entry.avatarBackground } as CSSProperties}>
      {entry.initials}
    </span>
  )
}

/**
 * Vorschau des Gästebuchs mit Platzhaltern: So sollen Einträge später aussehen, auch die von gelöschten Konten und
 * vom System. Schreiben geht noch nicht.
 */
export function ProfileGuestbook({ ownInitials, ownAvatarBackground }: { ownInitials: string; ownAvatarBackground: string }) {
  return (
    <ContentBox
      title={
        <>
          Gästebuch <span className="profile-preview-chip">Vorschau</span>
        </>
      }
      footer="Vorschau mit Platzhaltern, das Gästebuch kommt später."
    >
      <div className="profile-guestbook-composer">
        <span className="profile-guestbook-avatar" style={{ '--profile-avatar-bg': ownAvatarBackground } as CSSProperties}>
          {ownInitials}
        </span>
        <div className="profile-guestbook-input">
          <label className="visually-hidden" htmlFor="profile-guestbook-text">
            Neuer Eintrag
          </label>
          <textarea id="profile-guestbook-text" className="form-control" rows={2} placeholder="Schreib etwas in dein Gästebuch …" disabled />
          <Hoverlay text="Eintragen kommt mit dem Gästebuch." alignRight>
            <button type="button" className="button-save" disabled>
              <Icon name="send" /> Eintragen
            </button>
          </Hoverlay>
        </div>
      </div>
      <ul className="profile-guestbook-list">
        {PLACEHOLDER_ENTRIES.map((entry) => (
          <li key={entry.id} className="profile-guestbook-entry">
            <EntryAvatar entry={entry} />
            <div className="profile-guestbook-body">
              <div className="profile-guestbook-head">
                <span className={entry.author === null ? 'profile-guestbook-author profile-guestbook-author-deleted' : 'profile-guestbook-author'}>
                  {entry.author ?? 'Gelöschtes Konto'}
                </span>
                <span className="profile-guestbook-time">{entry.time}</span>
              </div>
              <p className="profile-guestbook-text">{entry.text}</p>
            </div>
          </li>
        ))}
      </ul>
    </ContentBox>
  )
}
