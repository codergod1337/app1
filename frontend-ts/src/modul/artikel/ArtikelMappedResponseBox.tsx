import { type ReactNode, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { AbasOpenButton } from '../../components/AbasOpenButton.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { JsonRowCards, jsonRowsHaveNestedValues } from '../../components/JsonRowCards.tsx'
import { jsonRowsOf, jsonRowValueText } from '../../components/jsonRows.ts'
import { type JsonRowsColumn, JsonRowsTable } from '../../components/JsonRowsTable.tsx'
import type { ResponseMappingExecutionResult } from '../abasmanager/AbasSternzeit.ts'
import { isValidSternzeit } from '../abasmanager/AbasSternzeit.ts'
import { fetchArtikelmappeInfosys, fetchArtikelmappeSternzeit } from './Artikel.ts'

/** Was nachgeladen wird: ein hinterlegter Aufruf für den Artikel, oder das Objekt hinter der Sternzeit in einem Feld */
export type ArtikelNachladeRequest = { kind: 'infosys'; abasInfosysKey: string } | { kind: 'sternzeit'; fieldName: string }

interface ArtikelMappedResponseBoxProps {
  artikelNummer: string
  request: ArtikelNachladeRequest
  title: ReactNode
  /** ein Satz über der Tabelle, z. B. was der Stand bedeutet */
  hint?: ReactNode
  emptyText: string
}

/** Eine Tabelle aus dem Ergebnis: der Key des Tabellen-Eintrags und seine Zeilen; nested heißt, Zeilen tragen Unterobjekte und werden Karten */
interface MappedTable {
  key: string
  rows: Record<string, unknown>[]
  columns: JsonRowsColumn[]
  nested: boolean
}

/** Was geladen ist, gebunden an Artikel und Anfrage; eine andere Anfrage heißt „lädt“ */
type Loaded = { requestKey: string } & ({ kind: 'ok'; result: ResponseMappingExecutionResult } | { kind: 'error'; message: string })

/**
 * Eine Box mit dem, was die Mappe live bei ABAS nachlädt (User 2026-10-11): lädt beim Öffnen, zeigt jeden
 * Tabellen-Eintrag des Mappings als Tabelle (die Spalten sind die des Eintrags, die Köpfe die ABAS-Namen) und die
 * übrigen Werte als Liste; eine Sternzeit in einer Zelle bekommt den schmalen ABAS-Knopf. Tragen die Zeilen
 * Unterobjekte, weil ein Subfetch je Zeile seine Antwort hineingehängt hat (User 2026-10-13, Prüfpläne mit Merkmalen,
 * Prüfaufträge mit Ergebnis), wird die Tabelle zu Karten je Zeile mit Untertabellen. Im Fuß das Mapping, die Zeilenzahl
 * und die Warnungen des Laufs.
 */
export function ArtikelMappedResponseBox({ artikelNummer, request, title, hint, emptyText }: ArtikelMappedResponseBoxProps) {
  const { t } = useTranslation()
  const requestKey = request.kind === 'infosys' ? `${artikelNummer}|infosys|${request.abasInfosysKey}` : `${artikelNummer}|sternzeit|${request.fieldName}`
  const requestName = request.kind === 'infosys' ? request.abasInfosysKey : request.fieldName
  const [lastLoaded, setLastLoaded] = useState<Loaded | null>(null)

  useEffect(() => {
    let cancelled = false
    const key = request.kind === 'infosys' ? `${artikelNummer}|infosys|${requestName}` : `${artikelNummer}|sternzeit|${requestName}`
    const send = request.kind === 'infosys' ? fetchArtikelmappeInfosys(artikelNummer, requestName) : fetchArtikelmappeSternzeit(artikelNummer, requestName)
    send
      .then((response) => {
        if (!cancelled) {
          setLastLoaded({ requestKey: key, kind: 'ok', result: response.data })
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setLastLoaded({ requestKey: key, kind: 'error', message: error instanceof RequestError ? error.message : t('common.unknownError') })
        }
      })
    return () => {
      cancelled = true
    }
  }, [artikelNummer, request.kind, requestName, t])

  const loaded = lastLoaded !== null && lastLoaded.requestKey === requestKey ? lastLoaded : null
  const result = loaded?.kind === 'ok' ? loaded.result : null

  /** Die Beschriftung einer Spalte oder eines Unterobjekts: aus dem Sprachkatalog, sonst der Name aus dem Mapping */
  function labelOf(key: string): string {
    return t(`artikel.tableColumns.${key}`, { defaultValue: key })
  }

  const tables: MappedTable[] = []
  const facts: { key: string; text: string }[] = []
  if (result !== null) {
    for (const [key, values] of Object.entries(result.valuesByKey)) {
      const rows = jsonRowsOf(values.map((value) => jsonRowValueText(value)))
      if (rows.length > 0) {
        const columnKeys: string[] = []
        for (const row of rows) {
          for (const columnKey of Object.keys(row)) {
            if (!columnKeys.includes(columnKey)) {
              columnKeys.push(columnKey)
            }
          }
        }
        tables.push({
          key,
          rows,
          columns: columnKeys.map((columnKey, index) => ({ key: columnKey, label: labelOf(columnKey), unimportant: index > 2 })),
          nested: jsonRowsHaveNestedValues(rows),
        })
        continue
      }
      const text = values
        .map((value) => jsonRowValueText(value))
        .filter((value) => value !== '')
        .join(', ')
      if (text !== '') {
        facts.push({ key, text })
      }
    }
  }
  const rowCount = tables.reduce((sum, table) => sum + table.rows.length, 0)

  let footer
  if (loaded === null) {
    footer = (
      <span>
        <span className="spinner-border spinner-border-sm me-2" role="status" aria-label={t('common.loading')} />
        {t('artikel.nachladen.loading')}
      </span>
    )
  } else if (loaded.kind === 'error') {
    footer = <span className="text-danger">{t('artikel.nachladen.failed', { error: loaded.message })}</span>
  } else {
    footer = (
      <span className="small">
        {t('artikel.nachladen.mapping', { key: loaded.result.responseMappingKey })} · {t('artikel.nachladen.rows', { count: rowCount })}
        {loaded.result.warnings.length > 0 && <span className="text-danger"> · {t('artikel.nachladen.warnings', { warnings: loaded.result.warnings.join('; ') })}</span>}
      </span>
    )
  }

  /** Ein Wert in Zelle oder Karte: eine Sternzeit mit dem schmalen ABAS-Knopf, mehrzeiliger Text mit seinen Umbrüchen */
  function renderValue(_key: string, value: unknown) {
    const text = jsonRowValueText(value)
    if (isValidSternzeit(text)) {
      return (
        <span className="d-inline-flex align-items-center gap-2">
          <AbasOpenButton compact sternzeit={text} />
          <code className="small">{text}</code>
        </span>
      )
    }
    return text.includes('\n') ? <span style={{ whiteSpace: 'pre-wrap' }}>{text}</span> : text
  }

  function renderCell(_row: Record<string, unknown>, key: string, value: unknown) {
    return renderValue(key, value)
  }

  return (
    <ContentBox title={title} footer={footer}>
      {hint !== undefined && <p className="small text-muted">{hint}</p>}
      {result !== null && tables.length === 0 && facts.length === 0 && <p className="mb-0 text-muted">{emptyText}</p>}
      {facts.length > 0 && (
        <dl className="row mb-2 small">
          {facts.map((fact) => (
            <div key={fact.key} className="col-12 col-md-6 d-flex gap-2">
              <dt className="text-muted fw-normal">{fact.key}</dt>
              <dd className="mb-0 text-break">{fact.text}</dd>
            </div>
          ))}
        </dl>
      )}
      {tables.map((table) => (
        <div key={table.key} className={tables.length > 1 ? 'mb-3' : undefined}>
          {tables.length > 1 && <div className="small text-muted mb-1">{labelOf(table.key)}</div>}
          {table.nested ? (
            <JsonRowCards rows={table.rows} labelOf={labelOf} renderValue={renderValue} emptyText={emptyText} />
          ) : (
            <JsonRowsTable rows={table.rows} columns={table.columns} emptyText={emptyText} renderCell={renderCell} />
          )}
        </div>
      ))}
    </ContentBox>
  )
}
