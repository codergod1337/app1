import { Link } from 'react-router'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'

/** Ein Artikel, den der User zuletzt offen hatte, wie die History ihn später liefert */
interface PlaceholderArticle {
  artikelNummer: string
  name: string
  time: string
  /** was er dort getan hat */
  action: string
}

/** Platzhalter, bis es die Endpunkt-History gibt */
const PLACEHOLDER_ARTICLES: PlaceholderArticle[] = [
  { artikelNummer: '10001234', name: 'Typenschild 100x50 Polyesterfolie', time: 'heute, 09:12', action: 'Mappe geöffnet' },
  { artikelNummer: '10000871', name: 'Frontplatte Alu eloxiert 2 mm', time: 'heute, 08:40', action: 'Prüfungen angesehen' },
  { artikelNummer: '10002207', name: 'Kabelmarkierer gelb 4 mm', time: 'gestern, 16:05', action: 'Stückliste angesehen' },
  { artikelNummer: '10000119', name: 'Gehäuse IP65 200x150x80', time: 'gestern, 11:21', action: 'Dokumente angesehen' },
  { artikelNummer: '10001980', name: 'Folientastatur 12 Tasten', time: 'Mo, 14:48', action: 'Mappe geöffnet' },
]

const COLUMNS: TableColumn<PlaceholderArticle>[] = [
  {
    header: 'Artikel',
    cell: (row) => (
      <Link to={`/artikel/${encodeURIComponent(row.artikelNummer)}`}>
        <span className="font-monospace">{row.artikelNummer}</span> {row.name}
      </Link>
    ),
  },
  { header: 'Was', width: '11rem', unimportant: true, cell: (row) => row.action },
  { header: 'Wann', width: '8rem', cell: (row) => row.time },
]

/**
 * Die eigene History an Artikeln auf der Landingpage (User 2026-10-16), Vorschau mit Platzhaltern: welche Mappen der
 * User zuletzt offen hatte und was er dort getan hat, neueste zuerst. Die Daten kommen später aus der Endpunkt-History
 * des Anfragenmonitors, aufbereitet nach Fachbegriffen statt roher URLs.
 */
export function ProfileArticleHistory() {
  return (
    <ContentBox
      title={
        <>
          Zuletzt bei Artikeln <span className="profile-preview-chip">Vorschau</span>
        </>
      }
      footer="Vorschau mit Platzhaltern, die History kommt aus dem Anfragenmonitor."
    >
      <Table columns={COLUMNS} rows={PLACEHOLDER_ARTICLES} rowKey={(row) => row.artikelNummer} detailTitle={(row) => row.name} emptyText="" />
    </ContentBox>
  )
}
