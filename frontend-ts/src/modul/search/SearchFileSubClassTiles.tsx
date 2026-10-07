import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { highlight } from '../../components/highlight.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { fileSubClassesStartingWith, fileSubClassNameOf, type FileSubClass } from '../filesubclass/FileSubClass.ts'
import { FileSubClassSymbol } from '../filesubclass/FileSubClassSymbol.tsx'

interface SearchFileSubClassTilesProps {
  /** die Dateiarten, die zur Wahl stehen: die der User lesen darf */
  fileSubClasses: readonly FileSubClass[]
  /** was hinter „fsc.“ steht, klein; leer passt alles */
  needle: string
  language: LanguageCode
  onPick: (fileSubClass: FileSubClass) => void
}

/**
 * Der Dateiart-Modus der Suche nach der Vorlage: Beginnt die Eingabe mit „fsc.“, erscheinen statt der Treffer die
 * Dateiarten als Kacheln wie in der Dateiart-Auswahl. Was dahinter getippt wird, grenzt ein. Klick setzt den Chip
 * fileSubClassKey = key, Enter im Suchfeld nimmt die erste Kachel. Komplett im Frontend: Der Katalog liegt seit dem
 * Login vor, ans Backend geht nur der Chip, ein ganz normaler Filter.
 */
export function SearchFileSubClassTiles({ fileSubClasses, needle, language, onPick }: SearchFileSubClassTilesProps) {
  const { t } = useTranslation()
  const matches = fileSubClassesStartingWith(fileSubClasses, needle, language)

  return (
    <div className="search-fsc">
      <span className="small text-muted">{t('search.fsc.hint')}</span>
      {matches.length === 0 && <span className="small text-muted">{t('common.noMatches')}</span>}
      <div className="file-sub-class-picker-tiles">
        {matches.map((fileSubClass) => (
          <Hoverlay key={fileSubClass.key} text={fileSubClass.key}>
            <button type="button" className="file-sub-class-picker-tile" onClick={() => onPick(fileSubClass)}>
              <FileSubClassSymbol fileSubClass={fileSubClass} />
              <span className="file-sub-class-picker-name">
                <span className="file-sub-class-picker-position">{fileSubClass.listingPosition}</span>
                {highlight(fileSubClassNameOf(fileSubClass, language), needle)}
              </span>
            </button>
          </Hoverlay>
        ))}
      </div>
    </div>
  )
}
