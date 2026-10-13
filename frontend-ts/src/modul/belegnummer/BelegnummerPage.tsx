import { type FormEvent, type ReactNode, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { AbasOpenButton } from '../../components/AbasOpenButton.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { Subheader } from '../../components/Subheader.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { ARTIKEL_TARGET_KEY, artikelPath } from '../artikel/Artikel.ts'
import { type AbasBelegnummerLookupResult, BELEGNUMMER_PATH, lookupBelegnummer } from './Belegnummer.ts'

/** Beim Verlassen mitgeben, damit der header „zurück zu Belegnummer“ anbietet. Das label ist ein Schlüssel des Katalogs. */
const ORIGIN_STATE: OriginState = { origin: { path: BELEGNUMMER_PATH, label: 'belegnummer.title' } }

/** Eine Zeile des Ergebnisses: Bezeichnung und Wert */
interface ResultRow {
  key: string
  label: string
  value: ReactNode
}

/**
 * Das Modul Belegnummer (User 2026-10-11, nach der Bezugsnummer der Vorlage): eine Nummer eintippen, ohne zu wissen,
 * was sie ist, und ABAS sagt es über den hinterlegten Aufruf BELEG2ID. Das Ergebnis als Tabelle: Typ, unser Mapping
 * dazu, die Sternzeit mit dem großen ABAS-Knopf, und bei einem Artikel der Weg in seine Mappe. Für jeden Angemeldeten.
 */
export function BelegnummerPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [belegnummer, setBelegnummer] = useState('')
  const [busy, setBusy] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [result, setResult] = useState<AbasBelegnummerLookupResult | null>(null)

  function lookup(event: FormEvent) {
    event.preventDefault()
    const nummer = belegnummer.trim()
    if (nummer === '' || busy) {
      return
    }
    setBusy(true)
    setErrorMessage(null)
    setResult(null)
    lookupBelegnummer(nummer)
      .then((response) => setResult(response.data))
      .catch((error: unknown) => setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError')))
      .finally(() => setBusy(false))
  }

  const columns: TableColumn<ResultRow>[] = [
    { header: t('belegnummer.columns.field'), cell: (row) => <span className="text-muted">{row.label}</span>, width: '14rem' },
    { header: t('belegnummer.columns.value'), cell: (row) => row.value },
  ]

  const rows: ResultRow[] = []
  if (result !== null && result.found) {
    rows.push({ key: 'number', label: t('belegnummer.rows.number'), value: <strong>{result.belegnummer}</strong> })
    rows.push({ key: 'objectType', label: t('belegnummer.rows.objectType'), value: result.objectType === null ? <span className="text-muted">{t('belegnummer.noObjectType')}</span> : <code>{result.objectType}</code> })
    rows.push({ key: 'mapping', label: t('belegnummer.rows.mapping'), value: result.responseMappingKey === null ? <span className="text-muted">{t('belegnummer.noMapping')}</span> : <strong>{result.responseMappingKey}</strong> })
    if (result.sternzeit !== null) {
      rows.push({
        key: 'sternzeit',
        label: t('belegnummer.rows.sternzeit'),
        value: (
          <span className="d-inline-flex align-items-center gap-3">
            <code>{result.sternzeit}</code>
            <AbasOpenButton sternzeit={result.sternzeit} hint={t('belegnummer.inAbasHint', { belegnummer: result.belegnummer })} />
          </span>
        ),
      })
    }
    if (result.responseMappingTargetKey === ARTIKEL_TARGET_KEY && result.primaryId !== null) {
      const artikelNummer = result.primaryId
      rows.push({
        key: 'artikel',
        label: t('belegnummer.rows.artikel'),
        value: (
          <button type="button" className="button-other" onClick={() => navigate(artikelPath(artikelNummer), { state: ORIGIN_STATE })}>
            <Icon name="artikel" /> {t('belegnummer.toArtikel', { artikelNummer })}
          </button>
        ),
      })
    }
  }

  let footer
  if (busy) {
    footer = (
      <span>
        <span className="spinner-border spinner-border-sm me-2" role="status" aria-label={t('common.loading')} />
        {t('belegnummer.looking')}
      </span>
    )
  } else if (errorMessage !== null) {
    footer = <span className="text-danger">{errorMessage}</span>
  } else {
    footer = <span>{t('belegnummer.hint')}</span>
  }

  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="belegnummer" /> {t('belegnummer.title')}
          </>
        }
        sticky
      />
      <div className="main-content">
        <ContentBox title={t('belegnummer.lookupTitle')} footer={footer}>
          <form className="d-flex flex-column gap-3" onSubmit={lookup}>
            <div className="row g-2 align-items-end">
              <div className="col-12 col-md-5">
                <label className="form-label mb-1" htmlFor="belegnummer-number">
                  {t('belegnummer.number')}
                </label>
                <input
                  id="belegnummer-number"
                  className="form-control font-monospace"
                  value={belegnummer}
                  placeholder={t('belegnummer.placeholder')}
                  autoFocus
                  onChange={(event) => setBelegnummer(event.target.value)}
                />
              </div>
              <div className="col-12 col-md-auto">
                <button type="submit" className="button-other" disabled={busy || belegnummer.trim() === ''}>
                  <Icon name="search" /> {t('belegnummer.lookup')}
                </button>
              </div>
            </div>
          </form>
          {result !== null && !result.found && <p className="mt-3 mb-0 text-muted">{t('belegnummer.notFound', { belegnummer: result.belegnummer })}</p>}
          {rows.length > 0 && (
            <div className="mt-3">
              <Table columns={columns} rows={rows} rowKey={(row) => row.key} detailTitle={(row) => row.label} emptyText="" />
            </div>
          )}
        </ContentBox>
      </div>
    </>
  )
}
