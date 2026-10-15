import { branding } from '../../branding/branding.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'

/** Die Nachricht des Tages, wie sie später aus der Datenbank kommt: Verfasser, Zeitpunkt, Text */
const PLACEHOLDER_MESSAGE = {
  author: 'Geschäftsleitung',
  time: 'heute, 07:30',
  text: `Ab Montag läuft die Bestellfreigabe über die Jobs hier im Profil: Wer ein Angebot braucht, erteilt einen Job an die Beschaffung, wer eine Kalkulation prüfen soll, bekommt ihn als Job. Bitte nicht mehr per Mail. Fragen an die IT. Schönes Wochenende, euer ${branding.companyName}-Team.`,
}

/**
 * Message of the day auf der Landingpage (User 2026-10-16), Vorschau mit Platzhalter: eine Nachricht an alle, vom Admin
 * oder der Geschäftsleitung gesetzt, oben über allem. Pflegen kommt später.
 */
export function ProfileMessageOfTheDay() {
  return (
    <ContentBox
      title={
        <>
          Message of the day <span className="profile-preview-chip">Vorschau</span>
        </>
      }
      footer="Vorschau mit Platzhalter, die Nachricht des Tages pflegt später der Admin."
    >
      <div className="profile-motd">
        <span className="profile-motd-icon">
          <Icon name="info" />
        </span>
        <div className="profile-motd-body">
          <p className="profile-motd-text">{PLACEHOLDER_MESSAGE.text}</p>
          <span className="profile-motd-meta">
            {PLACEHOLDER_MESSAGE.author} · {PLACEHOLDER_MESSAGE.time}
          </span>
        </div>
      </div>
    </ContentBox>
  )
}
