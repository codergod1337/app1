import { Link } from 'react-router'
import type { IconName } from '../../branding/icons.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'

/** Was der User mit einem Dokument getan hat */
type DocumentAction = 'angesehen' | 'bearbeitet' | 'heruntergeladen'

const ACTION_ICONS: Record<DocumentAction, IconName> = {
  angesehen: 'view',
  bearbeitet: 'edit',
  heruntergeladen: 'download',
}

/** Ein Dokument, das der User zuletzt angefasst hat, wie die History es später liefert */
interface PlaceholderDocument {
  /** die DMS-id, in echt der sha256 */
  id: string
  fileName: string
  fileSubClass: string
  action: DocumentAction
  time: string
}

/** Platzhalter, bis es die Endpunkt-History gibt */
const PLACEHOLDER_DOCUMENTS: PlaceholderDocument[] = [
  { id: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', fileName: 'datenblatt_rev_c.pdf', fileSubClass: 'Datenblatt', action: 'heruntergeladen', time: 'heute, 10:02' },
  { id: '2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824', fileName: 'angebot_7000012_2026_10.pdf', fileSubClass: 'Angebot', action: 'angesehen', time: 'heute, 09:35' },
  { id: '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08', fileName: 'zeichnung_frontplatte_v3.dwg', fileSubClass: 'Zeichnung', action: 'bearbeitet', time: 'gestern, 15:50' },
  { id: 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', fileName: 'typenschild_foto_01.jpg', fileSubClass: 'Bilder', action: 'angesehen', time: 'gestern, 13:17' },
  { id: '3a7bd3e2360a3d29eea436fcfb7e44c735d117c42d1c1835420b6b9942dd4f1b', fileName: 'pruefbericht_q3.pdf', fileSubClass: 'Prüfbericht', action: 'heruntergeladen', time: 'Mo, 16:40' },
]

const COLUMNS: TableColumn<PlaceholderDocument>[] = [
  {
    header: 'Dokument',
    cell: (row) => <Link to={`/dms/solr-document/${row.id}`}>{row.fileName}</Link>,
  },
  { header: 'Dateiart', width: '8rem', unimportant: true, cell: (row) => row.fileSubClass },
  {
    header: 'Was',
    width: '10rem',
    cell: (row) => (
      <span className="d-inline-flex align-items-center gap-1">
        <Icon name={ACTION_ICONS[row.action]} /> {row.action}
      </span>
    ),
  },
  { header: 'Wann', width: '8rem', unimportant: true, cell: (row) => row.time },
]

/**
 * Die eigene History an Dokumenten auf der Landingpage (User 2026-10-16), Vorschau mit Platzhaltern: was der User
 * zuletzt angesehen, bearbeitet oder heruntergeladen hat, neueste zuerst. Die Daten kommen später aus der
 * Endpunkt-History des Anfragenmonitors.
 */
export function ProfileDocumentHistory() {
  return (
    <ContentBox
      title={
        <>
          Zuletzt bei Dokumenten <span className="profile-preview-chip">Vorschau</span>
        </>
      }
      footer="Vorschau mit Platzhaltern, die History kommt aus dem Anfragenmonitor."
    >
      <Table columns={COLUMNS} rows={PLACEHOLDER_DOCUMENTS} rowKey={(row) => row.id} detailTitle={(row) => row.fileName} emptyText="" />
    </ContentBox>
  )
}
