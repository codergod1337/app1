import { LANGUAGES, type LanguageCode } from '../branding/languages.ts'
import { Hoverlay } from './Hoverlay.tsx'

interface LanguageFlagsProps {
  value: LanguageCode
  onChange: (language: LanguageCode) => void
}

/** Die Flaggen aller aktivierten Sprachen zum Umschalten. Beim Hover der Name des Landes in dessen Sprache. */
export function LanguageFlags({ value, onChange }: LanguageFlagsProps) {
  return (
    <div className="language-flags">
      {LANGUAGES.map(({ code, flag, countryName }) => (
        <Hoverlay key={code} text={countryName} alignRight>
          <button
            type="button"
            className={code === value ? 'language-flag active' : 'language-flag'}
            aria-label={countryName}
            aria-pressed={code === value}
            onClick={() => onChange(code)}
          >
            <span className={`fi fi-${flag}`} />
          </button>
        </Hoverlay>
      ))}
    </div>
  )
}
