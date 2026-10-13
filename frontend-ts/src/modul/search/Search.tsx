import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import type { OriginState } from '../../components/Origin.ts'
import { Subheader, type SubheaderToggle } from '../../components/Subheader.tsx'
import { useUsersSettings } from '../../components/usersSettingsContext.ts'
import { DMS_CORE_KEY, FILE_SUB_CLASS_FIELD_NAME } from '../dms/Dms.ts'
import { fileSubClassesStartingWith, fileSubClassNameOf, type FileSubClass } from '../filesubclass/FileSubClass.ts'
import { useSession } from '../login/sessionContext.ts'
import {
  solrCoreColorStyle,
  solrCoreMainColor,
  solrCoreOfKey,
  solrCoreTextColor,
  type SolrCore,
} from '../solrmanager/SolrCore.ts'
import { SolrCoreEmblem } from '../solrmanager/SolrCoreEmblem.tsx'
import {
  FILE_SUB_CLASS_MODE_PREFIX,
  hasSearchWord,
  isSameSearchFilter,
  SEARCH_ACTIVE_CORES_SETTING_KEY,
  SEARCH_DEBOUNCE_MS,
  SEARCH_MAX_WORDS,
  SEARCH_MIN_CHARACTERS,
  SEARCH_PATH,
  SEARCH_TEXT_PARAM,
  searchFiltersOfParams,
  searchOfferOfReason,
  searchParamsOf,
  searchRequestOf,
  searchWordsOf,
  useSearchPrefill,
  withoutSearchWord,
  type SearchFilter,
  type SearchOffer,
  type SearchResultPage,
} from './Search.ts'
import { SEARCH_CORES, searchCoresOf, type SearchCore } from './searchCores.ts'
import { SearchFileSubClassTiles } from './SearchFileSubClassTiles.tsx'
import { SearchFilterChips } from './SearchFilterChips.tsx'
import { cachedSearchResult, rememberSearchResult } from './searchResultCache.ts'
import { SearchSuggestions } from './SearchSuggestions.tsx'

/**
 * Die Suchmaske nach der SuchePage der Vorlage, ein Modul über alle Kerne: ein Eingabefeld, gesucht wird in allen
 * angewählten Kernen zugleich, zu denen der User die Such-Rolle hat. Die Kernschalter sind keine Seiten, sondern
 * Filter, welche Kerne der User durchsucht, merkt sich seine UsersSetting. Je Kern läuft seine eigene Suche mit eigener
 * Laufnummer und eigenem Fehler: Ein langsamer Kern hält die anderen nicht auf. Über der Eingabe die Filter als Chips,
 * darunter die Treffer je Kern in seiner Darstellung. Keine Feldauswahl: Filter kommen aus „fsc.“, aus Hook-Werten in
 * Treffern und auf der Dokumentseite.
 *
 * Nie ins Leere suchen: ohne Suchwort ab drei Zeichen und ohne Filter wird nicht einmal gefragt. Höchstens sechs
 * Suchwörter, eine Regel der Maske. „fsc.“ in der Eingabe öffnet die Dateiarten als Kacheln, die Wahl wird der Chip
 * fileSubClassKey. Ein exakt getippter Schlüssel springt direkt zur Seite des Treffers. Vorbelegungen kommen per
 * Router-State (Hook-Werte aus Dokumentseite und Treffern), nie über die Adresse.
 *
 * Das Backend liefert nur die besten Treffer (app1.solr.search.hits), keine Seiten: Gibt es mehr, sagt der Footer,
 * dass man enger suchen soll. Der Treffergrund kommt immer mit, es gibt keinen Schalter dafür (User 2026-10-07).
 *
 * Die Angebote über der Eingabe (User 2026-10-09) kommen aus den Gründen der Treffer, ohne eigenen Request: Jeder
 * Hook-Wert, den ein Suchwort getroffen hat, steht als Chip da. Klick oder Tab macht ihn zum Filter und nimmt das
 * Suchwort aus dem Text: ein Wort weniger in der qf, ein exakter fq dazu.
 *
 * Suchwörter und Chips stehen in der Adresse (User 2026-10-07: ?q=… und f=feld:wert), die Seite schreibt sie bei jeder
 * Änderung hinein, ohne neuen Verlaufseintrag. Zurück aus einem Treffer, der Knopf „zurück zu Suche“ und ein Reload
 * stellen damit den Stand wieder her. Kommt eine Adresse von außen (Lupe, Zurück im Browser, Link), gilt sie.
 */
export function Search() {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const navigate = useNavigate()
  const location = useLocation()
  const { hasAccessRole, sessionInfo } = useSession()
  const usersGuid = sessionInfo?.usersGuid ?? null
  const { fileSubClasses, solrCores, solrHooks } = useMasterData()
  const { settingValue, changeSetting } = useUsersSettings()
  const prefill = useSearchPrefill()
  const [searchParams, setSearchParams] = useSearchParams()

  const cores = searchCoresOf(hasAccessRole)
  const [chosenCoreKeys, setChosenCoreKeys] = useState<string[] | null>(null)
  // der Stand aus der Adresse, z. B. nach Zurück aus einem Treffer oder einem Reload
  const [searchText, setSearchText] = useState(() => searchParams.get(SEARCH_TEXT_PARAM) ?? '')
  const [filters, setFilters] = useState<SearchFilter[]>(() => searchFiltersOfParams(searchParams))
  /** die Adresse, die diese Seite zuletzt selbst geschrieben hat: nur eine andere kommt von außen */
  const writtenSearchRef = useRef<string | null>(null)
  const [resultByCore, setResultByCore] = useState<Record<string, SearchResultPage>>({})
  const [errorByCore, setErrorByCore] = useState<Record<string, string | null>>({})
  const [running, setRunning] = useState(0)
  /** Laufnummern je Kern gegen veraltete Antworten */
  const runByCore = useRef<Record<string, number>>({})
  const inputRef = useRef<HTMLInputElement>(null)

  // Die angewählten Kerne: die Wahl des Users in dieser Sitzung, sonst seine gespeicherte, gefiltert nach dem, was
  // er darf, sonst alle. Verliert jemand eine Such-Rolle, gibt ein alter Eintrag ihm den Kern nicht zurück.
  const storedCoreKeys = (settingValue(SEARCH_ACTIVE_CORES_SETTING_KEY) ?? '')
    .split(',')
    .map((entry) => entry.trim())
    .filter((entry) => cores.some((core) => core.key === entry))
  const activeCoreKeys = chosenCoreKeys ?? (storedCoreKeys.length > 0 ? storedCoreKeys : cores.map((core) => core.key))
  const activeCores = cores.filter((core) => activeCoreKeys.includes(core.key))
  const allActive = cores.length > 0 && cores.every((core) => activeCoreKeys.includes(core.key))

  function chooseCores(keys: string[]) {
    setChosenCoreKeys(keys)
    changeSetting(SEARCH_ACTIVE_CORES_SETTING_KEY, keys.join(',')).catch(() => {
      // nur eine Bequemlichkeit, die Suche läuft auch ohne gespeicherte Wahl
    })
  }

  function toggleCore(key: string) {
    chooseCores(activeCoreKeys.includes(key) ? activeCoreKeys.filter((entry) => entry !== key) : [...activeCoreKeys, key])
  }

  // Eine Adresse von außen (Lupe, Zurück im Browser, Link, Reload): ihr Stand gilt, das Feld bekommt den Cursor mit
  // markiertem Text. Die selbst geschriebene Adresse löst nichts aus, sonst stünde nach jeder Taste alles markiert.
  useEffect(() => {
    if (writtenSearchRef.current === location.search) {
      return
    }
    writtenSearchRef.current = location.search
    const params = new URLSearchParams(location.search)
    setSearchText(params.get(SEARCH_TEXT_PARAM) ?? '')
    setFilters(searchFiltersOfParams(params))
    inputRef.current?.focus()
    inputRef.current?.select()
  }, [location.search])

  // Die Vorbelegung von anderswo, einmal je Ankunft: Filter kommen dazu, ohne Doppelte, der Text ersetzt
  useEffect(() => {
    if (prefill === null) {
      return
    }
    if (prefill.searchText !== undefined) {
      setSearchText(prefill.searchText)
    }
    if (prefill.filters !== undefined && prefill.filters.length > 0) {
      const incoming = prefill.filters
      setFilters((previous) => [
        ...previous,
        ...incoming.filter((filter) => !previous.some((existing) => isSameSearchFilter(existing, filter))),
      ])
    }
    // nur beim Ankommen, der State ändert sich danach nicht mehr
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.key])

  const words = searchWordsOf(searchText)
  const tooManyWords = words.length > SEARCH_MAX_WORDS
  const fileSubClassMode =
    activeCoreKeys.includes(DMS_CORE_KEY) && searchText.toLowerCase().startsWith(FILE_SUB_CLASS_MODE_PREFIX)
  const fileSubClassNeedle = fileSubClassMode ? searchText.slice(FILE_SUB_CLASS_MODE_PREFIX.length).trim().toLowerCase() : ''
  /** die Dateiarten, die der User lesen darf: nur die stehen als Filter zur Wahl */
  const readableFileSubClasses = (fileSubClasses ?? []).filter((fileSubClass) =>
    (fileSubClass.readAccessRoleKeys ?? []).some(hasAccessRole),
  )
  const searchable = !fileSubClassMode && !tooManyWords && (hasSearchWord(searchText) || filters.length > 0)

  /** Die Angebote aus den Gründen der Treffer aller angewählten Kerne, ohne schon gesetzte Filter, ohne Doppelte */
  const isHook = (fieldName: string) => (solrHooks ?? []).some((hook) => hook.key === fieldName)
  const offers: SearchOffer[] = []
  for (const core of activeCores) {
    for (const hit of resultByCore[core.key]?.hits ?? []) {
      for (const reason of hit.reasons) {
        const offer = searchOfferOfReason(core.key, reason, isHook)
        if (
          offer !== null &&
          !filters.some((existing) => isSameSearchFilter(existing, offer)) &&
          !offers.some((existing) => existing.fieldName === offer.fieldName && existing.value === offer.value)
        ) {
          offers.push(offer)
        }
      }
    }
  }

  function addFilter(filter: SearchFilter) {
    setFilters((previous) => (previous.some((existing) => isSameSearchFilter(existing, filter)) ? previous : [...previous, filter]))
  }

  function removeFilter(filter: SearchFilter) {
    setFilters((previous) => previous.filter((existing) => !isSameSearchFilter(existing, filter)))
  }

  function pickFileSubClass(fileSubClass: FileSubClass) {
    addFilter({
      fieldName: FILE_SUB_CLASS_FIELD_NAME,
      value: fileSubClass.key,
      label: fileSubClassNameOf(fileSubClass, language),
    })
    setSearchText('')
  }

  /** Ein Angebot einloggen: der Wert wird Chip (fq), das Suchwort, das ihn traf, geht aus dem Suchtext, samt + davor */
  function pickOffer(offer: SearchOffer) {
    addFilter({ fieldName: offer.fieldName, value: offer.value })
    setSearchText((previous) => withoutSearchWord(previous, offer.word))
  }

  function runSearch(core: SearchCore, text: string, currentFilters: SearchFilter[]) {
    const run = (runByCore.current[core.key] ?? 0) + 1
    runByCore.current[core.key] = run
    const request = searchRequestOf(text, currentFilters)
    // schon gesehen: sofort aus dem Zwischenspeicher, kein Request, kein Direktsprung (der führte zurück und wieder hin)
    const cached = cachedSearchResult(usersGuid, core.key, request)
    if (cached !== undefined) {
      setResultByCore((previous) => ({ ...previous, [core.key]: cached }))
      setErrorByCore((previous) => ({ ...previous, [core.key]: null }))
      return
    }
    setRunning((count) => count + 1)
    core
      .search(request)
      .then(({ data }) => {
        rememberSearchResult(usersGuid, core.key, request, data)
        if (runByCore.current[core.key] !== run) {
          return
        }
        // Direktsprung: Passt ein Schlüssel exakt auf das Getippte, war es eine id, dann direkt zur Seite
        const typed = text.trim().toLowerCase()
        if (core.directPathOf !== null && typed !== '') {
          const direct = data.hits.find((hit) => core.idOf(hit.fieldValues)?.toLowerCase() === typed)
          const path = direct ? core.directPathOf(direct.fieldValues) : null
          if (path !== null) {
            navigate(path, { state: { origin: { path: `${SEARCH_PATH}${location.search}`, label: 'search.title' } } })
            return
          }
        }
        setResultByCore((previous) => ({ ...previous, [core.key]: data }))
        setErrorByCore((previous) => ({ ...previous, [core.key]: null }))
      })
      .catch((error: unknown) => {
        if (runByCore.current[core.key] !== run) {
          return
        }
        setErrorByCore((previous) => ({
          ...previous,
          [core.key]: error instanceof RequestError ? error.message : t('common.unknownError'),
        }))
      })
      .finally(() => setRunning((count) => count - 1))
  }

  // Stabile Schlüssel statt Array-Referenzen, sonst feuert der Effekt bei jedem Render
  const filtersKey = JSON.stringify(filters)
  const activeCoresKey = activeCoreKeys.join(',')

  // Der Stand in die Adresse, der Eintrag im Verlauf wird ersetzt, nicht vermehrt: Zurück führt aus der Suche heraus,
  // nicht durch jede getippte Taste. Der Router-State fällt dabei weg, die Vorbelegung ist da schon eingearbeitet.
  useEffect(() => {
    const params = searchParamsOf(searchText, filters)
    const nextSearch = params.size === 0 ? '' : `?${params.toString()}`
    if (nextSearch === location.search) {
      writtenSearchRef.current = nextSearch
      return
    }
    writtenSearchRef.current = nextSearch
    setSearchParams(params, { replace: true })
    // filters über ihren Schlüssel; location.search und setSearchParams ändern sich nur durch diesen Effekt selbst
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchText, filtersKey])

  /** Beim Verlassen der Suche mitgeben, mit dem aktuellen Stand in der Adresse, damit „zurück zu Suche“ ihn wiederherstellt */
  const originState: OriginState = { origin: { path: `${SEARCH_PATH}${location.search}`, label: 'search.title' } }

  // Je angewähltem Kern eine Suche. Was der Zwischenspeicher schon hat, steht sofort da, ohne Tipp-Pause (Zurück aus
  // einem Treffer, Backspace auf Gesehenes); der Rest nach 250 ms Tipp-Pause.
  useEffect(() => {
    if (!searchable) {
      setResultByCore({})
      setErrorByCore({})
      return
    }
    const pendingSearch: SearchCore[] = []
    const request = searchRequestOf(searchText, filters)
    for (const core of activeCores) {
      const cached = cachedSearchResult(usersGuid, core.key, request)
      if (cached !== undefined) {
        setResultByCore((previous) => ({ ...previous, [core.key]: cached }))
        setErrorByCore((previous) => ({ ...previous, [core.key]: null }))
      } else {
        pendingSearch.push(core)
      }
    }
    if (pendingSearch.length === 0) {
      return
    }
    const timer = setTimeout(() => {
      for (const core of pendingSearch) {
        runSearch(core, searchText, filters)
      }
    }, SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
    // die Kerne stehen über activeCoresKey drin, filters über ihren Schlüssel
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchText, filtersKey, activeCoresKey, searchable])

  function keyDown(event: KeyboardEvent<HTMLInputElement>) {
    // Tab loggt das erste Angebot ein, wie in der Vorlage; ohne Angebot bleibt Tab, was es ist
    if (event.key === 'Tab' && !event.shiftKey && offers.length > 0) {
      event.preventDefault()
      pickOffer(offers[0])
      return
    }
    if (fileSubClassMode && event.key === 'Enter') {
      event.preventDefault()
      const first = fileSubClassesStartingWith(readableFileSubClasses, fileSubClassNeedle, language)[0]
      if (first !== undefined) {
        pickFileSubClass(first)
      }
      return
    }
    if (event.key === 'Escape' && searchText !== '') {
      event.preventDefault()
      setSearchText('')
    }
  }

  // Die Kernschalter im subheader wie die Kernreiter der Vorlage: keine Seiten, sondern Filter, welche Kerne suchen.
  // „alle Kerne“ leuchtet nur, wenn wirklich keiner fehlt. Ein Kern ohne Such-Rolle ist gesperrt, nicht versteckt:
  // Man soll sehen, was es gäbe. Gesperrte Knöpfe sind eine Bequemlichkeit, keine Sicherung, das Tor ist das Backend.
  const coreToggles: SubheaderToggle[] = [
    {
      key: 'all',
      label: t('search.allCores'),
      active: allActive,
      disabled: cores.length === 0,
      onClick: () => chooseCores(allActive ? [] : cores.map((core) => core.key)),
    },
  ]
  for (const core of SEARCH_CORES) {
    const allowed = cores.some((candidate) => candidate.key === core.key)
    // Name, Symbol und Farbe des Kerns aus den Stammdaten (look des Kerns), wie die Kernfarbe der Vorlage
    const solrCore = solrCoreOfKey(solrCores, core.key)
    coreToggles.push({
      key: core.key,
      label: (solrCore ? translate(solrCore.displayName, language) : '') || t(`search.cores.${core.labelKey}`),
      symbol: solrCore?.look.symbol ?? undefined,
      color: solrCoreMainColor(solrCore),
      textColor: solrCoreTextColor(solrCore),
      active: allowed && activeCoreKeys.includes(core.key),
      disabled: !allowed,
      hint: allowed ? undefined : t('search.missingRole', { role: core.searchAccessRoleKey }),
      onClick: () => toggleCore(core.key),
    })
  }

  let inputHint: string | null = null
  if (tooManyWords) {
    inputHint = t('search.hint.TOO_MANY_SEARCH_WORDS', { max: SEARCH_MAX_WORDS })
  } else if (!fileSubClassMode && !searchable) {
    inputHint = t('search.hint.NO_SEARCH_WORD_AND_NO_FILTER', { min: SEARCH_MIN_CHARACTERS })
  }

  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="search" /> {t('search.title')}
          </>
        }
        toggles={coreToggles}
        sticky
      />
      <div className="main-content">
        <ContentBox title={t('search.title')}>
          {cores.length === 0 ? (
            <p className="mb-0">{t('search.noCores')}</p>
          ) : (
            <div className="d-flex flex-column gap-3">
              <p className="form-text mb-0">{t('search.intro')}</p>
              <SearchFilterChips filters={filters} onRemove={removeFilter} language={language} />
              <SearchSuggestions offers={offers} language={language} onPick={pickOffer} />
              <div className="d-flex align-items-center gap-2">
                <input
                  ref={inputRef}
                  type="search"
                  className="form-control search-input"
                  value={searchText}
                  placeholder={t('search.placeholder')}
                  aria-label={t('search.title')}
                  onChange={(event) => setSearchText(event.target.value)}
                  onKeyDown={keyDown}
                />
                {running > 0 && (
                  <span className="spinner-border spinner-border-sm" role="status" aria-label={t('search.running')} />
                )}
              </div>
              <div className="d-flex flex-wrap align-items-center gap-3">
                <span className={tooManyWords ? 'search-words search-words-exceeded' : 'search-words'}>
                  {t('search.words', { count: words.length, max: SEARCH_MAX_WORDS })}
                </span>
                {inputHint !== null && <span className="small text-muted">{inputHint}</span>}
              </div>
              {fileSubClassMode && (
                <SearchFileSubClassTiles
                  fileSubClasses={readableFileSubClasses}
                  needle={fileSubClassNeedle}
                  language={language}
                  onPick={pickFileSubClass}
                />
              )}
            </div>
          )}
        </ContentBox>

        {searchable &&
          activeCores.map((core) => (
            <SearchCoreResults
              key={core.key}
              core={core}
              solrCore={solrCoreOfKey(solrCores, core.key)}
              result={resultByCore[core.key] ?? null}
              errorMessage={errorByCore[core.key] ?? null}
              origin={originState}
              onFilter={addFilter}
            />
          ))}
      </div>
    </>
  )
}

interface SearchCoreResultsProps {
  core: SearchCore
  /** der Kern aus den Stammdaten, für Emblem und Farbe; null, solange sie laden */
  solrCore: SolrCore | null
  result: SearchResultPage | null
  errorMessage: string | null
  /** woher man kommt, mit dem Stand der Suche in der Adresse */
  origin: OriginState
  onFilter: (filter: SearchFilter) => void
}

/**
 * Die Treffer eines Kerns: Emblem und Zahl im Kopf, der Fehler, die Liste in der Darstellung des Kerns in seiner Farbe
 * (Marken des Treffergrunds, Rand der Treffer), unten die Gesamtzahl und, wenn das Backend mehr hat als es liefert,
 * der Rat, enger zu suchen.
 */
function SearchCoreResults({ core, solrCore, result, errorMessage, origin, onFilter }: SearchCoreResultsProps) {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const coreName = solrCore ? (
    <SolrCoreEmblem solrCore={solrCore} language={language} />
  ) : (
    t(`search.cores.${core.labelKey}`)
  )
  const total = result?.totalFound ?? 0
  const shown = result?.hits.length ?? 0
  const ResultList = core.ResultList

  const footer =
    result !== null ? (
      <div className="d-flex flex-wrap align-items-center gap-2">
        <span>{t('resolve.total', { count: total })}</span>
        {total > shown && <span className="small text-muted">{t('search.results.onlyBest', { count: shown })}</span>}
      </div>
    ) : undefined

  let body
  if (errorMessage !== null) {
    body = <p className="text-danger mb-0">{errorMessage}</p>
  } else if (result === null) {
    body = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('search.running')} />
  } else if (result.hits.length === 0) {
    body = <p className="mb-0 text-muted">{t('resolve.empty')}</p>
  } else {
    body = <ResultList hits={result.hits} origin={origin} onFilter={onFilter} />
  }

  const title = (
    <span className="d-inline-flex align-items-center gap-2">
      {coreName}
      {result !== null && <span className="fw-normal">{t('resolve.total', { count: total })}</span>}
    </span>
  )

  return (
    <ContentBox title={title} footer={footer}>
      <div className="search-core" style={solrCoreColorStyle(solrCore)}>
        {body}
      </div>
    </ContentBox>
  )
}
