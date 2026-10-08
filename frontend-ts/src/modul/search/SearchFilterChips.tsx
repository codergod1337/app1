import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { FILE_SUB_CLASS_FIELD_NAME } from '../dms/Dms.ts'
import { fileSubClassNameOf } from '../filesubclass/FileSubClass.ts'
import { solrCoreColorStyle, solrCoreOfFieldName } from '../solrmanager/SolrCore.ts'
import { SolrHookChip } from '../solrmanager/SolrHookChip.tsx'
import type { SearchFilter } from './Search.ts'

interface SearchFilterChipsProps {
  filters: readonly SearchFilter[]
  onRemove: (filter: SearchFilter) => void
  language: LanguageCode
}

/**
 * Die gesetzten Filter der Suche als Chips über der Eingabe, wie in der Vorlage: Hooks als Hook-Chip, Felder eines
 * Kerns als Pille in der Farbe dieses Kerns, jeder einzeln entfernbar. Gesetzt wird ein Filter nie über eine
 * Feldauswahl, sondern über die Angebote der Vervollständigung, „fsc.“ für die Dateiart, einen Hook-Wert im Treffer
 * oder auf der Dokumentseite. Chips sind (Feld, Wert) ohne Kern: Sie gehen an jeden Kern, wer das Feld nicht kennt,
 * überliest sie; die Farbe sagt nur, aus welchem Kern das Feld stammt.
 */
export function SearchFilterChips({ filters, onRemove, language }: SearchFilterChipsProps) {
  const { t } = useTranslation()
  const { solrHooks, fileSubClasses, solrCores, solrFields } = useMasterData()

  if (filters.length === 0) {
    return null
  }

  /** Die Beschriftung eines Chips: der mitgegebene Zusatz, bei einer Dateiart ihr Name aus den Stammdaten, sonst der Wert */
  function shownValue(filter: SearchFilter): string {
    let label = filter.label
    if (label === undefined && filter.fieldName === FILE_SUB_CLASS_FIELD_NAME) {
      const fileSubClass = fileSubClasses?.find((candidate) => candidate.key === filter.value)
      label = fileSubClass ? fileSubClassNameOf(fileSubClass, language) : undefined
    }
    return label ? `${label} (${filter.value})` : filter.value
  }

  return (
    <div className="search-chips">
      {filters.map((filter) => {
        const hook = solrHooks?.find((candidate) => candidate.key === filter.fieldName)
        const key = `${filter.fieldName}=${filter.value}`
        const shown = shownValue(filter)
        if (hook) {
          return (
            <SolrHookChip
              key={key}
              solrHook={hook}
              value={shown}
              language={language}
              onDelete={() => onRemove(filter)}
              deleteLabel={t('search.filters.remove', {
                field: translate(hook.displayName, language) || hook.key,
                value: filter.value,
              })}
            />
          )
        }
        const solrCore = solrCoreOfFieldName(solrCores, solrFields, filter.fieldName)
        return (
          <span
            key={key}
            className={solrCore ? 'search-filter-chip search-filter-chip-core' : 'search-filter-chip'}
            style={solrCore ? solrCoreColorStyle(solrCore) : undefined}
          >
            <span className="search-filter-chip-field">{filter.fieldName}</span>
            <span>{shown}</span>
            <DeleteButton
              label={t('search.filters.remove', { field: filter.fieldName, value: filter.value })}
              onClick={() => onRemove(filter)}
            />
          </span>
        )
      })}
    </div>
  )
}
