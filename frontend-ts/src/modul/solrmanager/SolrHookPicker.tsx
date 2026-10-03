import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { Icon } from '../../components/Icon.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { Popup } from '../../components/Popup.tsx'
import { groupSolrHooks, type SolrHook } from './SolrHook.ts'
import { SolrHookCatalog } from './SolrHookCatalog.tsx'

interface SolrHookPickerProps {
  /** Beschriftung des Knopfes und Titel des Popups, z. B. „Hook hinzufügen“ */
  label: string
  /** Sprache der Anzeigenamen und Gruppen */
  language: LanguageCode
  /** Hooks, die nicht angeboten werden, z. B. die eine Maske schon zeigt */
  excludeKeys?: readonly string[]
  onPick: (solrHook: SolrHook) => void
  /** gesperrt, z. B. während ein Upload läuft */
  disabled?: boolean
}

/**
 * Die Hook-Auswahl nach der HookAuswahl der Vorlage: Ein Knopf öffnet ein Popup mit Suchfeld, der Fokus sitzt sofort
 * darin. Darunter der Hook-Katalog (SolrHookCatalog) mit allen noch nicht gewählten Hooks als Klickzeilen. Tippen
 * filtert live per Teilstring über Feldname und Anzeigename, Enter nimmt den ersten Treffer, ein Klick wählt und
 * schließt, Esc und ein Klick daneben schließen.
 *
 * Anders als in der Vorlage kommen die Hooks aus den Stammdaten, nicht aus einer festen Liste: Ein neuer Hook steht
 * sofort überall zur Wahl. Die Presets 1 bis 9 der Vorlage sind nicht übernommen, dort waren alle leer.
 */
export function SolrHookPicker({ label, language, excludeKeys = [], onPick, disabled = false }: SolrHookPickerProps) {
  const { t } = useTranslation()
  const { solrHooks, solrHookGroups } = useMasterData()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
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
  const available = (solrHooks ?? []).filter((solrHook) => !excludeKeys.includes(solrHook.key))
  const groups = groupSolrHooks(available, solrHookGroups ?? [])
    .map((group) => ({
      ...group,
      solrHooks: group.solrHooks.filter(
        (solrHook) =>
          needle === '' ||
          solrHook.key.toLowerCase().includes(needle) ||
          translate(solrHook.displayName, language).toLowerCase().includes(needle),
      ),
    }))
    .filter((group) => group.solrHooks.length > 0)
  const shownCount = groups.reduce((sum, group) => sum + group.solrHooks.length, 0)

  function close() {
    setOpen(false)
    setQuery('')
  }

  function pick(solrHook: SolrHook) {
    onPick(solrHook)
    close()
  }

  // Esc und Klick daneben schließt das Popup selbst, hier nur Enter: der erste Treffer
  function pickFirstOnEnter(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter') {
      event.preventDefault()
      const first = groups[0]?.solrHooks[0]
      if (first) {
        pick(first)
      }
    }
  }

  return (
    <>
      <button type="button" className="button-other" disabled={disabled} onClick={() => setOpen(true)}>
        <Icon name="search" /> {label}
      </button>
      <Popup
        open={open}
        title={label}
        onClose={close}
        footer={<span>{t('solrmanager.hooks.picker.shown', { shown: shownCount, total: available.length })}</span>}
      >
        <input
          ref={searchInput}
          className="form-control form-control-sm"
          type="search"
          placeholder={t('solrmanager.hooks.picker.placeholder')}
          aria-label={t('solrmanager.hooks.picker.searchAria')}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={pickFirstOnEnter}
        />
        <div className="solr-hook-picker-list">
          <SolrHookCatalog
            groups={groups}
            language={language}
            needle={needle}
            onPick={pick}
            emptyText={t('solrmanager.hooks.picker.noMatches')}
          />
        </div>
      </Popup>
    </>
  )
}
