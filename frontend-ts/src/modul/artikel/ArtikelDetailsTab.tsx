import { useTranslation } from 'react-i18next'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { isSolrFieldOfCore, isSolrHookField } from '../solrmanager/SolrField.ts'
import { ARTIKEL_CORE_KEY, artikelFieldLabel, artikelValuesOf } from './Artikel.ts'
import { ARTIKEL_FIELD_NAMES_SHOWN_ELSEWHERE, ARTIKEL_FIELD_SECTIONS, type ArtikelFieldSectionKey } from './artikelFieldSections.ts'

interface ArtikelDetailsTabProps {
  document: Record<string, unknown>
}

/** Eine Zeile einer Sektion: technischer Name, Beschriftung, Werte */
interface DetailRow {
  fieldName: string
  label: string
  values: string[]
}

/**
 * Der Details-Tab, nach dem DetailsTab der Vorlage: die Felder in Sektionen, leere Felder fallen weg, leere Sektionen
 * auch. Zuletzt „Übrige Felder“: jedes normale Feld des Kerns aus den Stammdaten, das keine Sektion nennt und kein
 * Tab zeigt, damit beim Durchgehen nichts verborgen bleibt (User 2026-10-10). Der technische Name steht neben der
 * Beschriftung, denn er ist der ABAS-Name und sagt, woher der Wert kommt.
 */
export function ArtikelDetailsTab({ document }: ArtikelDetailsTabProps) {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const { solrFields } = useMasterData()

  function rowsOf(fieldNames: readonly string[]): DetailRow[] {
    return fieldNames
      .map((fieldName) => ({ fieldName, label: artikelFieldLabel(solrFields, fieldName, undefined, language), values: artikelValuesOf(document[fieldName]) }))
      .filter((row) => row.values.length > 0 && row.values.some((value) => value !== ''))
  }

  const namedFieldNames = new Set<string>([...ARTIKEL_FIELD_NAMES_SHOWN_ELSEWHERE, ...ARTIKEL_FIELD_SECTIONS.flatMap((section) => section.fieldNames)])
  const remainingFieldNames = (solrFields ?? [])
    .filter((solrField) => isSolrFieldOfCore(solrField, ARTIKEL_CORE_KEY) && !isSolrHookField(solrField) && !namedFieldNames.has(solrField.name))
    .map((solrField) => solrField.name)

  const allSections: { key: ArtikelFieldSectionKey; rows: DetailRow[] }[] = ARTIKEL_FIELD_SECTIONS.map((section) => ({
    key: section.key,
    rows: rowsOf(section.fieldNames),
  }))
  allSections.push({ key: 'remaining', rows: rowsOf(remainingFieldNames) })
  const sections = allSections.filter((section) => section.rows.length > 0)

  const columns: TableColumn<DetailRow>[] = [
    {
      header: t('artikel.details.columns.field'),
      cell: (row) => (
        <span className="d-inline-flex flex-column">
          <span>{row.label}</span>
          {row.label !== row.fieldName && <code className="small text-muted">{row.fieldName}</code>}
        </span>
      ),
      width: '16rem',
    },
    {
      header: t('artikel.details.columns.value'),
      cell: (row) =>
        row.values.length === 1 ? (
          <span className="text-break">{row.values[0]}</span>
        ) : (
          <span className="d-inline-flex flex-wrap gap-1">
            {row.values.map((value, index) => (
              <span key={index} className="badge text-bg-light fw-normal text-break">
                {value}
              </span>
            ))}
          </span>
        ),
    },
  ]

  if (sections.length === 0) {
    return (
      <ContentBox title={t('artikel.details.title')}>
        <p className="mb-0 text-muted">{t('artikel.details.empty')}</p>
      </ContentBox>
    )
  }

  return (
    <>
      {sections.map((section) => (
        <ContentBox key={section.key} title={t(`artikel.details.sections.${section.key}`)}>
          <Table columns={columns} rows={section.rows} rowKey={(row) => row.fieldName} detailTitle={(row) => row.label} emptyText="" />
        </ContentBox>
      ))}
    </>
  )
}
