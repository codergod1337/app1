import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { showToast } from '../../components/toastStore.ts'
import {
  type AbasSternzeitMappingPreview,
  isValidSternzeit,
  previewAbasSternzeitMapping,
  writeAbasSternzeitMapping,
} from '../abasmanager/AbasSternzeit.ts'
import { valueText } from './jsonPath.ts'

/** Höchstens so viele Werte einer Liste stehen in der Zeile, der Rest als Zahl */
const SHOWN_LIST_VALUES = 5

/** Eine Zeile der Vorschau: ein Key mit seinen Werten */
interface KeyValuesRow {
  key: string
  values: unknown[]
}

/**
 * Der Tab „Ausführen“ (User 2026-10-10): eine Sternzeit eintippen, das Backend holt sie, lässt das Mapping laufen und
 * zeigt die Werte je Key mit den Solr-Feldern, die der Key füllt, darunter das Dokument, wie es in den Kern geht. Erst
 * „Vorschau“, dann „In Solr schreiben“. Subfetch-Einträge werden gemeldet, noch nicht geholt.
 */
export function MappingExecuteTab() {
  const { t } = useTranslation()
  const { solrFields } = useMasterData()
  const [sternzeit, setSternzeit] = useState('')
  const [busy, setBusy] = useState<'preview' | 'write' | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [preview, setPreview] = useState<AbasSternzeitMappingPreview | null>(null)

  const sternzeitTrimmed = sternzeit.trim()
  const sternzeitValid = isValidSternzeit(sternzeitTrimmed)

  function run(kind: 'preview' | 'write', event?: FormEvent) {
    event?.preventDefault()
    if (!sternzeitValid || busy !== null) {
      return
    }
    setBusy(kind)
    setErrorMessage(null)
    const send = kind === 'write' ? writeAbasSternzeitMapping(sternzeitTrimmed) : previewAbasSternzeitMapping(sternzeitTrimmed)
    send
      .then((result) => {
        setPreview(result.data)
        if (result.data.written) {
          showToast({ kind: 'success', title: t('mapping.execute.written', { id: String(result.data.document?.id ?? '') }) })
        }
      })
      .catch((error: unknown) => setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError')))
      .finally(() => setBusy(null))
  }

  /** Die Felder, die ein Key füllt, aus den Stammdaten: Kern/Name */
  function solrFieldsOf(key: string): string[] {
    return (solrFields ?? [])
      .filter((solrField) => solrField.command === key)
      .map((solrField) => `${solrField.coreKey ?? '*'}/${solrField.name}`)
  }

  function valuesCell(values: unknown[]) {
    const withValue = values.filter((value) => value !== null && value !== undefined && String(value) !== '')
    if (withValue.length === 0) {
      return <span className="text-body-secondary">{t('mapping.execute.noValue')}</span>
    }
    if (values.length === 1) {
      return <span className="text-break">{valueText(values[0])}</span>
    }
    return (
      <span className="d-inline-flex flex-wrap gap-1 align-items-center">
        {values.slice(0, SHOWN_LIST_VALUES).map((value, index) => (
          <span key={index} className="badge text-bg-light fw-normal text-break">
            {valueText(value)}
          </span>
        ))}
        <span className="badge text-bg-secondary">{t('mapping.editor.listCount', { count: values.length })}</span>
      </span>
    )
  }

  const keyRows: KeyValuesRow[] = preview === null ? [] : Object.entries(preview.result.valuesByKey).map(([key, values]) => ({ key, values }))
  const keyColumns: TableColumn<KeyValuesRow>[] = [
    { header: t('mapping.editor.columns.key'), cell: (row) => <strong>{row.key}</strong>, width: '16rem' },
    { header: t('mapping.editor.columns.values'), cell: (row) => valuesCell(row.values) },
    {
      header: t('mapping.editor.columns.solrFields'),
      cell: (row) => (
        <span className="d-inline-flex flex-wrap gap-1">
          {solrFieldsOf(row.key).map((name) => (
            <span key={name} className="badge text-bg-secondary fw-normal font-monospace">
              {name}
            </span>
          ))}
        </span>
      ),
      unimportant: true,
      width: '16rem',
    },
  ]

  const documentRows = preview?.document === null || preview === null ? [] : Object.entries(preview.document).map(([name, value]) => ({ name, value }))
  const documentColumns: TableColumn<{ name: string; value: unknown }>[] = [
    { header: t('mapping.execute.columns.field'), cell: (row) => <code>{row.name}</code>, width: '16rem' },
    { header: t('mapping.execute.columns.value'), cell: (row) => valuesCell(Array.isArray(row.value) ? row.value : [row.value]) },
  ]

  let inputFooter
  if (busy !== null) {
    inputFooter = (
      <span>
        <span className="spinner-border spinner-border-sm me-2" role="status" aria-label={t('common.loading')} />
        {t('mapping.execute.running')}
      </span>
    )
  } else if (errorMessage !== null) {
    inputFooter = <span className="text-danger">{errorMessage}</span>
  } else {
    inputFooter = <span>{t('mapping.execute.hint')}</span>
  }

  return (
    <>
      <ContentBox title={t('mapping.execute.title')} footer={inputFooter}>
        <form className="d-flex align-items-end gap-2 flex-wrap" onSubmit={(event) => run('preview', event)}>
          <div>
            <label className="form-label mb-1" htmlFor="mapping-execute-sternzeit">
              {t('mapping.abasFetch.sternzeitLabel')}
            </label>
            <input
              id="mapping-execute-sternzeit"
              className="form-control form-control-sm font-monospace"
              value={sternzeit}
              placeholder={t('mapping.abasFetch.sternzeitPlaceholder')}
              onChange={(event) => setSternzeit(event.target.value)}
            />
          </div>
          <button type="submit" className="button-other" disabled={!sternzeitValid || busy !== null}>
            <Icon name="abasManager" /> {t('mapping.execute.preview')}
          </button>
          <button type="button" className="button-save" disabled={!sternzeitValid || busy !== null || preview === null} onClick={() => run('write')}>
            <Icon name="solrCores" /> {t('mapping.execute.write')}
          </button>
        </form>
      </ContentBox>

      {preview !== null && (
        <ContentBox
          title={t('mapping.execute.resultTitle', { mapping: preview.result.responseMappingKey, type: preview.result.responseObjectType })}
          footer={
            <span>
              {t('mapping.execute.resultFooter', {
                target: preview.result.targetKey ?? t('mapping.overview.noTarget'),
                primary: preview.result.primaryIdKey ?? '–',
              })}
              {preview.result.warnings.length > 0 && (
                <>
                  {' · '}
                  <span className="text-danger">{t('mapping.execute.warnings', { warnings: preview.result.warnings.join(' | ') })}</span>
                </>
              )}
            </span>
          }
        >
          <Table columns={keyColumns} rows={keyRows} rowKey={(row) => row.key} detailTitle={(row) => row.key} emptyText={t('mapping.execute.noValues')} />
        </ContentBox>
      )}

      {preview !== null && preview.document !== null && (
        <ContentBox
          title={t(preview.written ? 'mapping.execute.documentWrittenTitle' : 'mapping.execute.documentTitle', { id: String(preview.document.id ?? '') })}
          footer={<span>{t('mapping.execute.documentFooter', { count: documentRows.length })}</span>}
        >
          <Table columns={documentColumns} rows={documentRows} rowKey={(row) => row.name} detailTitle={(row) => row.name} emptyText={t('mapping.execute.noValues')} />
        </ContentBox>
      )}
    </>
  )
}
