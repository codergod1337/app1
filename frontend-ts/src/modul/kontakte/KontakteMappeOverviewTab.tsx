import { useTranslation } from 'react-i18next'
import { AbasOpenButton } from '../../components/AbasOpenButton.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import type { OriginState } from '../../components/Origin.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { isSolrFieldOfCore, isSolrHookField } from '../solrmanager/SolrField.ts'
import { SolrHookValues } from '../solrmanager/SolrHookValues.tsx'
import {
  KONTAKTE_CORE_KEY,
  KONTAKTE_ID_FIELD_NAME,
  KONTAKTE_NAME_FIELD_NAME,
  KONTAKTE_PARTNER_ROLES,
  KONTAKTE_SYNC_DATE_FIELD_NAME,
  KONTAKTE_SYNC_ERROR_FIELD_NAME,
  KONTAKTE_SYNC_STATUS_FIELD_NAME,
  type KontaktePartnerRole,
  kontakteFieldLabel,
  kontakteValueOf,
  kontakteValuesOf,
} from './Kontakte.ts'
import { KONTAKTE_MAPPE_FIELD_NAMES_SHOWN_ELSEWHERE, KONTAKTE_OTHER_ROLE_FIELD_PREFIX, kontakteMappeFieldSectionsOf, type KontakteMappeFieldSectionKey } from './kontakteMappeFieldSections.ts'

interface KontakteMappeOverviewTabProps {
  partnerRole: KontaktePartnerRole
  nummer: string
  /** das Dokument der Firma, mit den Feldern, die der User lesen darf */
  company: Record<string, unknown>
  origin: OriginState
}

/** Eine Zeile eines Abschnitts: technischer Name, Beschriftung, Werte */
interface FactRow {
  fieldName: string
  label: string
  values: string[]
}

/**
 * Die Übersicht der Kunden- oder Lieferantenmappe, nach dem Kopf und den Blöcken der Kundenmappe der Vorlage: oben die
 * Firma mit ihren Hooks (die Nummern führen in ihre Mappen, eine Firma, die beides ist, so auch in die andere), dem
 * ABAS-Knopf des 19ers und dem der ABAS-Kopie, im Fuß Sternzeit und Sync. Darunter je Abschnitt eine Tabelle, leere
 * Felder und Abschnitte fallen weg; die Geschäftsdaten nur der eigenen Rolle. Zuletzt „Übrige Felder“: jedes normale
 * Feld des Kerns mit Wert, das kein Abschnitt nennt, ohne die der anderen Rolle.
 */
export function KontakteMappeOverviewTab({ partnerRole, nummer, company, origin }: KontakteMappeOverviewTabProps) {
  const { t, i18n } = useTranslation()
  const { language } = useLanguage()
  const { solrFields, solrHooks } = useMasterData()
  const role = KONTAKTE_PARTNER_ROLES[partnerRole]

  function rowsOf(fieldNames: readonly string[]): FactRow[] {
    return fieldNames
      .map((fieldName) => ({ fieldName, label: kontakteFieldLabel(solrFields, solrHooks, fieldName, language), values: kontakteValuesOf(company[fieldName]) }))
      .filter((row) => row.values.some((value) => value !== ''))
  }

  const sectionDefinitions = kontakteMappeFieldSectionsOf(partnerRole)
  const namedFieldNames = new Set<string>([...KONTAKTE_MAPPE_FIELD_NAMES_SHOWN_ELSEWHERE, ...sectionDefinitions.flatMap((section) => section.fieldNames)])
  const otherRolePrefix = KONTAKTE_OTHER_ROLE_FIELD_PREFIX[partnerRole]
  const remainingFieldNames = (solrFields ?? [])
    .filter(
      (solrField) =>
        isSolrFieldOfCore(solrField, KONTAKTE_CORE_KEY) && !isSolrHookField(solrField) && !namedFieldNames.has(solrField.name) && !solrField.name.startsWith(otherRolePrefix),
    )
    .map((solrField) => solrField.name)
  const sections: { key: KontakteMappeFieldSectionKey; rows: FactRow[] }[] = [
    ...sectionDefinitions.map((section) => ({ key: section.key, rows: rowsOf(section.fieldNames) })),
    { key: 'remaining' as const, rows: rowsOf(remainingFieldNames) },
  ].filter((section) => section.rows.length > 0)

  const columns: TableColumn<FactRow>[] = [
    {
      header: t('kontakte.mappe.columns.field'),
      cell: (row) => (
        <span className="d-inline-flex flex-column">
          <span>{row.label}</span>
          {row.label !== row.fieldName && <code className="small text-muted">{row.fieldName}</code>}
        </span>
      ),
      width: '16rem',
    },
    {
      header: t('kontakte.mappe.columns.value'),
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

  const name = kontakteValueOf(company, KONTAKTE_NAME_FIELD_NAME) || nummer
  const sternzeit = kontakteValueOf(company, KONTAKTE_ID_FIELD_NAME)
  const abasPartnerSternzeit = kontakteValueOf(company, role.abasPartnerSternzeitFieldName)
  const syncDate = kontakteValueOf(company, KONTAKTE_SYNC_DATE_FIELD_NAME)
  const syncStatus = kontakteValueOf(company, KONTAKTE_SYNC_STATUS_FIELD_NAME)
  const syncError = kontakteValueOf(company, KONTAKTE_SYNC_ERROR_FIELD_NAME)
  const syncDay = syncDate === '' ? '' : new Date(syncDate).toLocaleString(i18n.language)
  const hooksWithValues = (solrHooks ?? []).filter((hook) => kontakteValuesOf(company[hook.key]).length > 0)

  return (
    <>
      <ContentBox
        title={name}
        actions={
          <span className="d-inline-flex align-items-center gap-2">
            {/* die ABAS-Kopie (Kunde 0:1, Lieferant 1:1), schmal; der große Knopf öffnet den 19er selbst */}
            <AbasOpenButton compact sternzeit={abasPartnerSternzeit} hint={t(`kontakte.mappe.overview.openAbasPartner.${partnerRole}`, { nummer })} />
            <AbasOpenButton sternzeit={sternzeit} hint={t('kontakte.mappe.overview.openAbasCompany', { name })} />
          </span>
        }
        footer={
          <span className="small text-muted">
            {sternzeit !== '' && <>{t('kontakte.mappe.overview.sternzeit', { sternzeit })} · </>}
            {syncDay !== '' && <>{t('kontakte.mappe.overview.syncedAt', { date: syncDay })} · </>}
            {syncStatus !== '' && (
              <span className={syncStatus === 'OK' ? undefined : 'text-danger'}>
                {t('kontakte.search.syncStatus', { status: syncStatus })}
                {syncError !== '' && <> ({syncError})</>}
              </span>
            )}
          </span>
        }
      >
        {hooksWithValues.length > 0 ? (
          <div className="d-flex flex-wrap gap-2">
            {hooksWithValues.map((hook) => (
              <SolrHookValues key={hook.key} solrHook={hook} values={kontakteValuesOf(company[hook.key])} language={language} origin={origin} />
            ))}
          </div>
        ) : (
          <p className="mb-0 text-muted">{t('kontakte.mappe.overview.noHooks')}</p>
        )}
      </ContentBox>

      {sections.map((section) => (
        <ContentBox key={section.key} title={section.key === 'business' ? t(`kontakte.mappe.businessTitle.${partnerRole}`, { nummer }) : t(`kontakte.mappe.sections.${section.key}`)}>
          <Table columns={columns} rows={section.rows} rowKey={(row) => row.fieldName} detailTitle={(row) => row.label} emptyText="" />
        </ContentBox>
      ))}
    </>
  )
}
