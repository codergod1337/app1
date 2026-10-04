import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { Popup } from '../../components/Popup.tsx'
import { documentLanguageFlagOf, documentLanguageName, documentLanguagesInUiOrder } from './DmsDocumentLanguage.ts'

interface DmsDocumentLanguagePickerProps {
  /** der Code, null: keine Sprache */
  value: string | null
  onChange: (code: string | null) => void
  disabled?: boolean
  /** nur die gewählte Flagge, die Reihe öffnet sich im Popup: für die Zelle einer Tabelle */
  compact?: boolean
}

/**
 * Die Sprache eines Dokuments wählen: vorn „keine“ als Strich, dann die Flaggen in der Reihenfolge der Anzeige (de, en,
 * bg, der Rest alphabetisch nach dem Namen in der Oberflächensprache), der Name im Hoverlay, Klick wählt. Dieselbe Reihe
 * im Upload je Datei (kompakt, im Popup) und in der Bearbeiten-Maske (offen).
 */
export function DmsDocumentLanguagePicker({ value, onChange, disabled = false, compact = false }: DmsDocumentLanguagePickerProps) {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const [open, setOpen] = useState(false)

  function choose(code: string | null) {
    onChange(code)
    setOpen(false)
  }

  const noneLabel = t('dms.language.none')
  const row = (
    <span className="document-language-picker" role="group" aria-label={t('dms.language.pickerLabel')}>
      <Hoverlay text={noneLabel}>
        <button
          type="button"
          className={`language-flag document-language-none${value === null ? ' active' : ''}`}
          aria-label={noneLabel}
          aria-pressed={value === null}
          disabled={disabled}
          onClick={() => choose(null)}
        >
          —
        </button>
      </Hoverlay>
      {documentLanguagesInUiOrder(language).map(({ code, flag }) => {
        const name = documentLanguageName(code, language)
        return (
          <Hoverlay key={code} text={`${name} (${code})`}>
            <button
              type="button"
              className={`language-flag${code === value ? ' active' : ''}`}
              aria-label={name}
              aria-pressed={code === value}
              disabled={disabled}
              onClick={() => choose(code)}
            >
              <span className={`fi fi-${flag}`} />
            </button>
          </Hoverlay>
        )
      })}
    </span>
  )

  if (!compact) {
    return row
  }

  const flag = value === null ? null : documentLanguageFlagOf(value)
  const current = value === null ? noneLabel : `${documentLanguageName(value, language)} (${value})`
  return (
    <>
      <Hoverlay text={current}>
        <button
          type="button"
          className="language-flag active"
          aria-label={t('dms.language.pickerLabel')}
          disabled={disabled}
          onClick={() => setOpen(true)}
        >
          {flag !== null ? <span className={`fi fi-${flag}`} /> : <span className="document-language-none">—</span>}
        </button>
      </Hoverlay>
      <Popup open={open} title={t('dms.language.pickerLabel')} onClose={() => setOpen(false)}>
        {row}
      </Popup>
    </>
  )
}
