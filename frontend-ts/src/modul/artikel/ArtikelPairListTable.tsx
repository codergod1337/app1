import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import type { OriginState } from '../../components/Origin.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { artikelFieldLabel, artikelPairRows, artikelPath } from './Artikel.ts'
import { ARTIKEL_PAIR_LIST_LINK_FIELD_NAMES } from './artikelFieldSections.ts'

interface ArtikelPairListTableProps {
  title: string
  /** das Dokument des Artikels, die Felder sind index-parallele Listen */
  document: Record<string, unknown>
  /** die Felder in Spaltenreihenfolge; die Beschriftung kommt aus der Felddefinition */
  fieldNames: readonly string[]
  emptyText: string
  /** die Herkunft für den Sprung in eine andere Mappe */
  origin: OriginState
}

/** Eine Zeile einer Paarliste: ihre Position als Schlüssel und je Spalte der Wert */
interface PairRow {
  index: number
  values: string[]
}

/**
 * Eine Paarliste in einer ContentBox, nach PaarTabelle der Artikelmappe der Vorlage: index-parallele multiValued-Felder
 * als Zeilen der zentralen Table. Eine Spalte mit einer hauseigenen Artikelnummer (Stückliste, Referenzen) ist ein
 * Weg in deren Mappe. Dieselbe Komponente für Stückliste, Arbeitsgänge, Referenzen, Kunden, Lieferanten und Hersteller.
 */
export function ArtikelPairListTable({ title, document, fieldNames, emptyText, origin }: ArtikelPairListTableProps) {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const navigate = useNavigate()
  const { solrFields } = useMasterData()

  const rows: PairRow[] = artikelPairRows(document, fieldNames).map((values, index) => ({ index, values }))

  const columns: TableColumn<PairRow>[] = fieldNames.map((fieldName, column) => ({
    header: artikelFieldLabel(solrFields, fieldName, undefined, language),
    cell: (row) => {
      const value = row.values[column]
      if (value !== '' && ARTIKEL_PAIR_LIST_LINK_FIELD_NAMES.includes(fieldName)) {
        return (
          <button type="button" className="btn btn-link btn-sm p-0 border-0" onClick={() => navigate(artikelPath(value), { state: origin })}>
            {value}
          </button>
        )
      }
      return value
    },
    // die erste Spalte bleibt auch am Handy, der Rest geht ins Detail-Popup
    unimportant: column > 0 && fieldNames.length > 2,
  }))

  return (
    <ContentBox title={title} footer={<span>{t('artikel.pairList.count', { count: rows.length })}</span>}>
      <Table columns={columns} rows={rows} rowKey={(row) => String(row.index)} detailTitle={(row) => row.values[0] ?? ''} emptyText={emptyText} />
    </ContentBox>
  )
}
