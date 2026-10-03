import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { isValidKey, KEY_MAX_LENGTH, toKeyCharacters } from './keyRule.ts'

interface KeyInputProps {
  id: string
  /** der ganze Key, mit Präfix */
  value: string
  onChange: (key: string) => void
  /** Pflicht-Präfix, z. B. ARC_: steht fest vor dem Feld, getippt wird nur der Rest */
  prefix?: string
  required?: boolean
  disabled?: boolean
}

/**
 * Eingabefeld für jeden Key im System. Lässt beim Tippen nur erlaubte Zeichen zu (klein wird groß, Leerzeichen
 * wird _) und zeigt die Regel an, solange der Key noch nicht gültig ist, z. B. weil er auf _ endet.
 * Ungültig lässt sich das Formular nicht abschicken.
 */
export function KeyInput({ id, value, onChange, prefix = '', required = false, disabled = false }: KeyInputProps) {
  const { t } = useTranslation()
  const input = useRef<HTMLInputElement>(null)
  const invalid = value !== '' && !(isValidKey(value) && value.startsWith(prefix))
  const rule = t('common.keyRule')
  const ruleText = prefix ? t('common.keyRuleWithPrefix', { prefix, rule }) : rule

  // Der Browser hält das Formular auf, solange der Key ungültig ist
  useEffect(() => {
    input.current?.setCustomValidity(invalid ? ruleText : '')
  }, [invalid, ruleText])

  const inputElement = (
    <input
      ref={input}
      id={id}
      className={invalid ? 'form-control is-invalid' : 'form-control'}
      required={required}
      disabled={disabled}
      maxLength={KEY_MAX_LENGTH - prefix.length}
      autoComplete="off"
      spellCheck={false}
      value={value.slice(prefix.length)}
      onChange={(event) => {
        const rest = toKeyCharacters(event.target.value)
        onChange(rest === '' ? '' : prefix + rest)
      }}
    />
  )

  return (
    <>
      {prefix ? (
        <div className="input-group has-validation">
          <span className="input-group-text">{prefix}</span>
          {inputElement}
          {invalid && <div className="invalid-feedback">{ruleText}</div>}
        </div>
      ) : (
        <>
          {inputElement}
          {invalid && <div className="invalid-feedback">{ruleText}</div>}
        </>
      )}
    </>
  )
}
