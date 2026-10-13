import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { AbasOpenButton } from '../../components/AbasOpenButton.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { jsonRowValueText } from '../../components/jsonRows.ts'
import { type JsonRowsColumn, JsonRowsTable } from '../../components/JsonRowsTable.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import type { OriginState } from '../../components/Origin.ts'
import { isValidSternzeit } from '../abasmanager/AbasSternzeit.ts'
import { DmsFileSubClassHitsBadge } from '../dms/DmsFileSubClassHitsBadge.tsx'
import { artikelFieldLabel, artikelPath, artikelTableRowsOf } from './Artikel.ts'
import type { ArtikelTableField } from './artikelFieldSections.ts'

interface ArtikelTableFieldBoxProps {
  document: Record<string, unknown>
  /** das Tabellenfeld mit seinen Spalten: je Wert eine Zeile als JSON-Objekt */
  tableField: ArtikelTableField
  emptyText: string
  origin: OriginState
}

/**
 * Eine Tabelle aus einem Tabellenfeld des Artikels (Stückliste, Arbeitsgänge) in einer ContentBox: der Titel ist der
 * Anzeigename des Feldes, die Spaltenbeschriftung kommt aus dem Sprachkatalog. Je Spalte, wie es die Beschreibung sagt:
 * eine Artikelnummer führt in ihre Mappe, davor der schmale ABAS-Knopf mit der Sternzeit der Zeile, Menge und Einheit
 * stehen in einer Zelle, der Typ als Badge (User 2026-10-11: schön anzeigen, über die Sternzeit verlinken).
 */
export function ArtikelTableFieldBox({ document, tableField, emptyText, origin }: ArtikelTableFieldBoxProps) {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const navigate = useNavigate()
  const { solrFields } = useMasterData()
  const rows = artikelTableRowsOf(document, tableField.fieldName)
  const columns: JsonRowsColumn[] = tableField.columns.map((column) => ({
    key: column.key,
    label: t(`artikel.tableColumns.${column.key}`, { defaultValue: column.key }),
    unimportant: column.unimportant,
    width: column.width,
  }))

  return (
    <ContentBox title={artikelFieldLabel(solrFields, tableField.fieldName, undefined, language)} footer={<span>{t('artikel.pairList.count', { count: rows.length })}</span>}>
      <JsonRowsTable
        rows={rows}
        columns={columns}
        emptyText={emptyText}
        renderCell={(row, key, value) => {
          const column = tableField.columns.find((candidate) => candidate.key === key)
          if (column?.documentsOf !== undefined) {
            // die Spalte hat keinen eigenen Wert: der Hookwert kommt aus einer anderen Spalte derselben Zeile
            const hookValue = jsonRowValueText(row[column.documentsOf.valueColumnKey])
            return hookValue === '' ? '' : <DmsFileSubClassHitsBadge fileSubClassKey={column.documentsOf.fileSubClassKey} valueByHookName={{ [column.documentsOf.hookName]: hookValue }} origin={origin} />
          }
          const text = jsonRowValueText(value)
          if (column === undefined || text === '') {
            return text
          }
          const sternzeit = tableField.sternzeitColumnKey === null ? '' : jsonRowValueText(row[tableField.sternzeitColumnKey])
          const abasButton = column.abasButton && isValidSternzeit(sternzeit) ? <AbasOpenButton compact sternzeit={sternzeit} hint={t('artikel.tableRowInAbas', { value: text })} /> : null
          if (column.link) {
            return (
              <span className="d-inline-flex align-items-center gap-2">
                {abasButton}
                <button type="button" className="btn btn-link btn-sm p-0 border-0" onClick={() => navigate(artikelPath(text), { state: origin })}>
                  {text}
                </button>
              </span>
            )
          }
          if (abasButton !== null) {
            return (
              <span className="d-inline-flex align-items-center gap-2">
                {abasButton}
                <span>{text}</span>
              </span>
            )
          }
          if (column.unitKey !== undefined) {
            const unit = jsonRowValueText(row[column.unitKey])
            return (
              <span className="font-monospace text-nowrap">
                {text}
                {unit !== '' && <span className="text-muted"> {unit}</span>}
              </span>
            )
          }
          if (column.badge) {
            return <span className="badge text-bg-light border fw-normal">{text}</span>
          }
          return text
        }}
      />
    </ContentBox>
  )
}
