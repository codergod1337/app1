import type { LanguageCode } from '../../branding/languages.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import type { OriginState } from '../../components/Origin.ts'
import { FILE_SUB_CLASS_FIELD_NAME } from '../dms/Dms.ts'
import type { PickableSubClass } from '../filesubclass/FileSubClassPicker.tsx'
import type { SearchFilter } from './Search.ts'
import { spezialFilterErsterOrdnungOfField } from './searchCores.ts'
import { SearchFilterBadge } from './SearchFilterBadge.tsx'
import { SearchSubClassChip } from './SearchSubClassChip.tsx'

interface SearchFilterChipsProps {
  filters: readonly SearchFilter[]
  onRemove: (filter: SearchFilter) => void
  /** die Herkunft für eine Mappe als Ziel eines Werts */
  origin: OriginState
  language: LanguageCode
}

/**
 * Die gesetzten Filter der Suche über der Eingabe, jeder als schmale SearchFilterBadge in der Farbe seines Kerns,
 * einzeln entfernbar (User 2026-10-13, nach der Vorlage). Der Zusatz kommt vom Angebot mit (label). Ein
 * spezialFilterErsterOrdnung (Dateiart über „fsc.“, Nachrichtenart über „msc.“) bekommt seinen eigenen Chip mit Symbol
 * und Namen aus den Stammdaten (User 2026-10-14). Gesetzt wird ein Filter nie über eine Feldauswahl, sondern über die
 * Angebote, „fsc.“, „msc.“, einen Hook-Wert im Treffer oder auf der Dokumentseite.
 */
export function SearchFilterChips({ filters, onRemove, origin, language }: SearchFilterChipsProps) {
  const { fileSubClasses, messageSubClasses } = useMasterData()

  if (filters.length === 0) {
    return null
  }

  /** Die Art eines spezialFilterErsterOrdnung aus den Stammdaten; fehlt sie dort, nur ihr key */
  function subClassOf(filter: SearchFilter): PickableSubClass {
    const catalog: readonly PickableSubClass[] = (filter.fieldName === FILE_SUB_CLASS_FIELD_NAME ? fileSubClasses : messageSubClasses) ?? []
    return (
      catalog.find((candidate) => candidate.key === filter.value) ?? {
        key: filter.value,
        displayName: '',
        listingPosition: 0,
        symbol: null,
        color: null,
      }
    )
  }

  return (
    <div className="search-chips">
      {filters.map((filter) =>
        spezialFilterErsterOrdnungOfField(filter.fieldName) !== null ? (
          <SearchSubClassChip
            key={`${filter.fieldName}=${filter.value}`}
            subClass={subClassOf(filter)}
            language={language}
            onRemove={() => onRemove(filter)}
          />
        ) : (
          <SearchFilterBadge
            key={`${filter.fieldName}=${filter.value}`}
            fieldName={filter.fieldName}
            value={filter.value}
            additionText={filter.label ?? null}
            previewBase64Png={filter.previewBase64Png ?? null}
            variant="set"
            onRemove={() => onRemove(filter)}
            origin={origin}
            language={language}
          />
        ),
      )}
    </div>
  )
}
