import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { highlight } from '../../components/highlight.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { Popup } from '../../components/Popup.tsx'
import { isSolrFieldOfCore, type SolrField } from './SolrField.ts'

interface SolrFieldPickerProps {
  /** Beschriftung des Knopfes und Titel des Popups, z. B. „Feld verknüpfen“ */
  label: string
  /** Sprache der Anzeigenamen */
  language: LanguageCode
  /** die Felder, die zur Wahl stehen; der Aufrufer hat schon aussortiert, was nicht geht */
  solrFields: readonly SolrField[]
  /** der Kern, der beim Öffnen gewählt ist, z. B. der des Ziels; null heißt alle Kerne */
  initialCoreKey?: string | null
  onPick: (solrField: SolrField) => void
  disabled?: boolean
}

/**
 * Die Feld-Auswahl nach dem Muster der Hook-Auswahl (User 2026-10-10): Ein Knopf öffnet ein Popup, oben die Wahl des
 * Kerns, vorbelegt mit dem Kern des Ziels, daneben das Suchfeld mit dem Fokus. Darunter die Felder des Kerns als
 * Klickzeilen mit Solr-Name, Anzeigename und Typ; ein Feld, das schon ein Mapping füllt, zeigt dessen Key. Tippen
 * filtert live per Teilstring, Enter nimmt den ersten Treffer, ein Klick wählt und schließt.
 */
export function SolrFieldPicker({ label, language, solrFields, initialCoreKey = null, onPick, disabled = false }: SolrFieldPickerProps) {
  const { t } = useTranslation()
  const { solrCores } = useMasterData()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [coreKey, setCoreKey] = useState<string | null>(initialCoreKey)
  const searchInput = useRef<HTMLInputElement>(null)

  // Fokus ins Suchfeld, sobald der dialog offen ist. Das Popup öffnet ihn erst nach dem Zeichnen, deshalb kurz warten.
  useEffect(() => {
    if (!open) {
      return undefined
    }
    const timer = window.setTimeout(() => searchInput.current?.focus(), 0)
    return () => window.clearTimeout(timer)
  }, [open])

  const needle = query.trim().toLowerCase()
  const ofCore = coreKey === null ? solrFields : solrFields.filter((solrField) => isSolrFieldOfCore(solrField, coreKey))
  const shown = ofCore.filter(
    (solrField) =>
      needle === '' ||
      solrField.name.toLowerCase().includes(needle) ||
      translate(solrField.displayName, language).toLowerCase().includes(needle),
  )

  function openPicker() {
    setCoreKey(initialCoreKey)
    setOpen(true)
  }

  function close() {
    setOpen(false)
    setQuery('')
  }

  function pick(solrField: SolrField) {
    onPick(solrField)
    close()
  }

  // Esc und Klick daneben schließt das Popup selbst, hier nur Enter: der erste Treffer
  function pickFirstOnEnter(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter') {
      event.preventDefault()
      const first = shown[0]
      if (first) {
        pick(first)
      }
    }
  }

  return (
    <>
      <button type="button" className="button-other" disabled={disabled} onClick={openPicker}>
        <Icon name="search" /> {label}
      </button>
      <Popup
        open={open}
        title={label}
        onClose={close}
        footer={<span>{t('solrmanager.sheet.fieldPicker.shown', { shown: shown.length, total: ofCore.length })}</span>}
      >
        <div className="d-flex flex-column gap-2">
          <div className="d-flex gap-2">
            <select
              className="form-select form-select-sm w-auto"
              aria-label={t('solrmanager.sheet.fieldPicker.core')}
              value={coreKey ?? ''}
              onChange={(event) => setCoreKey(event.target.value === '' ? null : event.target.value)}
            >
              <option value="">{t('solrmanager.sheet.fieldPicker.allCores')}</option>
              {(solrCores ?? []).map((solrCore) => (
                <option key={solrCore.key} value={solrCore.key}>
                  {translate(solrCore.displayName || null, language) || solrCore.key}
                </option>
              ))}
            </select>
            <input
              ref={searchInput}
              className="form-control form-control-sm"
              type="search"
              placeholder={t('solrmanager.sheet.fieldPicker.placeholder')}
              aria-label={t('solrmanager.sheet.fieldPicker.searchAria')}
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              onKeyDown={pickFirstOnEnter}
            />
          </div>
          {shown.length === 0 ? (
            <p className="mb-0 text-body-secondary">{t('solrmanager.sheet.fieldPicker.noMatches')}</p>
          ) : (
            <ul className="list-unstyled mb-0 d-flex flex-column gap-1">
              {shown.map((solrField) => {
                const displayName = translate(solrField.displayName, language)
                return (
                  <li key={solrField.id}>
                    <button type="button" className="button-other w-100 text-start d-flex align-items-center gap-2 flex-wrap" onClick={() => pick(solrField)}>
                      <span className="badge text-bg-secondary fw-normal">{solrField.coreKey ?? '*'}</span>
                      <code>{highlight(solrField.name, needle)}</code>
                      {displayName !== '' && <span>{highlight(displayName, needle)}</span>}
                      <span className="text-body-secondary small ms-auto">
                        {solrField.type}
                        {solrField.multiValued && ' []'}
                      </span>
                      {solrField.command !== null && (
                        <span className="badge text-bg-warning fw-normal">{t('solrmanager.sheet.fieldPicker.hasMapping', { key: solrField.command })}</span>
                      )}
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      </Popup>
    </>
  )
}
