import { ContentBox } from '../../components/ContentBox.tsx'

/** Eine Nachricht, wie die Unternehmensnews sie später zeigen sollen */
interface PlaceholderNews {
  id: number
  date: string
  title: string
  teaser: string
  author: string
}

/** Platzhalter, bis es die News gibt */
const PLACEHOLDER_NEWS: PlaceholderNews[] = [
  {
    id: 1,
    date: '15.10.2026',
    title: 'Neue Prüfeinstellungen in der Artikelmappe',
    teaser: 'Der Tab „Prüfungen“ zeigt jetzt Prüfplan und Merkmale direkt aus ABAS, ohne Umweg über das Infosystem.',
    author: 'IT',
  },
  {
    id: 2,
    date: '13.10.2026',
    title: 'Inventur am 30. Oktober',
    teaser: 'Das Lager ist am 30.10. ganztägig geschlossen. Entnahmen bitte bis zum 29.10. buchen.',
    author: 'Logistik',
  },
  {
    id: 3,
    date: '10.10.2026',
    title: 'Messe in Stuttgart: Standdienst gesucht',
    teaser: 'Für die drei Messetage im November suchen wir noch zwei Kolleginnen oder Kollegen für den Stand.',
    author: 'Vertrieb',
  },
]

/**
 * Unternehmensnews auf der Landingpage (User 2026-10-16), Vorschau mit Platzhaltern: kurze Meldungen mit Datum,
 * Titel, Anriss und Absender, neueste zuerst. Pflegen kommt später.
 */
export function ProfileNews() {
  return (
    <ContentBox
      title={
        <>
          Unternehmensnews <span className="profile-preview-chip">Vorschau</span>
        </>
      }
      footer="Vorschau mit Platzhaltern, die News pflegt später das Marketing."
    >
      <ul className="profile-news-list">
        {PLACEHOLDER_NEWS.map((news) => (
          <li key={news.id} className="profile-news-entry">
            <span className="profile-news-date">{news.date}</span>
            <div className="profile-news-body">
              <span className="profile-news-title">{news.title}</span>
              <p className="profile-news-teaser">{news.teaser}</p>
              <span className="profile-news-author">{news.author}</span>
            </div>
          </li>
        ))}
      </ul>
    </ContentBox>
  )
}
