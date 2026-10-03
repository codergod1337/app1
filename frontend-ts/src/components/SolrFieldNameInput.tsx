import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { isValidSolrFieldName, SOLR_FIELD_NAME_MAX_LENGTH, toSolrFieldNameCharacters } from './solrFieldNameRule.ts'

interface SolrFieldNameInputProps {
  id: string
  value: string
  onChange: (fieldName: string) => void
  required?: boolean
  disabled?: boolean
}

/**
 * Eingabefeld für einen Solr-Feldnamen, z. B. den key eines Hooks. Lässt beim Tippen nur erlaubte Zeichen zu und
 * zeigt die Regel an, solange der Name noch nicht gültig ist, z. B. weil er auf _ endet.
 * Ungültig lässt sich das Formular nicht abschicken.
 */
export function SolrFieldNameInput({ id, value, onChange, required = false, disabled = false }: SolrFieldNameInputProps) {
  const { t } = useTranslation()
  const input = useRef<HTMLInputElement>(null)
  const invalid = value !== '' && !isValidSolrFieldName(value)
  const ruleText = t('common.solrFieldNameRule')

  // Der Browser hält das Formular auf, solange der Name ungültig ist
  useEffect(() => {
    input.current?.setCustomValidity(invalid ? ruleText : '')
  }, [invalid, ruleText])

  return (
    <>
      <input
        ref={input}
        id={id}
        className={invalid ? 'form-control is-invalid' : 'form-control'}
        required={required}
        disabled={disabled}
        maxLength={SOLR_FIELD_NAME_MAX_LENGTH}
        autoComplete="off"
        spellCheck={false}
        value={value}
        onChange={(event) => onChange(toSolrFieldNameCharacters(event.target.value))}
      />
      {invalid && <div className="invalid-feedback">{ruleText}</div>}
    </>
  )
}
