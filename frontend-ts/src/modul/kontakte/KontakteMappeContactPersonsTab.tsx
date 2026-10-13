import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { AbasOpenButton } from '../../components/AbasOpenButton.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { KONTAKTE_ID_FIELD_NAME, KONTAKTE_NAME_FIELD_NAME, kontakteValueOf } from './Kontakte.ts'

/** Ab so vielen Zeichen filtert das Feld die Ansprechpartner, wie in der Vorlage */
const FILTER_MIN_CHARACTERS = 2

interface KontakteMappeContactPersonsTabProps {
  /** die Ansprechpartner der Firma: Dokumente mit kontaktorig „Kontakt“ und derselben Nummer */
  contactPersons: readonly Record<string, unknown>[]
}

/** Passt ein Ansprechpartner zum Filter: über alle geladenen Felder, Listen Eintrag für Eintrag, nach der Vorlage */
function matchesFilter(contactPerson: Record<string, unknown>, filter: string): boolean {
  return Object.values(contactPerson).some((value) => ([] as unknown[]).concat(value ?? []).some((entry) => String(entry).toLowerCase().includes(filter)))
}

/**
 * Die Ansprechpartner der Firma, nach der AnsprechpartnerTabelle der Kundenmappe der Vorlage: Name, Funktion, Telefon,
 * Mobil, E-Mail, rechts der schmale ABAS-Knopf mit der Sternzeit des 19ers. Telefonnummern und E-Mail sind Links. Das
 * Feld im Kopf filtert ab zwei Zeichen über alle geladenen Felder, denn meist sucht man genau einen.
 */
export function KontakteMappeContactPersonsTab({ contactPersons }: KontakteMappeContactPersonsTabProps) {
  const { t } = useTranslation()
  const [filter, setFilter] = useState('')
  const wanted = filter.trim().toLowerCase()
  const shown = wanted.length >= FILTER_MIN_CHARACTERS ? contactPersons.filter((contactPerson) => matchesFilter(contactPerson, wanted)) : contactPersons

  const columns: TableColumn<Record<string, unknown>>[] = [
    { header: t('kontakte.mappe.contactPersons.columns.name'), cell: (row) => kontakteValueOf(row, KONTAKTE_NAME_FIELD_NAME) },
    { header: t('kontakte.mappe.contactPersons.columns.function'), cell: (row) => kontakteValueOf(row, 'funktion'), unimportant: true },
    {
      header: t('kontakte.mappe.contactPersons.columns.phone'),
      cell: (row) => {
        const phone = kontakteValueOf(row, 'tele')
        return phone === '' ? '' : <a href={`tel:${phone}`}>{phone}</a>
      },
    },
    {
      header: t('kontakte.mappe.contactPersons.columns.mobile'),
      cell: (row) => {
        const mobile = kontakteValueOf(row, 'mtele')
        return mobile === '' ? '' : <a href={`tel:${mobile}`}>{mobile}</a>
      },
      unimportant: true,
    },
    {
      header: t('kontakte.mappe.contactPersons.columns.email'),
      cell: (row) => {
        const email = kontakteValueOf(row, 'email')
        return email === '' ? '' : <a href={`mailto:${email}`}>{email}</a>
      },
    },
  ]

  let footer
  if (contactPersons.length === 0) {
    footer = <span className="text-muted">{t('kontakte.mappe.contactPersons.empty')}</span>
  } else if (shown.length === contactPersons.length) {
    footer = <span>{t('kontakte.mappe.contactPersons.count', { count: contactPersons.length })}</span>
  } else {
    footer = <span>{t('kontakte.mappe.contactPersons.countFiltered', { shown: shown.length, total: contactPersons.length })}</span>
  }

  return (
    <ContentBox
      title={t('kontakte.mappe.tabs.contactPersons')}
      actions={
        contactPersons.length > 0 ? (
          <input
            className="form-control form-control-sm"
            placeholder={t('kontakte.mappe.contactPersons.filterPlaceholder', { minimum: FILTER_MIN_CHARACTERS })}
            aria-label={t('kontakte.mappe.contactPersons.filterPlaceholder', { minimum: FILTER_MIN_CHARACTERS })}
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
          />
        ) : undefined
      }
      footer={footer}
    >
      <Table
        columns={columns}
        rows={[...shown]}
        rowKey={(row) => kontakteValueOf(row, KONTAKTE_ID_FIELD_NAME)}
        detailTitle={(row) => kontakteValueOf(row, KONTAKTE_NAME_FIELD_NAME)}
        emptyText={contactPersons.length === 0 ? t('kontakte.mappe.contactPersons.empty') : t('kontakte.mappe.contactPersons.noMatch', { filter: filter.trim() })}
        actions={(row) => (
          <AbasOpenButton
            compact
            sternzeit={kontakteValueOf(row, KONTAKTE_ID_FIELD_NAME)}
            hint={t('kontakte.mappe.contactPersons.openAbas', { name: kontakteValueOf(row, KONTAKTE_NAME_FIELD_NAME) || kontakteValueOf(row, KONTAKTE_ID_FIELD_NAME) })}
          />
        )}
      />
    </ContentBox>
  )
}
