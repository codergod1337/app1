import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { FileSubClassSymbol } from '../filesubclass/FileSubClassSymbol.tsx'
import { messageSubClassNameOf } from '../messagesubclass/MessageSubClass.ts'
import type { SearchResultListProps } from '../search/searchCores.ts'
import { SearchHitReasons } from '../search/SearchHitReasons.tsx'
import { SolrHookValues } from '../solrmanager/SolrHookValues.tsx'
import { NACHRICHTEN_CORE_KEY, nachrichtenFieldLabel, nachrichtenValueOf, nachrichtenValuesOf, nachrichtPath } from './Nachrichten.ts'

/**
 * Die Treffer des Kerns NACHRICHTEN in der Suche: je Treffer das Symbol der Nachrichtenart, der Betreff als Weg auf die
 * Seite der Nachricht samt Verlauf, Datum, Absender und Empfänger, das Postfach, der Vorschautext, die Hooks mit ihren
 * Werten und der Treffergrund. Ein Hook-Wert loggt sich als Filter ein oder öffnet die Mappe seines Hooks.
 */
export function NachrichtenSearchResultList({ hits, origin, onFilter }: SearchResultListProps) {
  const { t, i18n } = useTranslation()
  const { language } = useLanguage()
  const navigate = useNavigate()
  const { solrHooks, solrFields, messageSubClasses } = useMasterData()

  return (
    <div className="search-hits">
      {hits.map((hit) => {
        const document = hit.fieldValues
        const id = nachrichtenValueOf(document, 'id')
        const subject = nachrichtenValueOf(document, 'subject') || t('archivieren.noSubject')
        const messageSubClass = (messageSubClasses ?? []).find((candidate) => candidate.key === nachrichtenValueOf(document, 'messageSubClassKey')) ?? null
        const sent = nachrichtenValueOf(document, 'sentDateTime')
        const from = nachrichtenValueOf(document, 'fromName') || nachrichtenValueOf(document, 'fromAddress')
        const to = nachrichtenValuesOf(document.toRecipientNames)
        const preview = nachrichtenValueOf(document, 'bodyPreview')
        const hooksWithValues = (solrHooks ?? []).filter((hook) => nachrichtenValuesOf(document[hook.key]).length > 0)

        return (
          <div key={id} className="search-hit">
            <span className="search-hit-symbol">
              {messageSubClass ? (
                <Hoverlay text={messageSubClassNameOf(messageSubClass, language)}>
                  <FileSubClassSymbol fileSubClass={messageSubClass} />
                </Hoverlay>
              ) : (
                <Icon name="inbox" />
              )}
            </span>

            <div className="search-hit-body">
              <div className="search-hit-head">
                <button type="button" className="search-hit-name" onClick={() => navigate(nachrichtPath(id), { state: origin })}>
                  {subject}
                </button>
                {nachrichtenValueOf(document, 'hasAttachments') === 'true' && (
                  <Hoverlay text={nachrichtenFieldLabel(solrFields, solrHooks, 'hasAttachments', language)}>
                    <Icon name="attachment" />
                  </Hoverlay>
                )}
              </div>

              <div className="search-hit-facts">
                {sent !== '' && <span className="search-hit-fact">{new Date(sent).toLocaleString(i18n.language)}</span>}
                {from !== '' && (
                  <span className="search-hit-fact">
                    <span className="text-muted">{t('archivieren.mail.from')}:</span> {from}
                  </span>
                )}
                {to.length > 0 && (
                  <span className="search-hit-fact">
                    <span className="text-muted">{t('archivieren.mail.to')}:</span> {to.join(', ')}
                  </span>
                )}
                <span className="search-hit-fact">
                  <span className="text-muted">{t('archivieren.columns.mailbox')}:</span> {nachrichtenValueOf(document, 'mailbox')}
                </span>
              </div>

              {preview !== '' && <div className="text-muted small text-truncate">{preview}</div>}

              {hooksWithValues.length > 0 && (
                <div className="search-hit-hooks">
                  {hooksWithValues.map((hook) => (
                    <SolrHookValues
                      key={hook.key}
                      solrHook={hook}
                      values={nachrichtenValuesOf(document[hook.key])}
                      language={language}
                      origin={origin}
                      onSearchFilter={onFilter}
                      badgeClassName="search-hit-hook-value"
                      valuesInHoverlay
                    />
                  ))}
                </div>
              )}

              <SearchHitReasons reasons={hit.reasons} coreKey={NACHRICHTEN_CORE_KEY} language={language} />
            </div>
          </div>
        )
      })}
    </div>
  )
}
