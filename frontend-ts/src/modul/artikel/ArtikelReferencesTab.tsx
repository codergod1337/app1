import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { AbasOpenButton } from '../../components/AbasOpenButton.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import { IntegerInput } from '../../components/IntegerInput.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { isValidSternzeit } from '../abasmanager/AbasSternzeit.ts'
import {
  type ArtikelAggregatedReference,
  type ArtikelAggregatedReferencesForClient,
  type ArtikelReferenceFlag,
  artikelPath,
  getArtikelAggregatedReferences,
  recomputeArtikelAggregatedReferences,
} from './Artikel.ts'

interface ArtikelReferencesTabProps {
  artikelNummer: string
  flag: ArtikelReferenceFlag
  /** die Überschrift der Box, der Name des Tabs */
  title: string
  /** die Herkunft für den Sprung in eine andere Mappe */
  origin: OriginState
}

/** Die gewählte Tiefe, an Artikel und Flag gebunden: ein anderer Tab beginnt wieder bei der kleinsten */
interface ChosenDepth {
  key: string
  depth: number
}

/** Was zuletzt geladen wurde, an Artikel, Flag und Tiefe gebunden; passt der Schlüssel nicht, lädt der Tab gerade */
interface LoadedReferences {
  key: string
  result: ArtikelAggregatedReferencesForClient
}

/** Höchste Tiefe, die die Maske anbietet; das Backend hat denselben Deckel in app1.solr.artikel.references.max-depth */
const MAX_ALTERNATIVE_DEPTH = 5

/**
 * Der Tab eines Flags in der Mappe (User 2026-10-13): die aufaggregierten Verbindungen des Artikels, zuerst die direkt
 * hinterlegten, dahinter die der Alternativgruppe bis zur gewählten Tiefe. Die Tiefe steht im Kopf, dazu „neu suchen“,
 * das immer frisch rechnet und im Dokument ablegt; sonst zeigt der Tab den abgelegten Stand mit Datum. Je Treffer die
 * Nummer als Weg in dessen Mappe mit dem ABAS-Knopf, Name, Entfernung in Beziehungen, der Weg und die Richtung.
 */
export function ArtikelReferencesTab({ artikelNummer, flag, title, origin }: ArtikelReferencesTabProps) {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  // bei ALTERNATIVE ist die Gruppe selbst das Ergebnis, Tiefe 0 hieße nichts
  const minDepth = flag === 'ALTERNATIVE' ? 1 : 0
  const flagKey = `${artikelNummer}|${flag}`
  const [chosenDepth, setChosenDepth] = useState<ChosenDepth | null>(null)
  const depth = chosenDepth !== null && chosenDepth.key === flagKey ? chosenDepth.depth : minDepth
  const loadKey = `${flagKey}|${depth}`
  const [lastLoaded, setLastLoaded] = useState<LoadedReferences | null>(null)
  const [recomputing, setRecomputing] = useState(false)
  const [failed, setFailed] = useState<{ key: string; message: string } | null>(null)

  // jede Änderung von Artikel, Flag oder Tiefe lädt aus der Ablage, oder das Backend rechnet, wenn sie nicht reicht
  useEffect(() => {
    let cancelled = false
    getArtikelAggregatedReferences(artikelNummer, flag, depth)
      .then((result) => {
        if (!cancelled) {
          setLastLoaded({ key: loadKey, result: result.data })
          setFailed(null)
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setFailed({ key: loadKey, message: error instanceof RequestError ? error.message : t('common.unknownError') })
        }
      })
    return () => {
      cancelled = true
    }
  }, [artikelNummer, flag, depth, loadKey, t])

  function recompute() {
    if (recomputing) {
      return
    }
    setRecomputing(true)
    recomputeArtikelAggregatedReferences(artikelNummer, flag, depth)
      .then((result) => {
        setLastLoaded({ key: loadKey, result: result.data })
        setFailed(null)
      })
      .catch((error: unknown) => setFailed({ key: loadKey, message: error instanceof RequestError ? error.message : t('common.unknownError') }))
      .finally(() => setRecomputing(false))
  }

  const loaded = lastLoaded !== null && lastLoaded.key === loadKey ? lastLoaded.result : null
  const errorMessage = failed !== null && failed.key === loadKey ? failed.message : null
  const busy = recomputing || (loaded === null && errorMessage === null)

  const columns: TableColumn<ArtikelAggregatedReference>[] = [
    {
      header: t('artikel.references.columns.artikelNummer'),
      width: '13rem',
      cell: (row) => (
        <span className="d-inline-flex align-items-center gap-2">
          {isValidSternzeit(row.sternzeit) && <AbasOpenButton compact sternzeit={row.sternzeit} hint={t('artikel.tableRowInAbas', { value: row.artikelNummer })} />}
          <button type="button" className="btn btn-link btn-sm p-0 border-0" onClick={() => navigate(artikelPath(row.artikelNummer), { state: origin })}>
            {row.artikelNummer}
          </button>
        </span>
      ),
    },
    { header: t('artikel.references.columns.name'), cell: (row) => row.name },
    { header: t('artikel.references.columns.distance'), width: '9rem', cell: (row) => t('artikel.references.distance', { count: row.distance }) },
    { header: t('artikel.references.columns.path'), unimportant: true, cell: (row) => <span className="font-monospace">{row.pathArtikelNummern.join(' → ')}</span> },
    {
      header: t('artikel.references.columns.direction'),
      unimportant: true,
      cell: (row) => {
        const via = row.pathArtikelNummern[row.pathArtikelNummern.length - 1] ?? artikelNummer
        return <span className="badge text-bg-light border fw-normal">{row.foundByReverseLookup ? t('artikel.references.reverse', { via }) : t('artikel.references.listed', { via })}</span>
      },
    },
  ]

  const actions = (
    <span className="d-inline-flex align-items-center gap-2 flex-wrap">
      <label className="mb-0 small text-nowrap" htmlFor={`artikel-references-depth-${flag}`}>
        {t('artikel.references.depth')}
      </label>
      <IntegerInput
        id={`artikel-references-depth-${flag}`}
        className="w-auto"
        value={depth}
        min={minDepth}
        max={MAX_ALTERNATIVE_DEPTH}
        disabled={busy}
        onChange={(value) => setChosenDepth({ key: flagKey, depth: value })}
      />
      <button type="button" className="button-other" disabled={busy} onClick={recompute}>
        <Icon name="refresh" /> {t('artikel.references.recompute')}
      </button>
    </span>
  )

  let footer
  if (errorMessage !== null) {
    footer = <span className="text-danger">{t('artikel.references.failed', { error: errorMessage })}</span>
  } else if (busy || loaded === null) {
    footer = (
      <span>
        <span className="spinner-border spinner-border-sm me-2" role="status" aria-label={t('common.loading')} />
        {t('artikel.references.loading')}
      </span>
    )
  } else {
    footer = (
      <span className="d-inline-flex align-items-center gap-3 flex-wrap">
        <span>{t('artikel.pairList.count', { count: loaded.references.length })}</span>
        <span className="text-body-secondary">{t('artikel.references.stateOf', { date: new Date(loaded.aggregatedAt).toLocaleString(i18n.language), depth: loaded.alternativeDepth })}</span>
        {loaded.truncated && <span className="text-warning">{t('artikel.references.truncated')}</span>}
      </span>
    )
  }

  return (
    <ContentBox title={title} actions={actions} footer={footer}>
      <Table columns={columns} rows={loaded?.references ?? []} rowKey={(row) => row.artikelNummer} detailTitle={(row) => row.artikelNummer} emptyText={loaded === null ? '' : t('artikel.references.empty')} />
    </ContentBox>
  )
}
