import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import type { LanguageCode } from '../../branding/languages.ts'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { translate } from '../../components/multilingual.ts'
import type { SolrHook } from './SolrHook.ts'

interface SolrHookChipProps {
  solrHook: SolrHook
  /** der Wert des Filters. Ohne Wert zeigt der Chip nur den Hook, z. B. als Vorschau oder im Hook-Block eines Kerns */
  value?: string | null
  /** Sprache des Anzeigenamens, ohne Angabe die Standardsprache */
  language?: LanguageCode
  /** wohin der Wert führt, z. B. die Artikelmappe. Dann ist nur der Wert der Link, der Rest des Chips bleibt, was er ist */
  to?: string
  /** Klick auf den Chip, z. B. ein Angebot der Suche einloggen */
  onClick?: () => void
  /** weggelassen: nicht entfernbar, sonst erscheint der Mülleimer, z. B. am gesetzten Filter */
  onDelete?: () => void
  /** Beschriftung des Mülleimers, ohne Angabe „Filter … entfernen“ */
  deleteLabel?: string
}

/**
 * Der Chip eines Hooks: Anzeigename und Wert, als gesetzter Filter oder als Angebot. Nach dem Muster der FilterBadge der
 * Vorlage. Alle Hooks sehen gleich aus, der Look (Fels, anthrazit, rot glühende Risse) steht fest in basic.css
 * (solr-hook-chip), nichts davon kommt aus den Daten.
 *
 * Ein Knopf im Knopf geht nicht: Ist der Chip anklickbar und entfernbar, wird nicht er selbst zum Knopf, sondern sein
 * Inhalt, der Mülleimer steht daneben.
 */
export function SolrHookChip({ solrHook, value = null, language, to, onClick, onDelete, deleteLabel }: SolrHookChipProps) {
  const { t } = useTranslation()
  const name = translate(solrHook.displayName || null, language) || solrHook.key

  const content = (
    <>
      <span className="solr-hook-chip-hook">{name}</span>
      {value !== null &&
        (to ? (
          <Link to={to} className="solr-hook-chip-value solr-hook-chip-value-link" onClick={(event) => event.stopPropagation()}>
            {value}
          </Link>
        ) : (
          <span className="solr-hook-chip-value">{value}</span>
        ))}
    </>
  )

  if (onClick && !onDelete) {
    return (
      <button type="button" className="solr-hook-chip" onClick={onClick}>
        {content}
      </button>
    )
  }

  return (
    <span className="solr-hook-chip">
      {onClick ? (
        <button type="button" className="solr-hook-chip-button" onClick={onClick}>
          {content}
        </button>
      ) : (
        content
      )}
      {onDelete && (
        <DeleteButton
          label={deleteLabel ?? t('solrmanager.hooks.removeFilter', { name, value: value ?? '' })}
          onClick={onDelete}
        />
      )}
    </span>
  )
}
