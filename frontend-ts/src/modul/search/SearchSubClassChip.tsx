import type { CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { fileSubClassNameOf } from '../filesubclass/FileSubClass.ts'
import type { PickableSubClass } from '../filesubclass/FileSubClassPicker.tsx'
import { FileSubClassSymbol } from '../filesubclass/FileSubClassSymbol.tsx'

interface SearchSubClassChipProps {
  /** die Dateiart oder Nachrichtenart des Chips */
  subClass: PickableSubClass
  language: LanguageCode
  onRemove: () => void
}

/**
 * Der Chip eines spezialFilterErsterOrdnung (User 2026-10-14): anders als die Filter-Badge ohne Kern und Feld, nur das
 * Symbol der Art in ihrer Farbe und ihr Anzeigename, der Rahmen in der Farbe der Art. Er legt die Suche auf einen Kern
 * fest, das soll man ihm ansehen. Das Aussehen steht in basic.css (search-subclass-chip).
 */
export function SearchSubClassChip({ subClass, language, onRemove }: SearchSubClassChipProps) {
  const { t } = useTranslation()
  const name = fileSubClassNameOf(subClass, language)
  const style = subClass.color !== null ? ({ '--subclass-color': subClass.color } as CSSProperties) : undefined
  return (
    <span className="search-subclass-chip" style={style}>
      <FileSubClassSymbol fileSubClass={subClass} />
      <span className="search-subclass-chip-name">{name}</span>
      <DeleteButton label={t('search.filters.remove', { field: name, value: subClass.key })} onClick={onRemove} />
    </span>
  )
}
