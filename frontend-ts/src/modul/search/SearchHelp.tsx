import { Fragment, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { SEARCH_MAX_WORDS, SEARCH_MIN_CHARACTERS } from './Search.ts'

/** Die Regeln der Hilfe in ihrer Reihenfolge; Text und Beispiele je Regel stehen im Sprachkatalog unter search.help.rules */
const SEARCH_HELP_RULES = ['words', 'switch', 'plus', 'minus', 'fsc', 'msc'] as const

type SearchHelpRule = (typeof SEARCH_HELP_RULES)[number]

/** Die Zeichen, die in einer Beispiel-Eingabe in der Farbe ihrer Regel stehen */
const INPUT_OPERATORS: readonly { prefix: string; rule: SearchHelpRule }[] = [
  { prefix: 'fsc.', rule: 'fsc' },
  { prefix: 'msc.', rule: 'msc' },
  { prefix: '+', rule: 'plus' },
  { prefix: '-', rule: 'minus' },
]

interface SearchHelpDropped {
  text: string
  /** warum der Treffer nicht dabei ist, in Alltagssprache */
  reason: string | null
}

interface SearchHelpExample {
  /** z. B. „Schalter an“, wenn das Beispiel nur in einer Stellung gilt */
  label: string | null
  /** der Chip, der schon im Feld steht */
  chip: string | null
  input: string
  /** die Kachel, die angeklickt zum Chip wird */
  becomes: string | null
  note: string | null
  /** Treffer, [Wort] ist markiert */
  hits: string[]
  /** was nicht dabei ist, {Wort} ist der Grund und rot markiert */
  dropped: SearchHelpDropped[]
}

/**
 * Die Bedienung der Suche im Hoverlay am Titel (User 2026-10-14): je Regel das Zeichen groß in seiner Farbe, daneben in
 * Alltagssprache, was es tut, und Beispiele mit Eingabe, Treffern samt Markierung und dem, was nicht dabei ist. Für
 * Leute ohne IT-Hintergrund, darum lieber ein Beispiel mehr. Texte und Beispiele kommen aus dem Sprachkatalog.
 */
export function SearchHelp() {
  const { t } = useTranslation()
  const keys = recordsOf(t('search.help.keys', { returnObjects: true }))
  return (
    <span className="hoverlay-explanation search-help">
      <strong>{t('search.help.title')}</strong>
      <span>{t('search.help.general', { min: SEARCH_MIN_CHARACTERS, max: SEARCH_MAX_WORDS })}</span>
      {SEARCH_HELP_RULES.map((rule) => (
        <span key={rule} className="search-help-rule">
          <span className={`search-help-operator search-help-operator-${rule}`}>{operatorOf(rule)}</span>
          <span className="search-help-text">{t(`search.help.rules.${rule}.text`)}</span>
          <span className="search-help-examples">
            {examplesOf(t(`search.help.rules.${rule}.examples`, { returnObjects: true })).map((example, index) => (
              <SearchHelpExampleView key={index} rule={rule} example={example} />
            ))}
          </span>
        </span>
      ))}
      <span className="search-help-keys">
        {keys.map((key) => (
          <span key={textOf(key.key)} className="search-help-key">
            <kbd>{textOf(key.key)}</kbd>
            <span>{textOf(key.text)}</span>
          </span>
        ))}
      </span>
    </span>
  )
}

function SearchHelpExampleView({ rule, example }: { rule: SearchHelpRule; example: SearchHelpExample }) {
  const { t } = useTranslation()
  return (
    <span className="search-help-example">
      {example.label && <span className="search-help-example-label">{example.label}</span>}
      <span className="search-help-example-caption">{t('search.help.youType')}</span>
      <span className="search-help-input">
        {example.chip && <SearchHelpChip rule={rule} name={example.chip} />}
        <span>{inputWords(example.input)}</span>
      </span>
      {example.becomes && (
        <>
          <span className="search-help-example-caption">{t('search.help.becomes')}</span>
          <span>
            <SearchHelpChip rule={rule} name={example.becomes} />
          </span>
        </>
      )}
      {example.hits.length > 0 && (
        <>
          <span className="search-help-example-caption">{t('search.help.youFind')}</span>
          <span className="search-help-hits">
            {example.hits.map((hit) => (
              <span key={hit} className="search-help-hit">
                <span className="search-help-hit-sign">✓</span>
                <span>{markedText(hit)}</span>
              </span>
            ))}
          </span>
        </>
      )}
      {example.dropped.length > 0 && (
        <>
          <span className="search-help-example-caption">{t('search.help.notFound')}</span>
          <span className="search-help-hits">
            {example.dropped.map((dropped) => (
              <span key={dropped.text} className="search-help-hit search-help-hit-dropped">
                <span className="search-help-hit-sign">✗</span>
                <span>
                  <span className="search-help-hit-text">{markedText(dropped.text)}</span>
                  {dropped.reason && <span className="search-help-hit-reason">{dropped.reason}</span>}
                </span>
              </span>
            ))}
          </span>
        </>
      )}
      {example.note && <span className="search-help-note">{example.note}</span>}
    </span>
  )
}

/** Ein Chip wie der echte (SearchSubClassChip), nur angedeutet: Symbol in der Farbe der Regel, Name, x */
function SearchHelpChip({ rule, name }: { rule: SearchHelpRule; name: string }) {
  return (
    <span className={`search-help-chip search-help-chip-${rule}`}>
      <span className="search-help-chip-symbol" />
      {name}
      <span className="search-help-chip-close">×</span>
    </span>
  )
}

function operatorOf(rule: SearchHelpRule): ReactNode {
  switch (rule) {
    case 'words':
      return 'abc'
    case 'switch':
      return <span className="search-help-switch" />
    case 'plus':
      return '+'
    case 'minus':
      return '-'
    case 'fsc':
      return 'fsc.'
    case 'msc':
      return 'msc.'
  }
}

/** Die Beispiel-Eingabe, die Zeichen +, -, fsc. und msc. am Wortanfang in der Farbe ihrer Regel */
function inputWords(input: string): ReactNode[] {
  return input.split(' ').map((word, index) => {
    const operator = INPUT_OPERATORS.find((candidate) => word.startsWith(candidate.prefix))
    return (
      <Fragment key={index}>
        {index > 0 && ' '}
        {operator ? (
          <>
            <span className={`search-help-input-operator search-help-input-operator-${operator.rule}`}>{operator.prefix}</span>
            {word.slice(operator.prefix.length)}
          </>
        ) : (
          word
        )}
      </Fragment>
    )
  })
}

/** [Wort] wird markiert wie in der Trefferbox, {Wort} rot als Grund, warum ein Treffer wegfällt */
function markedText(text: string): ReactNode[] {
  const pieces: ReactNode[] = []
  let last = 0
  for (const match of text.matchAll(/\[([^\]]*)\]|\{([^}]*)\}/g)) {
    if (match.index > last) {
      pieces.push(<Fragment key={`t${last}`}>{text.slice(last, match.index)}</Fragment>)
    }
    pieces.push(
      match[1] !== undefined ? (
        <mark key={`m${match.index}`}>{match[1]}</mark>
      ) : (
        <mark key={`m${match.index}`} className="search-help-mark-reason">
          {match[2]}
        </mark>
      ),
    )
    last = match.index + match[0].length
  }
  if (last < text.length) {
    pieces.push(<Fragment key={`t${last}`}>{text.slice(last)}</Fragment>)
  }
  return pieces
}

function examplesOf(value: unknown): SearchHelpExample[] {
  return recordsOf(value).map((example) => ({
    label: textOf(example.label) || null,
    chip: textOf(example.chip) || null,
    input: textOf(example.input),
    becomes: textOf(example.becomes) || null,
    note: textOf(example.note) || null,
    hits: Array.isArray(example.hits) ? example.hits.map(textOf) : [],
    dropped: recordsOf(example.dropped).map((dropped) => ({
      text: textOf(dropped.text),
      reason: textOf(dropped.reason) || null,
    })),
  }))
}

function recordsOf(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value)
    ? value.filter((entry): entry is Record<string, unknown> => typeof entry === 'object' && entry !== null)
    : []
}

function textOf(value: unknown): string {
  return typeof value === 'string' ? value : ''
}
