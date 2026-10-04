import { Hoverlay } from '../../components/Hoverlay.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { documentLanguageFlagOf, documentLanguageName } from './DmsDocumentLanguage.ts'

/**
 * Die Sprache eines Dokuments als Flagge, der Name im Hoverlay. Ohne Sprache ein Strich. Ein Code außerhalb der Liste
 * (alter Datenbestand) steht als Code.
 */
export function DmsDocumentLanguageFlag({ code }: { code: unknown }) {
  const { language } = useLanguage()
  if (typeof code !== 'string' || code === '') {
    return <span className="text-muted">—</span>
  }
  const flag = documentLanguageFlagOf(code)
  const name = documentLanguageName(code, language)
  return (
    <Hoverlay text={`${name} (${code})`}>
      <span className="document-language-flag" aria-label={name}>
        {flag !== null ? <span className={`fi fi-${flag}`} /> : <code>{code}</code>}
      </span>
    </Hoverlay>
  )
}
