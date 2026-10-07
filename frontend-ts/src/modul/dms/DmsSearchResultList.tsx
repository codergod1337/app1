import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { highlightWords } from '../../components/highlight.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { FileSubClassSymbol } from '../filesubclass/FileSubClassSymbol.tsx'
import type { SearchResultListProps } from '../search/searchCores.ts'
import { SolrHookChip } from '../solrmanager/SolrHookChip.tsx'
import {
  DMS_STATE_ACTIVE,
  DOCUMENT_LANGUAGE_FIELD_NAME,
  dmsSolrDocumentPath,
  FILE_SUB_CLASS_FIELD_NAME,
  readableBytes,
  STATUS_FIELD_NAME,
} from './Dms.ts'
import { DmsDocumentLanguageFlag } from './DmsDocumentLanguageFlag.tsx'
import { DmsSolrDocumentActionButtons } from './DmsSolrDocumentActionButtons.tsx'
import { DmsSolrDocumentIdentifiersHint } from './DmsSolrDocumentFacts.tsx'

/** Der Volltext: geht nie zum Client, im Treffergrund steht nur, dass und womit er traf */
const FULL_TEXT_FIELD_NAME = 'searchableWords'

/** Eine Liste, egal ob Solr einen Wert oder mehrere liefert */
function asList(value: unknown): string[] {
  if (value === null || value === undefined) {
    return []
  }
  return (Array.isArray(value) ? value : [value]).map((entry) => String(entry))
}

/**
 * Die Treffer des Kerns DMS in der Suche, nach DmsTreffer der Vorlage: je Treffer das Symbol der Dateiart oder das
 * Vorschaubild, der Dateiname als Weg zur Dokumentseite, die Fakten in einer Zeile, die Beschreibung, die Hooks mit
 * ihren Werten, der Treffergrund und rechts die bekannten Aktionen. Die Wörter des Treffergrunds sind in den gezeigten
 * Werten markiert (User 2026-10-07: jeder Filter, nicht nur die Eingabe); beim Volltext nur das Feld, denn der Text
 * selbst kommt nie mit. Ein Klick auf einen Hook-Wert loggt ihn als Filter ein.
 */
export function DmsSearchResultList({ hits, origin, onFilter }: SearchResultListProps) {
  const { t, i18n } = useTranslation()
  const { language } = useLanguage()
  const navigate = useNavigate()
  const { fileSubClasses, solrHooks, entityStates } = useMasterData()

  /** Die Beschriftung eines Feldes im Treffergrund: Hooks mit Anzeigename, bekannte Felder aus dem Katalog, sonst der Solr-Name */
  function labelOfField(name: string): string {
    const hook = solrHooks?.find((candidate) => candidate.key === name)
    if (hook) {
      return translate(hook.displayName, language) || hook.key
    }
    switch (name) {
      case FULL_TEXT_FIELD_NAME:
        return t('search.matched.fullText')
      case 'fileName':
        return t('dms.document.fileName')
      case 'fileIndex':
        return t('dms.document.fileIndex')
      case 'fileDescription':
        return t('dms.document.description')
      case FILE_SUB_CLASS_FIELD_NAME:
        return t('dms.document.fileSubClass')
      case STATUS_FIELD_NAME:
        return t('dms.document.status')
      case 'version':
        return t('dms.document.version')
      case 'mimeType':
        return t('dms.document.mimeType')
      case DOCUMENT_LANGUAGE_FIELD_NAME:
        return t('dms.document.documentLanguage')
      case 'documentCreatedAt':
        return t('dms.document.createdAt')
      case 'documentUploadedAt':
        return t('dms.document.uploadedAt')
      case 'lastChangedAt':
        return t('dms.document.lastChangedAt')
      default:
        return name
    }
  }

  function day(value: unknown): string | null {
    if (typeof value !== 'string') {
      return null
    }
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString(i18n.language)
  }

  return (
    <div className="search-hits">
      {hits.map((hit) => {
        const document = hit.document
        const matched = hit.matchedWordsByFieldName
        const wordsOf = (name: string): string[] => matched[name] ?? []
        const id = typeof document.id === 'string' ? document.id : ''
        const fileName = typeof document.fileName === 'string' ? document.fileName : id
        const fileSubClassKey =
          typeof document[FILE_SUB_CLASS_FIELD_NAME] === 'string' ? document[FILE_SUB_CLASS_FIELD_NAME] : null
        const fileSubClass = fileSubClasses?.find((candidate) => candidate.key === fileSubClassKey) ?? null
        const fileSubClassName = fileSubClass
          ? translate(fileSubClass.displayName, language) || fileSubClass.key
          : (fileSubClassKey ?? '')
        const status = typeof document[STATUS_FIELD_NAME] === 'string' ? document[STATUS_FIELD_NAME] : null
        const state = entityStates?.find((candidate) => candidate.key === status)
        const statusName = state ? translate(state.displayName, language) || state.key : status
        const description =
          typeof document.fileDescription === 'string' ? translate(document.fileDescription, language) : ''
        const version = document.version
        const fileIndex = typeof document.fileIndex === 'string' ? document.fileIndex : null
        const createdDay = day(document.documentCreatedAt)
        const marked = (name: string, value: ReactNode) => (wordsOf(name).length > 0 ? <mark>{value}</mark> : value)

        const facts: { key: string; label: string; value: ReactNode }[] = []
        if (fileSubClassName !== '') {
          facts.push({ key: 'fsc', label: t('dms.document.fileSubClass'), value: marked(FILE_SUB_CLASS_FIELD_NAME, fileSubClassName) })
        }
        if (status !== null && (status !== DMS_STATE_ACTIVE || wordsOf(STATUS_FIELD_NAME).length > 0)) {
          facts.push({ key: 'status', label: t('dms.document.status'), value: marked(STATUS_FIELD_NAME, statusName) })
        }
        if (typeof version === 'number') {
          facts.push({ key: 'version', label: t('dms.document.version'), value: version })
        }
        if (fileIndex !== null && fileIndex !== '') {
          facts.push({ key: 'index', label: t('dms.document.fileIndex'), value: highlightWords(fileIndex, wordsOf('fileIndex')) })
        }
        const size = readableBytes(document.fileSizeBytes)
        if (size !== '') {
          facts.push({ key: 'size', label: t('dms.document.fileSize'), value: size })
        }
        if (createdDay !== null) {
          facts.push({ key: 'created', label: t('dms.document.createdAt'), value: marked('documentCreatedAt', createdDay) })
        }
        if (typeof document[DOCUMENT_LANGUAGE_FIELD_NAME] === 'string') {
          facts.push({
            key: 'language',
            label: t('dms.document.documentLanguage'),
            value: <DmsDocumentLanguageFlag code={document[DOCUMENT_LANGUAGE_FIELD_NAME]} />,
          })
        }

        const hooksWithValues = (solrHooks ?? []).filter((hook) => asList(document[hook.key]).length > 0)
        const reasons = Object.entries(matched)

        return (
          <div key={id} className="search-hit">
            <Hoverlay text={fileSubClassName !== '' ? fileSubClassName : null}>
              <span className="search-hit-symbol">
                {typeof document.bildVorschauBase64Png === 'string' ? (
                  <img
                    className="search-hit-preview"
                    src={`data:image/png;base64,${document.bildVorschauBase64Png}`}
                    alt={fileName}
                  />
                ) : fileSubClass ? (
                  <FileSubClassSymbol fileSubClass={fileSubClass} />
                ) : (
                  <Icon name="dms" />
                )}
              </span>
            </Hoverlay>

            <div className="search-hit-body">
              <div className="search-hit-head">
                <button
                  type="button"
                  className="search-hit-name"
                  onClick={() => navigate(dmsSolrDocumentPath(id), { state: origin })}
                >
                  {highlightWords(fileName, wordsOf('fileName'))}
                </button>
                <DmsSolrDocumentIdentifiersHint document={document} />
              </div>

              {facts.length > 0 && (
                <div className="search-hit-facts">
                  {facts.map((fact) => (
                    <span key={fact.key} className="search-hit-fact">
                      <span className="text-muted">{fact.label}:</span> {fact.value}
                    </span>
                  ))}
                </div>
              )}

              {description !== '' && (
                <div className="search-hit-description text-break">
                  {highlightWords(description, wordsOf('fileDescription'))}
                </div>
              )}

              {hooksWithValues.length > 0 && (
                <div className="search-hit-hooks">
                  {hooksWithValues.map((hook) => (
                    <span key={hook.key} className="solr-hook-values">
                      <SolrHookChip solrHook={hook} language={language} />
                      {asList(document[hook.key]).map((value) => (
                        <button
                          key={value}
                          type="button"
                          className="solr-hook-value-badge solr-hook-value-badge-link search-hit-hook-value"
                          aria-label={t('search.filters.addValue', { field: labelOfField(hook.key), value })}
                          onClick={() => onFilter({ fieldName: hook.key, value })}
                        >
                          {highlightWords(value, wordsOf(hook.key))}
                        </button>
                      ))}
                    </span>
                  ))}
                </div>
              )}

              {reasons.length > 0 && (
                <div className="search-hit-reasons">
                  {reasons.map(([name, words]) => (
                    <span key={name} className="search-hit-reason">
                      {name === FULL_TEXT_FIELD_NAME ? <Icon name="fullText" /> : <Icon name="filter" />} {labelOfField(name)}:{' '}
                      <mark>{words.join(', ')}</mark>
                    </span>
                  ))}
                </div>
              )}
            </div>

            <div className="search-hit-actions">
              <DmsSolrDocumentActionButtons document={document} origin={origin} />
            </div>
          </div>
        )
      })}
    </div>
  )
}
