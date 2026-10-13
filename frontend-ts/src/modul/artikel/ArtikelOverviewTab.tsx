import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { AbasOpenButton } from '../../components/AbasOpenButton.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import type { OriginState } from '../../components/Origin.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { SolrHookValues } from '../solrmanager/SolrHookValues.tsx'
import {
  ARTIKEL_NAME_FIELD_NAME,
  ARTIKEL_PREVIEW_FIELD_NAME,
  ARTIKEL_STERNZEIT_HOOK_NAME,
  ARTIKEL_SYNC_DATE_FIELD_NAME,
  ARTIKEL_SYNC_ERROR_FIELD_NAME,
  ARTIKEL_SYNC_STATUS_FIELD_NAME,
  artikelFieldLabel,
  artikelMasterNumberOf,
  artikelPath,
  artikelValueOf,
  artikelValuesOf,
  type ArtikelDocumentForClient,
} from './Artikel.ts'
import { ARTIKEL_OVERVIEW_FIELD_NAMES } from './artikelFieldSections.ts'

interface ArtikelOverviewTabProps {
  artikelNummer: string
  artikelmappe: ArtikelDocumentForClient
  origin: OriginState
}

/** Eine Zeile der Stammdaten: Feldname, Beschriftung, Wert */
interface FactRow {
  fieldName: string
  label: string
  value: string
}

/** Ein Mitglied der Familie: Nummer, Name, ob Master, ob der geöffnete Artikel */
interface FamilyRow {
  artikelNummer: string
  name: string
  master: boolean
  current: boolean
}

/**
 * Die Übersicht der Mappe, nach UebersichtInhalt und FamilienBox der Vorlage: links das Vorschaubild, rechts die
 * Stammdaten als Tabelle, darüber die Sync-Zeile mit Sternzeit; darunter die Familie, wenn es eine gibt, mit dem
 * Master zuerst. Die Hooks des Artikels als Chips, jeder Wert führt in die Suche oder in eine Mappe.
 */
export function ArtikelOverviewTab({ artikelNummer, artikelmappe, origin }: ArtikelOverviewTabProps) {
  const { t, i18n } = useTranslation()
  const { language } = useLanguage()
  const navigate = useNavigate()
  const { solrFields, solrHooks } = useMasterData()
  const document = artikelmappe.document

  const facts: FactRow[] = ARTIKEL_OVERVIEW_FIELD_NAMES.map((fieldName) => ({
    fieldName,
    label: artikelFieldLabel(solrFields, fieldName, undefined, language),
    value: artikelValueOf(document, fieldName),
  })).filter((fact) => fact.value !== '')

  const factColumns: TableColumn<FactRow>[] = [
    { header: t('artikel.overview.columns.field'), cell: (row) => <span className="text-muted">{row.label}</span>, width: '14rem' },
    { header: t('artikel.overview.columns.value'), cell: (row) => <span className="text-break">{row.value}</span> },
  ]

  const masterNumber = artikelMasterNumberOf(document)
  const family: FamilyRow[] = artikelmappe.masterGroup.map((member) => {
    const number = typeof member.id === 'string' ? member.id : artikelValueOf(member, 'id')
    return {
      artikelNummer: number,
      name: artikelValueOf(member, ARTIKEL_NAME_FIELD_NAME),
      master: number === masterNumber,
      current: number === artikelNummer,
    }
  })
  const familyColumns: TableColumn<FamilyRow>[] = [
    {
      header: t('artikel.overview.family.number'),
      cell: (row) =>
        row.current ? (
          <strong>{row.artikelNummer}</strong>
        ) : (
          <button type="button" className="btn btn-link btn-sm p-0 border-0" onClick={() => navigate(artikelPath(row.artikelNummer), { state: origin })}>
            {row.artikelNummer}
          </button>
        ),
      width: '12rem',
    },
    { header: t('artikel.overview.family.name'), cell: (row) => row.name },
    { header: t('artikel.overview.family.role'), cell: (row) => (row.master ? <span className="badge text-bg-warning">{t('artikel.overview.family.master')}</span> : null), width: '7rem' },
  ]

  const preview = document[ARTIKEL_PREVIEW_FIELD_NAME]
  const sternzeit = artikelValueOf(document, ARTIKEL_STERNZEIT_HOOK_NAME)
  const syncDate = artikelValueOf(document, ARTIKEL_SYNC_DATE_FIELD_NAME)
  const syncStatus = artikelValueOf(document, ARTIKEL_SYNC_STATUS_FIELD_NAME)
  const syncError = artikelValueOf(document, ARTIKEL_SYNC_ERROR_FIELD_NAME)
  const syncDay = syncDate === '' ? '' : new Date(syncDate).toLocaleString(i18n.language)

  const hooksWithValues = (solrHooks ?? []).filter((hook) => artikelValuesOf(document[hook.key]).length > 0)

  return (
    <>
      {/* der große ABAS-Knopf im Kopf der Box, wie in der Übersicht der Vorlage: öffnet den Artikel in der ABAS-GUI */}
      <ContentBox
        title={t('artikel.overview.title')}
        actions={<AbasOpenButton sternzeit={sternzeit} hint={t('artikel.status', { artikelNummer })} />}
        footer={
          <span className="small text-muted">
            {sternzeit !== '' && <>{t('artikel.overview.sternzeit', { sternzeit })} · </>}
            {syncDay !== '' && <>{t('artikel.overview.syncedAt', { date: syncDay })} · </>}
            {syncStatus !== '' && (
              <span className={syncStatus === 'OK' ? undefined : 'text-danger'}>
                {t('artikel.overview.syncStatus', { status: syncStatus })}
                {syncError !== '' && <> ({syncError})</>}
              </span>
            )}
          </span>
        }
      >
        <div className="row g-3">
          <div className="col-md-3">
            {typeof preview === 'string' && preview !== '' ? (
              <img className="img-fluid rounded border" src={`data:image/png;base64,${preview}`} alt={artikelValueOf(document, ARTIKEL_NAME_FIELD_NAME)} />
            ) : (
              <div className="border rounded d-flex align-items-center justify-content-center text-muted" style={{ aspectRatio: '1 / 1' }}>
                <span className="d-flex flex-column align-items-center gap-1">
                  <Icon name="artikel" />
                  <span className="small">{t('artikel.overview.noPreview')}</span>
                </span>
              </div>
            )}
          </div>
          <div className="col-md-9">
            <Table columns={factColumns} rows={facts} rowKey={(row) => row.fieldName} detailTitle={(row) => row.label} emptyText={t('artikel.overview.noFacts')} />
          </div>
        </div>
        {hooksWithValues.length > 0 && (
          <div className="d-flex flex-wrap gap-2 mt-3">
            {hooksWithValues.map((hook) => (
              <SolrHookValues key={hook.key} solrHook={hook} values={artikelValuesOf(document[hook.key])} language={language} origin={origin} />
            ))}
          </div>
        )}
      </ContentBox>

      {family.length > 1 && (
        <ContentBox title={t('artikel.overview.family.title', { count: family.length })}>
          <Table columns={familyColumns} rows={family} rowKey={(row) => row.artikelNummer} detailTitle={(row) => row.artikelNummer} emptyText="" />
        </ContentBox>
      )}
    </>
  )
}
