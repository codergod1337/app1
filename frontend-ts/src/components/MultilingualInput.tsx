import { DEFAULT_LANGUAGE, type LanguageCode } from '../branding/languages.ts'
import { parseMultilingual, stringifyMultilingual } from './multilingual.ts'

interface MultilingualInputProps {
  id: string
  /** mehrsprachiges JSON, z. B. {"de":"Verwalter"}, oder null */
  value: string | null
  /** die Sprache, die gerade bearbeitet wird (gewählt über die Flaggen der ContentBox) */
  language: LanguageCode
  onChange: (value: string | null) => void
  /** Pflicht ist nur der Text der Standardsprache */
  required?: boolean
  disabled?: boolean
  /** mehrzeilig (textarea) für längere Texte, z. B. den Profiltext */
  multiline?: boolean
  maxLength?: number
}

/** Bearbeitet nur den Text der gewählten Sprache, die anderen Sprachen im JSON bleiben erhalten. */
export function MultilingualInput({
  id,
  value,
  language,
  onChange,
  required = false,
  disabled = false,
  multiline = false,
  maxLength,
}: MultilingualInputProps) {
  const text = parseMultilingual(value)
  const fieldProps = {
    id,
    className: 'form-control',
    required: required && language === DEFAULT_LANGUAGE,
    disabled,
    maxLength,
    // in einer anderen Sprache als Hilfe den Text der Standardsprache zeigen
    placeholder: language === DEFAULT_LANGUAGE ? '' : (text[DEFAULT_LANGUAGE] ?? ''),
    value: text[language] ?? '',
  }

  return multiline ? (
    <textarea
      {...fieldProps}
      rows={8}
      onChange={(event) => onChange(stringifyMultilingual({ ...text, [language]: event.target.value }))}
    />
  ) : (
    <input
      {...fieldProps}
      onChange={(event) => onChange(stringifyMultilingual({ ...text, [language]: event.target.value }))}
    />
  )
}
