import { useTranslation } from 'react-i18next'
import { ValuesInput } from '../../components/ValuesInput.tsx'
import type { SolrField } from './SolrField.ts'
import type { SolrFieldRawValue } from './SolrFieldValue.ts'

interface SolrFieldValueInputProps {
  id: string
  solrField: SolrField
  /** undefined: nicht gesetzt, das Feld wird nicht geschickt */
  value: SolrFieldRawValue | undefined
  onChange: (value: SolrFieldRawValue | undefined) => void
  disabled?: boolean
}

/**
 * Eine Eingabe für den Wert eines Solr-Feldes, nach seinem Typ: Zahl, Zeitpunkt, true/false, sonst Text, bei
 * multiValued die Werte-Eingabe. Geprüft wird hier nichts: Das macht das Backend gegen die Felddefinition und nennt
 * jede Beanstandung, deshalb bleibt alles roher Text bis zum Senden (solrFieldDocument).
 */
export function SolrFieldValueInput({ id, solrField, value, onChange, disabled = false }: SolrFieldValueInputProps) {
  const { t } = useTranslation()

  if (solrField.multiValued) {
    return (
      <ValuesInput
        values={Array.isArray(value) ? value : []}
        disabled={disabled}
        placeholder={t('solrmanager.valueInput.addValue')}
        ariaLabel={t('solrmanager.valueInput.addValueAria', { name: solrField.name })}
        onChange={(values) => onChange(values.length > 0 ? values : undefined)}
      />
    )
  }

  const text = typeof value === 'string' ? value : ''
  const change = (next: string) => onChange(next === '' ? undefined : next)

  switch (solrField.type) {
    case 'BOOLEAN':
      return (
        <select
          id={id}
          className="form-select form-select-sm"
          value={text}
          disabled={disabled}
          onChange={(event) => change(event.target.value)}
        >
          <option value="">{t('solrmanager.valueInput.unset')}</option>
          <option value="true">true</option>
          <option value="false">false</option>
        </select>
      )
    case 'LONG':
    case 'DOUBLE':
      return (
        <input
          id={id}
          type="number"
          step={solrField.type === 'LONG' ? '1' : 'any'}
          className="form-control form-control-sm"
          value={text}
          disabled={disabled}
          onChange={(event) => change(event.target.value)}
        />
      )
    case 'DATE':
      return (
        <input
          id={id}
          type="datetime-local"
          step="1"
          className="form-control form-control-sm"
          value={text}
          disabled={disabled}
          onChange={(event) => change(event.target.value)}
        />
      )
    case 'TEXT_SUCHE':
      return (
        <textarea
          id={id}
          rows={3}
          className="form-control form-control-sm"
          value={text}
          disabled={disabled}
          onChange={(event) => change(event.target.value)}
        />
      )
    default:
      return (
        <input
          id={id}
          type="text"
          className="form-control form-control-sm"
          value={text}
          disabled={disabled}
          onChange={(event) => change(event.target.value)}
        />
      )
  }
}
