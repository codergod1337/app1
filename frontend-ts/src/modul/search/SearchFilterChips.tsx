import type { LanguageCode } from '../../branding/languages.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import type { OriginState } from '../../components/Origin.ts'
import { FILE_SUB_CLASS_FIELD_NAME } from '../dms/Dms.ts'
import { fileSubClassNameOf } from '../filesubclass/FileSubClass.ts'
import type { SearchFilter } from './Search.ts'
import { SearchFilterBadge } from './SearchFilterBadge.tsx'

interface SearchFilterChipsProps {
  filters: readonly SearchFilter[]
  onRemove: (filter: SearchFilter) => void
  /** die Herkunft für eine Mappe als Ziel eines Werts */
  origin: OriginState
  language: LanguageCode
}

/**
 * Die gesetzten Filter der Suche über der Eingabe, jeder als schmale SearchFilterBadge in der Farbe seines Kerns,
 * einzeln entfernbar (User 2026-10-13, nach der Vorlage). Der Zusatz kommt vom Angebot mit (label), bei einer Dateiart
 * ihr Name aus den Stammdaten. Gesetzt wird ein Filter nie über eine Feldauswahl, sondern über die Angebote, „fsc.“
 * für die Dateiart, einen Hook-Wert im Treffer oder auf der Dokumentseite. Chips sind (Feld, Wert) ohne Kern: Sie gehen
 * an jeden Kern, die Farbe sagt nur, wohin das Feld gehört.
 */
export function SearchFilterChips({ filters, onRemove, origin, language }: SearchFilterChipsProps) {
  const { fileSubClasses } = useMasterData()

  if (filters.length === 0) {
    return null
  }

  /** Der Zusatz eines Chips: der mitgegebene, bei einer Dateiart ihr Name aus den Stammdaten */
  function additionTextOf(filter: SearchFilter): string | null {
    if (filter.label !== undefined) {
      return filter.label
    }
    if (filter.fieldName === FILE_SUB_CLASS_FIELD_NAME) {
      const fileSubClass = fileSubClasses?.find((candidate) => candidate.key === filter.value)
      return fileSubClass ? fileSubClassNameOf(fileSubClass, language) : null
    }
    return null
  }

  return (
    <div className="search-chips">
      {filters.map((filter) => (
        <SearchFilterBadge
          key={`${filter.fieldName}=${filter.value}`}
          fieldName={filter.fieldName}
          value={filter.value}
          additionText={additionTextOf(filter)}
          previewBase64Png={filter.previewBase64Png ?? null}
          variant="set"
          onRemove={() => onRemove(filter)}
          origin={origin}
          language={language}
        />
      ))}
    </div>
  )
}
