import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { groupSolrHooks } from './SolrHook.ts'
import { SolrHookChip } from './SolrHookChip.tsx'
import { SolrHookPicker } from './SolrHookPicker.tsx'

interface SolrHookKeysInputProps {
  /** die gewählten Hook-Keys */
  value: readonly string[]
  onChange: (hookKeys: string[]) => void
  language: LanguageCode
  disabled?: boolean
}

/**
 * Eine Liste von Hooks wählen, ohne Werte (User 2026-10-14, für die Trigger-Hooks von Nachrichtenart und Dateiart): die
 * gewählten als Chips mit Mülleimer, dazu die Hook-Auswahl für weitere. Die Reihenfolge ist die der Gruppen und Hooks
 * aus den Stammdaten. Ein Key, den es nicht mehr gibt, steht als key da und lässt sich entfernen.
 */
export function SolrHookKeysInput({ value, onChange, language, disabled = false }: SolrHookKeysInputProps) {
  const { t } = useTranslation()
  const { solrHooks, solrHookGroups } = useMasterData()
  const ordered = groupSolrHooks(solrHooks ?? [], solrHookGroups ?? []).flatMap((group) => group.solrHooks)
  const chosen = ordered.filter((solrHook) => value.includes(solrHook.key))
  const unknownKeys = value.filter((key) => !ordered.some((solrHook) => solrHook.key === key))

  function remove(key: string) {
    onChange(value.filter((candidate) => candidate !== key))
  }

  return (
    <div className="d-flex flex-wrap align-items-center gap-2">
      {chosen.map((solrHook) => (
        <SolrHookChip
          key={solrHook.key}
          solrHook={solrHook}
          language={language}
          deleteLabel={t('solrmanager.hooks.keysInput.remove', { key: solrHook.key })}
          onDelete={disabled ? undefined : () => remove(solrHook.key)}
        />
      ))}
      {unknownKeys.map((key) => (
        <span key={key} className="d-inline-flex align-items-center gap-1">
          <code>{key}</code>
          {!disabled && (
            <button type="button" className="button-other" onClick={() => remove(key)}>
              {t('solrmanager.hooks.keysInput.remove', { key })}
            </button>
          )}
        </span>
      ))}
      {value.length === 0 && <span className="text-body-secondary small">{t('solrmanager.hooks.keysInput.none')}</span>}
      <SolrHookPicker
        label={t('solrmanager.hooks.add')}
        language={language}
        excludeKeys={value}
        disabled={disabled}
        onPick={(solrHook) => onChange([...value, solrHook.key])}
      />
    </div>
  )
}
