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
  key:
    | 'indexed'
    | 'stored'
    | 'multiValued'
    | 'docValues'
    | 'required'
    | 'suggest'
    | 'ngram'
    | 'type'
    | 'fieldAccess'
    | 'searchWeight'
    | 'clientWriteRule'
    | 'command'
    | 'essential',
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

/** Die Erklärung der Spalte Lesen: die ARs, deren Inhaber das Feld bekommen */
export function solrFieldAccessHint(t: TFunction): ReactNode {
  return explanation(t('solrmanager.hints.fieldAccess.title'), ...paragraphs(t, 'fieldAccess'))
}

/** Die Erklärung der Spalte Suche: das Gewicht in der qf der Suche */
export function solrFieldSearchWeightHint(t: TFunction): ReactNode {
  return explanation(t('solrmanager.hints.searchWeight.title'), ...paragraphs(t, 'searchWeight'))
}

/** Die Erklärung des Umschaltknopfs der Schreibregel: Backend, nur beim Anlegen, Client */
export function solrFieldClientWriteRuleHint(t: TFunction): ReactNode {
  return explanation(t('solrmanager.hints.clientWriteRule.title'), ...paragraphs(t, 'clientWriteRule'))
}

/** Die Erklärung der Spalte Mapping: der Key des Mapping-Eintrags, dessen Werte ins Feld gehen */
export function solrFieldCommandHint(t: TFunction): ReactNode {
  return explanation(t('solrmanager.hints.command.title'), ...paragraphs(t, 'command'))
}

/** Die Erklärung der Spalte essenziell: wirkt nur beim Import eines Kerns, und warum required das nicht leistet */
export function solrFieldEssentialHint(t: TFunction): ReactNode {
  return explanation(t('solrmanager.hints.essential.title'), ...paragraphs(t, 'essential'))
}

/**
 * Der Solr-Name eines Feldes mit seiner Beschreibung (wozu, welche Daten) als Hoverlay am Info-Symbol der Zeile
 * (User 2026-10-09: die Namen brauchen in der Spalte zu viel Platz). Ohne Beschreibung steht nur der Name.
 */
export function solrFieldNameHint(name: string, description: string | null, language: LanguageCode): ReactNode {
  const text = translate(description, language)
  return text !== '' ? explanation(name, text) : explanation(name)
}
