import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { ValuesInput } from '../../components/ValuesInput.tsx'
import { groupSolrHooks, type SolrHook } from './SolrHook.ts'
import { SolrHookChip } from './SolrHookChip.tsx'
import { SolrHookPicker } from './SolrHookPicker.tsx'
import { SolrHookValues } from './SolrHookValues.tsx'

/** Die Hooks eines Dokuments: je Hook-Key seine Werte, alle Hooks sind multiValued */
export type SolrHookValuesByKey = Readonly<Record<string, readonly string[]>>

interface SolrHookFieldsProps {
  /** die Hooks des Dokuments. Ein leeres Array heißt: Feld geleert, daran erkennt das Speichern, dass es leer werden soll */
  hooks: SolrHookValuesByKey
  /** fehlt: nur lesen, die Werte stehen als Badges, Artikelnummern als Link */
  onChange?: (hooks: Record<string, string[]>) => void
  /** Sprache der Anzeigenamen */
  language: LanguageCode
  /** gesperrt, z. B. während gespeichert wird */
  disabled?: boolean
  /** Überschrift über dem Block, fehlt sie: „Hooks“ */
  title?: ReactNode
}

/**
 * Das Hook-Modul nach HookFelder der Vorlage: die Hooks eines Dokuments bearbeiten oder nur zeigen.
 *
 * Gezeigt wird nur, was gefüllt ist: alle Hooks gleichzeitig, fast alle leer, machen die Maske unlesbar. Fehlende
 * Hooks kommen über die Hook-Auswahl dazu (unten der Knopf), ein frisch hinzugefügter bleibt stehen, bis er Werte hat.
 * Je Hook eine Zeile: vorn der Chip, beim Bearbeiten mit Mülleimer, der das ganze Feld leert, dahinter die Werte, zum
 * Bearbeiten die Werte-Eingabe (Leertaste, Semikolon, Enter loggt ein, eine eingefügte Liste wird zerlegt).
 *
 * Entfernen leert das Feld (hooks[key] = []) statt es aus dem Objekt zu werfen: Daran erkennt das Speichern, das alle
 * Hooks ersetzt, dass das Feld geleert werden soll.
 *
 * Nur-Lesen zeigt dieselben Zeilen über SolrHookValues: dort ist eine Artikelnummer ein Link in ihre Mappe. Beim
 * Bearbeiten nicht, ein Klick verließe die ungespeicherte Maske. Die Reihenfolge ist die der Gruppen und Hooks aus
 * den Stammdaten, dieselbe wie in der Hook-Auswahl.
 */
export function SolrHookFields({ hooks, onChange, language, disabled = false, title }: SolrHookFieldsProps) {
  const { t } = useTranslation()
  const { solrHooks, solrHookGroups } = useMasterData()
  // Hooks, die gerade dazugeholt, aber noch nicht gefüllt sind: ohne diese Liste wäre ein neuer sofort wieder weg
  const [added, setAdded] = useState<string[]>([])

  const allHooks: SolrHook[] = groupSolrHooks(solrHooks ?? [], solrHookGroups ?? []).flatMap((group) => group.solrHooks)
  const visible = allHooks.filter((solrHook) => (hooks[solrHook.key]?.length ?? 0) > 0 || added.includes(solrHook.key))
  const readOnly = onChange === undefined

  function setValues(key: string, values: string[]) {
    onChange?.({ ...copy(hooks), [key]: values })
  }

  function removeField(key: string) {
    onChange?.({ ...copy(hooks), [key]: [] })
    setAdded((current) => current.filter((candidate) => candidate !== key))
  }

  function addField(solrHook: SolrHook) {
    if (!visible.some((candidate) => candidate.key === solrHook.key)) {
      setAdded((current) => [...current, solrHook.key])
    }
  }

  return (
    <div className="solr-hook-fields">
      <span className="solr-hook-fields-title">{title ?? t('solrmanager.hooks.fields.title')}</span>

      {visible.length === 0 && (
        <span className="text-secondary small">
          {readOnly ? t('solrmanager.hooks.fields.noneSet') : t('solrmanager.hooks.fields.noneYet')}
        </span>
      )}

      {visible.map((solrHook) =>
        readOnly ? (
          <div key={solrHook.key} className="solr-hook-field-line">
            <SolrHookValues solrHook={solrHook} values={hooks[solrHook.key] ?? []} language={language} />
          </div>
        ) : (
          <div key={solrHook.key} className="solr-hook-field-line">
            <SolrHookChip
              solrHook={solrHook}
              language={language}
              deleteLabel={t('solrmanager.hooks.fields.removeField', { key: solrHook.key })}
              onDelete={disabled ? undefined : () => removeField(solrHook.key)}
            />
            <ValuesInput
              values={hooks[solrHook.key] ?? []}
              disabled={disabled}
              placeholder={t('solrmanager.hooks.fields.addValue', { key: solrHook.key })}
              separator=";"
              ariaLabel={t('solrmanager.hooks.fields.addValueAria', { key: solrHook.key })}
              onChange={(values) => setValues(solrHook.key, values)}
              renderValue={(value, remove) => (
                <span className="solr-hook-value-badge">
                  {value}
                  <DeleteButton label={t('common.removeValue', { value })} disabled={disabled} onClick={remove} />
                </span>
              )}
            />
          </div>
        ),
      )}

      {!readOnly && (
        <div>
          <SolrHookPicker
            label={t('solrmanager.hooks.add')}
            language={language}
            excludeKeys={visible.map((solrHook) => solrHook.key)}
            disabled={disabled}
            onPick={addField}
          />
        </div>
      )}
    </div>
  )
}

/** Eine schreibbare Kopie, die Listen als neue Arrays */
function copy(hooks: SolrHookValuesByKey): Record<string, string[]> {
  return Object.fromEntries(Object.entries(hooks).map(([key, values]) => [key, [...values]]))
}
