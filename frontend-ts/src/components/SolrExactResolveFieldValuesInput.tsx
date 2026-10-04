import { useTranslation } from 'react-i18next'
import type { SolrExactResolveField, SolrExactResolveFieldValuesByFieldName } from '../modul/solrmanager/SolrExactResolve.ts'
import { DeleteButton } from './DeleteButton.tsx'
import { ValuesInput } from './ValuesInput.tsx'

interface SolrExactResolveFieldValuesInputProps {
  /** die Felder, die zur Wahl stehen, in der Reihenfolge der Stammdaten */
  fields: readonly SolrExactResolveField[]
  fieldValuesByFieldName: SolrExactResolveFieldValuesByFieldName
  onChange: (fieldValuesByFieldName: SolrExactResolveFieldValuesByFieldName) => void
  /** Anzeigename je Feld, z. B. der Hook-Name in der Sprache der Oberfläche. Fehlt er, steht der Solr-Name. */
  labelOf?: (field: SolrExactResolveField) => string
  disabled?: boolean
}

/** Ein Beispielwert je Typ im leeren Feld, damit man weiß, was hinein gehört */
function placeholderOf(type: SolrExactResolveField['type']): string {
  switch (type) {
    case 'BOOLEAN':
      return 'true'
    case 'LONG':
      return '4711'
    case 'DOUBLE':
      return '3.14'
    case 'DATE':
      return '2026-10-03T12:00:00Z'
    default:
      return 'Wert'
  }
}

/**
 * Die exakten Feldwerte einer Auflösung, geteilt von Auflöse-Maske und Dateifreigabe: je gewähltem Feld eine Zeile mit
 * der Werte-Eingabe (mehrere Werte ODER) und dem Mülleimer, darunter die Auswahl, mit der ein weiteres Feld dazukommt
 * (Felder UND). Geprüft wird hier nichts, das macht das Backend gegen die Felddefinition.
 */
export function SolrExactResolveFieldValuesInput({
  fields,
  fieldValuesByFieldName,
  onChange,
  labelOf,
  disabled = false,
}: SolrExactResolveFieldValuesInputProps) {
  const { t } = useTranslation()
  const chosen = fields.filter((field) => fieldValuesByFieldName[field.name] !== undefined)
  const available = fields.filter((field) => fieldValuesByFieldName[field.name] === undefined)
  const label = (field: SolrExactResolveField) => labelOf?.(field) ?? field.name

  function setValues(name: string, values: string[]) {
    onChange({ ...fieldValuesByFieldName, [name]: values })
  }

  function remove(name: string) {
    const next = { ...fieldValuesByFieldName }
    delete next[name]
    onChange(next)
  }

  return (
    <div className="d-flex flex-column gap-2">
      {chosen.map((field) => (
        <div key={field.name} className="d-flex flex-wrap align-items-center gap-2">
          <span style={{ minWidth: '12rem' }}>
            <strong>{label(field)}</strong>
            {field.hook && <span className="text-muted ms-1">({t('resolve.hook')})</span>}
            <span className="text-muted ms-1">{field.type.toLowerCase()}</span>
          </span>
          <ValuesInput
            values={fieldValuesByFieldName[field.name] ?? []}
            disabled={disabled}
            placeholder={placeholderOf(field.type)}
            ariaLabel={t('resolve.valuesOf', { field: label(field) })}
            onChange={(values) => setValues(field.name, values)}
          />
          <DeleteButton
            label={t('resolve.removeCondition', { field: label(field) })}
            disabled={disabled}
            onClick={() => remove(field.name)}
          />
        </div>
      ))}
      <select
        className="form-select form-select-sm w-auto"
        aria-label={t('resolve.addField')}
        value=""
        disabled={disabled || available.length === 0}
        onChange={(event) => {
          if (event.target.value !== '') {
            setValues(event.target.value, [])
          }
        }}
      >
        <option value="">{available.length === 0 ? t('resolve.noMoreFields') : t('resolve.addField')}</option>
        {available.map((field) => (
          <option key={field.name} value={field.name}>
            {label(field)}
            {field.hook ? ` (${t('resolve.hook')})` : ''}
          </option>
        ))}
      </select>
    </div>
  )
}
