import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { SolrHookChip } from '../solrmanager/SolrHookChip.tsx'
import type { SearchDateRangeFilter, SearchFilter } from './Search.ts'
import type { SearchCore } from './searchCores.ts'

interface SearchFilterEditorProps {
  /** die angewählten Kerne: ihre Felder stehen zur Wahl */
  cores: readonly SearchCore[]
  filters: readonly SearchFilter[]
  dateRanges: readonly SearchDateRangeFilter[]
  onAddFilter: (filter: SearchFilter) => void
  onRemoveFilter: (filter: SearchFilter) => void
  onAddDateRange: (dateRange: SearchDateRangeFilter) => void
  onRemoveDateRange: (fieldName: string) => void
  language: LanguageCode
}

/** Was im Feld-Select gewählt ist: ein exaktes Feld oder ein Datumsfeld für einen Zeitraum */
type FieldChoice = { kind: 'exact'; fieldName: string } | { kind: 'date'; fieldName: string } | null

function choiceOf(value: string): FieldChoice {
  if (value.startsWith('exact:')) {
    return { kind: 'exact', fieldName: value.slice('exact:'.length) }
  }
  if (value.startsWith('date:')) {
    return { kind: 'date', fieldName: value.slice('date:'.length) }
  }
  return null
}

/**
 * Die Filter der Suche: oben die gesetzten Chips (Hooks als Hook-Chip, andere Felder als Pille, Zeiträume mit
 * Kalender), jeder einzeln entfernbar; darunter die Zeile, mit der ein Filter von Hand dazukommt: Feld aus den Hooks
 * und den festen Feldern der angewählten Kerne, Wert, bei Datumsfeldern von und bis. Chips sind (Feld, Wert) ohne
 * Kern: Sie gehen an jeden Kern, wer das Feld nicht kennt, überliest sie.
 */
export function SearchFilterEditor({
  cores,
  filters,
  dateRanges,
  onAddFilter,
  onRemoveFilter,
  onAddDateRange,
  onRemoveDateRange,
  language,
}: SearchFilterEditorProps) {
  const { t } = useTranslation()
  const { solrHooks } = useMasterData()
  const [chosen, setChosen] = useState('')
  const [value, setValue] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')

  const choice = choiceOf(chosen)
  const hooks = solrHooks ?? []
  const dateFieldNames = [...new Set(cores.flatMap((core) => core.dateRangeFilterFieldNames))]
  const hookOf = (fieldName: string) => hooks.find((hook) => hook.key === fieldName) ?? null
  const labelOf = (fieldName: string) => {
    const hook = hookOf(fieldName)
    return hook ? translate(hook.displayName, language) || hook.key : fieldName
  }
  const canAdd =
    choice !== null &&
    (choice.kind === 'exact' ? value.trim() !== '' : from !== '' || to !== '') &&
    (choice.kind !== 'date' || from === '' || to === '' || from <= to)

  function add(event: FormEvent) {
    event.preventDefault()
    if (choice === null || !canAdd) {
      return
    }
    if (choice.kind === 'exact') {
      onAddFilter({ fieldName: choice.fieldName, value: value.trim() })
      setValue('')
    } else {
      onAddDateRange({ fieldName: choice.fieldName, from: from === '' ? null : from, to: to === '' ? null : to })
      setFrom('')
      setTo('')
    }
  }

  const chipLabel = (filter: SearchFilter) => (filter.label ? `${filter.label} (${filter.value})` : filter.value)

  return (
    <div className="d-flex flex-column gap-2">
      {(filters.length > 0 || dateRanges.length > 0) && (
        <div className="search-chips">
          {filters.map((filter) => {
            const hook = hookOf(filter.fieldName)
            const key = `${filter.fieldName}=${filter.value}`
            if (hook) {
              return (
                <SolrHookChip
                  key={key}
                  solrHook={hook}
                  value={chipLabel(filter)}
                  language={language}
                  onDelete={() => onRemoveFilter(filter)}
                  deleteLabel={t('search.filters.remove', { field: labelOf(filter.fieldName), value: filter.value })}
                />
              )
            }
            return (
              <span key={key} className="search-filter-chip">
                <span className="search-filter-chip-field">{filter.fieldName}</span>
                <span>{chipLabel(filter)}</span>
                <DeleteButton
                  label={t('search.filters.remove', { field: filter.fieldName, value: filter.value })}
                  onClick={() => onRemoveFilter(filter)}
                />
              </span>
            )
          })}
          {dateRanges.map((dateRange) => (
            <span key={dateRange.fieldName} className="search-filter-chip">
              <Icon name="calendar" />
              <span className="search-filter-chip-field">{dateRange.fieldName}</span>
              <span>
                {dateRange.from ?? t('search.filters.open')} – {dateRange.to ?? t('search.filters.open')}
              </span>
              <DeleteButton
                label={t('search.filters.removeDateRange', { field: dateRange.fieldName })}
                onClick={() => onRemoveDateRange(dateRange.fieldName)}
              />
            </span>
          ))}
        </div>
      )}

      <form className="search-filter-editor" onSubmit={add}>
        <Icon name="filter" />
        <select
          className="form-select form-select-sm"
          value={chosen}
          aria-label={t('search.filters.chooseField')}
          onChange={(event) => setChosen(event.target.value)}
        >
          <option value="">{t('search.filters.chooseField')}</option>
          {hooks.length > 0 && (
            <optgroup label={t('search.filters.hooks')}>
              {hooks.map((hook) => (
                <option key={hook.key} value={`exact:${hook.key}`}>
                  {translate(hook.displayName, language) || hook.key} ({hook.key})
                </option>
              ))}
            </optgroup>
          )}
          {cores.map((core) => (
            <optgroup key={core.key} label={t('search.filters.fieldsOfCore', { core: t(`search.cores.${core.labelKey}`) })}>
              {core.exactFilterFieldNames.map((fieldName) => (
                <option key={fieldName} value={`exact:${fieldName}`}>
                  {fieldName}
                </option>
              ))}
            </optgroup>
          ))}
          {dateFieldNames.length > 0 && (
            <optgroup label={t('search.filters.dateFields')}>
              {dateFieldNames.map((fieldName) => (
                <option key={fieldName} value={`date:${fieldName}`}>
                  {fieldName}
                </option>
              ))}
            </optgroup>
          )}
        </select>
        {choice?.kind === 'exact' && (
          <input
            type="text"
            className="form-control form-control-sm"
            value={value}
            placeholder={t('search.filters.value')}
            aria-label={t('search.filters.value')}
            onChange={(event) => setValue(event.target.value)}
          />
        )}
        {choice?.kind === 'date' && (
          <>
            <label className="small text-muted">{t('search.filters.from')}</label>
            <input
              type="date"
              className="form-control form-control-sm"
              value={from}
              aria-label={t('search.filters.from')}
              onChange={(event) => setFrom(event.target.value)}
            />
            <label className="small text-muted">{t('search.filters.to')}</label>
            <input
              type="date"
              className="form-control form-control-sm"
              value={to}
              aria-label={t('search.filters.to')}
              onChange={(event) => setTo(event.target.value)}
            />
          </>
        )}
        {choice !== null && (
          <button type="submit" className="button-other" disabled={!canAdd}>
            <Icon name="add" /> {t('search.filters.add')}
          </button>
        )}
      </form>
    </div>
  )
}
