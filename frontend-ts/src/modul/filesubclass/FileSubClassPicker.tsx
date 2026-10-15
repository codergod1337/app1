import { useState, type KeyboardEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { highlight } from '../../components/highlight.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { translate } from '../../components/multilingual.ts'
import type { FileSubClass } from './FileSubClass.ts'
import { FileSubClassSymbol } from './FileSubClassSymbol.tsx'

/** Was eine Kachel braucht: Dateiart und Nachrichtenart haben beide diese Felder (User 2026-10-14: MSC wie FSC wählen) */
export type PickableSubClass =Pick<FileSubClass, 'key' | 'displayName' | 'listingPosition' | 'symbol' | 'color'>

interface FileSubClassPickerProps {
  /** die angebotenen Dateiarten oder Nachrichtenarten. Wer filtert, entscheidet der Aufrufer */
  fileSubClasses: readonly PickableSubClass[]
  /** der gewählte key, null: noch keine Wahl */
  value: string | null
  onChange: (key: string) => void
  /** Sprache der Anzeigenamen */
  language: LanguageCode
  /** alle Kacheln unklickbar, z. B. während ein Upload läuft */
  disabled?: boolean
  /** Beschriftung über den Kacheln, ohne Angabe „Dateiart“, null blendet sie aus */
  title?: ReactNode | null
  /** zeigt den Stern hinter der Beschriftung */
  required?: boolean
}

/**
 * Die Dateiart-Auswahl nach der FileSubClassAuswahl der Vorlage: klickbare Kacheln mit Symbol, Positionsnummer und
 * Anzeigename. Eine Komponente für jede Stelle, an der eine Dateiart gewählt wird (Upload, später das Bearbeiten eines
 * Dokuments), damit die Wahl überall gleich aussieht. Die Nachrichtenart beim Archivieren wählt man über dieselben
 * Kacheln, mit eigenem title.
 *
 * Immer nach listingPosition sortiert, bei gleicher Position nach key: So liegt jede Kachel bei jedem Aufruf an
 * derselben Stelle. Ist gewählt, bleibt nur die gewählte Kachel stehen, dieselbe Optik an derselben Stelle, ein Klick
 * darauf holt alle zurück. Über den offenen Kacheln ein Suchfeld wie in der Hook-Auswahl: Teilstring über den
 * Anzeigenamen, Fundstellen markiert, Enter nimmt den ersten Treffer, Escape leert das Feld.
 *
 * Die Form um das Symbol kommt später von der Gruppe der Endung, bis dahin steht das Symbol allein.
 */
export function FileSubClassPicker({
  fileSubClasses,
  value,
  onChange,
  language,
  disabled = false,
  title,
  required = false,
}: FileSubClassPickerProps) {
  const { t } = useTranslation()
  /** alle Kacheln offen, obwohl schon gewählt ist: Klick auf die Wahl */
  const [expanded, setExpanded] = useState(false)
  const [query, setQuery] = useState('')

  const sorted = [...fileSubClasses].sort(
    (a, b) => a.listingPosition - b.listingPosition || a.key.localeCompare(b.key),
  )
  const chosen = sorted.find((fileSubClass) => fileSubClass.key === value) ?? null
  const collapsed = chosen !== null && !expanded
  const searchable = !collapsed && !disabled && sorted.length > 1
  const needle = searchable ? query.trim().toLowerCase() : ''
  const nameOf = (fileSubClass: PickableSubClass) => translate(fileSubClass.displayName, language) || fileSubClass.key
  const matches = needle === '' ? sorted : sorted.filter((fileSubClass) => nameOf(fileSubClass).toLowerCase().includes(needle))
  const visible = collapsed ? [chosen] : matches

  function click(key: string) {
    if (collapsed) {
      // die einzelne Kachel ist der Aufklapper, keine erneute Wahl
      setExpanded(true)
      setQuery('')
      return
    }
    onChange(key)
    setExpanded(false)
    setQuery('')
  }

  function keyDown(event: KeyboardEvent<HTMLInputElement>) {
    // Escape leert nur das Feld, ein umgebendes Popup bleibt offen
    if (event.key === 'Escape' && query !== '') {
      event.preventDefault()
      event.stopPropagation()
      setQuery('')
      return
    }
    // Enter nimmt den ersten Treffer und schickt kein Formular ab
    if (event.key === 'Enter') {
      event.preventDefault()
      if (needle !== '' && matches.length > 0) {
        click(matches[0].key)
      }
    }
  }

  if (fileSubClasses.length === 0) {
    return null
  }

  return (
    <div className="file-sub-class-picker">
      {title !== null && (
        <span className="file-sub-class-picker-title">
          {title ?? t('filesubclass.picker.title')}
          {required && <span className="text-danger"> *</span>}
        </span>
      )}
      {searchable && (
        <input
          type="search"
          className="form-control form-control-sm file-sub-class-picker-search"
          value={query}
          placeholder={t('common.search')}
          aria-label={t('common.search')}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={keyDown}
        />
      )}
      {needle !== '' && matches.length === 0 && (
        <span className="file-sub-class-picker-empty">{t('common.noMatches')}</span>
      )}
      <div className="file-sub-class-picker-tiles">
        {visible.map((fileSubClass) => {
          const active = fileSubClass.key === value
          return (
            <Hoverlay
              key={fileSubClass.key}
              text={collapsed && !disabled ? t('filesubclass.picker.clickToChange', { key: fileSubClass.key }) : fileSubClass.key}
            >
              <button
                type="button"
                className={`file-sub-class-picker-tile${active ? ' active' : ''}`}
                disabled={disabled}
                aria-pressed={active}
                onClick={() => click(fileSubClass.key)}
              >
                <FileSubClassSymbol fileSubClass={fileSubClass} />
                <span className="file-sub-class-picker-name">
                  <span className="file-sub-class-picker-position">{fileSubClass.listingPosition}</span>
                  {highlight(nameOf(fileSubClass), needle)}
                </span>
              </button>
            </Hoverlay>
          )
        })}
      </div>
    </div>
  )
}
