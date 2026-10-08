import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { SOLR_CORE_BUILT_IN_FIELD_NAMES } from '../modul/solrmanager/SolrCore.ts'
import { solrFieldDisplayNameOf } from '../modul/solrmanager/SolrField.ts'
import { Hoverlay } from './Hoverlay.tsx'
import { useLanguage } from './languageContext.ts'
import { useMasterData } from './masterDataContext.ts'
import { translate } from './multilingual.ts'
import { Popup } from './Popup.tsx'
import { Table, type TableColumn } from './Table.tsx'

/** Ein Dokument, wie es der Auflöse-Service liefert: nur die Felder, die der User lesen darf */
export type SolrResolvedDocument = Record<string, unknown>

/** Eine Trefferseite (SolrExactFieldValueResolveService.SolrResolvedDocumentsPage) */
export interface SolrResolvedDocumentsPage {
  totalFound: number
  page: number
  size: number
  documents: SolrResolvedDocument[]
}

interface SolrResolvedDocumentsPageTableProps {
  coreKey: string
  resolvedPage: SolrResolvedDocumentsPage
  /** die Felder und Hooks, die der User in diesem Kern lesen darf: danach entstehen die Spalten */
  readableFieldAndHookNames: readonly string[]
  /** eine andere Seite laden */
  onPage: (page: number) => void
  busy?: boolean
  /** weitere Knöpfe je Zeile, z. B. der Download im DMS. Stehen vor dem Knopf „Felder“. */
  rowActions?: (document: SolrResolvedDocument) => ReactNode
}

/** Das Vorschaubild, wenn der Kern eins hat, steht als erste Spalte */
const PREVIEW_IMAGE_FIELD_NAME = 'bildVorschauBase64Png'

/** Felder, die als Spalte nichts bringen, auch wenn sie Minimum sind */
const HIDDEN_COLUMN_FIELD_NAMES: readonly string[] = [...SOLR_CORE_BUILT_IN_FIELD_NAMES, PREVIEW_IMAGE_FIELD_NAME, 'sftpPath']

/** So viele Minimum-Felder werden Spalten, der Rest steht im Popup */
const MAX_FIELD_COLUMNS = 5

/** Ab so vielen Zeichen zeigt die Tabelle nur die Länge, z. B. beim Volltext */
const LONG_VALUE_CHARACTERS = 120

/** Zwei Felder, deren Werte Keys aus den Stammdaten sind: dazu der Anzeigename */
const STATUS_FIELD_NAME = 'status'
const FILE_SUB_CLASS_FIELD_NAME = 'fileSubClassKey'

/**
 * Die Trefferliste einer Auflösung, kernunabhängig und geteilt von DMS-Auflösen und Dateifreigabe: Vorschaubild, wenn
 * der Kern eins hat, die id, dann die ersten Minimum-Felder des Kerns in der Reihenfolge des Sheets, je Zeile ein Popup
 * mit allen gelieferten Feldern, unten das Blättern. Es erscheinen nur Felder, die der User lesen darf: Ein Feld, das
 * fehlt, ist nicht sichtbar, kein Fehler.
 */
export function SolrResolvedDocumentsPageTable({
  coreKey,
  resolvedPage,
  readableFieldAndHookNames,
  onPage,
  busy = false,
  rowActions,
}: SolrResolvedDocumentsPageTableProps) {
  const { t, i18n } = useTranslation()
  const { language } = useLanguage()
  const { solrFields, entityStates, fileSubClasses, solrHooks } = useMasterData()
  const [detailDocument, setDetailDocument] = useState<SolrResolvedDocument | null>(null)

  /** Die Beschriftung eines Feldes: Anzeigename des Feldes oder Hooks, sonst der technische Name */
  function labelOf(name: string): string {
    const hook = solrHooks?.find((candidate) => candidate.key === name)
    if (hook) {
      return translate(hook.displayName, language) || hook.key
    }
    return solrFieldDisplayNameOf(solrFields ?? [], coreKey, name, language) ?? name
  }

  /** Ein Wert fürs Lesen: Listen mit Komma, lange Texte nur mit ihrer Länge, Zeitpunkte in der Sprache der Oberfläche */
  function shownValue(name: string, value: unknown): string {
    if (value === null || value === undefined) {
      return ''
    }
    if (Array.isArray(value)) {
      return value.map((entry) => shownValue(name, entry)).join(', ')
    }
    if (typeof value === 'string') {
      if (value.length > LONG_VALUE_CHARACTERS) {
        return t('resolve.longValue', { count: value.length })
      }
      if (name === STATUS_FIELD_NAME) {
        const state = entityStates?.find((candidate) => candidate.key === value)
        const stateName = state ? translate(state.displayName) : ''
        return stateName !== '' ? `${stateName} (${value})` : value
      }
      if (name === FILE_SUB_CLASS_FIELD_NAME) {
        const fileSubClass = fileSubClasses?.find((candidate) => candidate.key === value)
        const fileSubClassName = fileSubClass ? translate(fileSubClass.displayName) : ''
        return fileSubClassName !== '' ? `${fileSubClassName} (${value})` : value
      }
      if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value)) {
        const date = new Date(value)
        return Number.isNaN(date.getTime()) ? value : date.toLocaleString(i18n.language)
      }
      return value
    }
    if (typeof value === 'object') {
      return JSON.stringify(value)
    }
    return String(value)
  }

  const minimumSetColumnFieldNames = (solrFields ?? [])
    .filter(
      (field) =>
        (field.coreKey === null || field.coreKey === coreKey) &&
        readableFieldAndHookNames.includes(field.name) &&
        !HIDDEN_COLUMN_FIELD_NAMES.includes(field.name),
    )
    .slice(0, MAX_FIELD_COLUMNS)
    .map((field) => field.name)

  const columns: TableColumn<SolrResolvedDocument>[] = []
  if (readableFieldAndHookNames.includes(PREVIEW_IMAGE_FIELD_NAME)) {
    columns.push({
      header: t('resolve.columns.preview'),
      cell: (document): ReactNode =>
        typeof document[PREVIEW_IMAGE_FIELD_NAME] === 'string' ? (
          <img
            src={`data:image/png;base64,${document[PREVIEW_IMAGE_FIELD_NAME]}`}
            alt=""
            style={{ width: '3rem', height: '3rem', objectFit: 'contain' }}
          />
        ) : null,
    })
  }
  columns.push({
    header: t('resolve.columns.id'),
    cell: (document) => (
      <Hoverlay text={String(document.id ?? '')}>
        <code>{String(document.id ?? '').slice(0, 10)}</code>
      </Hoverlay>
    ),
  })
  minimumSetColumnFieldNames.forEach((name, index) =>
    columns.push({
      header: labelOf(name),
      unimportant: index > 0,
      cell: (document) => shownValue(name, document[name]),
    }),
  )

  const pages = Math.max(1, Math.ceil(resolvedPage.totalFound / resolvedPage.size))

  return (
    <>
      <Table
        columns={columns}
        rows={resolvedPage.documents}
        rowKey={(document) => String(document.id ?? JSON.stringify(document))}
        detailTitle={(document) => String(document.id ?? '')}
        emptyText={t('resolve.empty')}
        actions={(document) => (
          <span className="d-inline-flex gap-2">
            {rowActions?.(document)}
            <button type="button" className="button-other" onClick={() => setDetailDocument(document)}>
              {t('resolve.showFields')}
            </button>
          </span>
        )}
        actionsWidth={rowActions ? '12rem' : undefined}
      />
      <div className="d-flex flex-wrap align-items-center gap-2 mt-2">
        <span>
          {t('resolve.total', { count: resolvedPage.totalFound })}
          {resolvedPage.totalFound > 0 && `, ${t('resolve.page', { page: resolvedPage.page + 1, pages })}`}
        </span>
        {pages > 1 && (
          <span className="ms-auto d-flex gap-2">
            <button
              type="button"
              className="button-other"
              disabled={busy || resolvedPage.page === 0}
              onClick={() => onPage(resolvedPage.page - 1)}
            >
              {t('resolve.previous')}
            </button>
            <button
              type="button"
              className="button-other"
              disabled={busy || resolvedPage.page + 1 >= pages}
              onClick={() => onPage(resolvedPage.page + 1)}
            >
              {t('resolve.next')}
            </button>
          </span>
        )}
      </div>
      <Popup
        open={detailDocument !== null}
        title={t('resolve.fieldsOf', { id: String(detailDocument?.id ?? '') })}
        onClose={() => setDetailDocument(null)}
      >
        {detailDocument !== null && (
          <dl className="details-list">
            {Object.entries(detailDocument).map(([name, value]) => (
              <div key={name}>
                <dt>
                  {labelOf(name)}
                  {labelOf(name) !== name && <span className="text-muted fw-normal ms-1">({name})</span>}
                </dt>
                <dd>{shownValue(name, value)}</dd>
              </div>
            ))}
          </dl>
        )}
      </Popup>
    </>
  )
}
