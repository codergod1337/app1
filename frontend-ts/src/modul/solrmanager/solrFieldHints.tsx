import type { TFunction } from 'i18next'
import type { ReactNode } from 'react'
import type { LanguageCode } from '../../branding/languages.ts'
import { translate } from '../../components/multilingual.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import type { SolrFieldSetting } from './SolrField.ts'

/**
 * Die ausführlichen Erklärungen der Spalten im Feld-Sheet, fürs Hoverlay am Spaltenkopf: was eine Einstellung tut,
 * welche Auswirkungen sie hat und wofür man sie braucht. Die Texte stehen im Sprachkatalog unter solrmanager.hints,
 * je Erklärung ein Titel und eine Liste von Absätzen. JSX, damit Absätze und Tabellen möglich sind, deshalb nicht in
 * SolrField.ts. Funktionen statt Konstanten, weil die Sprache zur Laufzeit wechselt: Der Aufrufer gibt sein t mit.
 */

function explanation(title: string, ...paragraphs: ReactNode[]): ReactNode {
  return (
    <span className="hoverlay-explanation">
      <strong>{title}</strong>
      {paragraphs.map((paragraph, index) => (
        <span key={index}>{paragraph}</span>
      ))}
    </span>
  )
}

/** Die Absätze einer Erklärung aus dem Katalog: eine Liste je Schlüssel */
function paragraphs(
  t: TFunction,
  key: 'indexed' | 'stored' | 'multiValued' | 'docValues' | 'required' | 'suggest' | 'type' | 'id',
): string[] {
  const value: unknown = t(`solrmanager.hints.${key}.paragraphs`, { returnObjects: true })
  return Array.isArray(value) ? value.map(String) : []
}

/** Was ein Wert je Schalter lang sein darf: ein string ist ein einziger Term, und ein Term hat ein hartes Limit */
interface StringLimitRow {
  setting: string
  limit: string
  reason: string
}

/** Die Limit-Tabelle für string-Felder, in den Hoverlays von indexed, stored und docValues (wie explanation kein Component) */
function stringLimitTable(t: TFunction): ReactNode {
  const rows: StringLimitRow[] = [
    {
      setting: 'indexed',
      limit: t('solrmanager.hints.limitRows.indexed'),
      reason: t('solrmanager.hints.limitRows.indexedReason'),
    },
    {
      setting: 'docValues',
      limit: t('solrmanager.hints.limitRows.docValues'),
      reason: t('solrmanager.hints.limitRows.docValuesReason'),
    },
    {
      setting: t('solrmanager.hints.limitRows.storedOnly'),
      limit: t('solrmanager.hints.limitRows.storedOnlyLimit'),
      reason: t('solrmanager.hints.limitRows.storedOnlyReason'),
    },
  ]
  const columns: TableColumn<StringLimitRow>[] = [
    { header: t('solrmanager.hints.limitColumns.setting'), cell: (row) => row.setting },
    { header: t('solrmanager.hints.limitColumns.limit'), cell: (row) => row.limit },
    { header: t('solrmanager.hints.limitColumns.reason'), cell: (row) => row.reason },
  ]
  return (
    <>
      <span>{t('solrmanager.hints.limitIntro')}</span>
      <Table columns={columns} rows={rows} rowKey={(row) => row.setting} detailTitle={(row) => row.setting} emptyText="" />
    </>
  )
}

/** Die Erklärung eines Schalters; bei indexed, stored und docValues mit der Limit-Tabelle */
export function solrFieldSettingHint(t: TFunction, setting: SolrFieldSetting): ReactNode {
  const withLimitTable = setting === 'indexed' || setting === 'stored' || setting === 'docValues'
  return explanation(
    t(`solrmanager.hints.${setting}.title`),
    ...paragraphs(t, setting),
    ...(withLimitTable ? [stringLimitTable(t)] : []),
  )
}

export function solrFieldTypeHint(t: TFunction): ReactNode {
  return explanation(t('solrmanager.hints.type.title'), ...paragraphs(t, 'type'))
}

export function solrFieldIdHint(t: TFunction): ReactNode {
  return explanation(t('solrmanager.hints.id.title'), ...paragraphs(t, 'id'))
}

/**
 * Die Beschreibung eines Feldes (wozu, welche Daten) als Hoverlay am Feldnamen, zusätzlich zur Spalte. Ohne Beschreibung
 * null: Dann umhüllt das Hoverlay nichts.
 */
export function solrFieldDescriptionHint(name: string, description: string | null, language: LanguageCode): ReactNode {
  const text = translate(description, language)
  return text !== '' ? explanation(name, text) : null
}
