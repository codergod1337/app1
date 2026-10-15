import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { highlight } from '../../components/highlight.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { fileSubClassesStartingWith, fileSubClassNameOf } from '../filesubclass/FileSubClass.ts'
import type { PickableSubClass } from '../filesubclass/FileSubClassPicker.tsx'
import { FileSubClassSymbol } from '../filesubclass/FileSubClassSymbol.tsx'

interface SearchFileSubClassTilesProps<T extends PickableSubClass> {
  /** die Arten, die zur Wahl stehen: Dateiarten oder Nachrichtenarten, die der User lesen darf */
  subClasses: readonly T[]
  /** was hinter „fsc.“ oder „msc.“ steht, klein; leer passt alles */
  needle: string
  /** die Zeile über den Kacheln, je spezialFilterErsterOrdnung ihr Text */
  hint: string
  language: LanguageCode
  onPick: (subClass: T) => void
}

/**
 * Die Kacheln eines spezialFilterErsterOrdnung, nach dem Dateiart-Modus der Vorlage: Beginnt die Eingabe mit „fsc.“
 * oder „msc.“, erscheinen statt der Treffer die Dateiarten oder Nachrichtenarten als Kacheln wie in der Auswahl. Was
 * dahinter getippt wird, grenzt ein. Klick setzt den Chip, Enter im Suchfeld nimmt die erste Kachel. Komplett im
 * Frontend: Der Katalog liegt seit dem Login vor, ans Backend geht nur der Chip, ein ganz normaler Filter.
 */
export function SearchFileSubClassTiles<T extends PickableSubClass>({ subClasses, needle, hint, language, onPick }: SearchFileSubClassTilesProps<T>) {
  const { t } = useTranslation()
  const matches = fileSubClassesStartingWith(subClasses, needle, language)

  return (
    <div className="search-fsc">
      <span className="small text-muted">{hint}</span>
      {matches.length === 0 && <span className="small text-muted">{t('common.noMatches')}</span>}
      <div className="file-sub-class-picker-tiles">
        {matches.map((subClass) => (
          <Hoverlay key={subClass.key} text={subClass.key}>
            <button type="button" className="file-sub-class-picker-tile" onClick={() => onPick(subClass)}>
              <FileSubClassSymbol fileSubClass={subClass} />
              <span className="file-sub-class-picker-name">
                <span className="file-sub-class-picker-position">{subClass.listingPosition}</span>
                {highlight(fileSubClassNameOf(subClass, language), needle)}
              </span>
            </button>
          </Hoverlay>
        ))}
      </div>
    </div>
  )
}
