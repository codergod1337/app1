import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import type { OriginState } from '../../components/Origin.ts'
import { Subheader } from '../../components/Subheader.tsx'
import { useUsersSettings } from '../../components/usersSettingsContext.ts'
import { DMS_CORE_KEY, FILE_SUB_CLASS_FIELD_NAME } from '../dms/Dms.ts'
import { fileSubClassesStartingWith, fileSubClassNameOf, type FileSubClass } from '../filesubclass/FileSubClass.ts'
import { useSession } from '../login/sessionContext.ts'
import {
  FILE_SUB_CLASS_MODE_PREFIX,
  hasSearchWord,
  isSameSearchFilter,
  SEARCH_ACTIVE_CORES_SETTING_KEY,
  SEARCH_DEBOUNCE_MS,
  SEARCH_MAX_PAGES,
  SEARCH_MAX_WORDS,
  SEARCH_MIN_CHARACTERS,
  SEARCH_PATH,
  searchRequestOf,
  searchWordsOf,
  useSearchPrefill,
  type SearchCoreFilterOffer,
  type SearchDateRangeFilter,
  type SearchFilter,
  type SearchFilterOffer,
  type SearchResultPage,
} from './Search.ts'
import { searchCoresOf, type SearchCore } from './searchCores.ts'
import { SearchFileSubClassTiles } from './SearchFileSubClassTiles.tsx'
import { SearchFilterEditor } from './SearchFilterEditor.tsx'
import { SearchSuggestions } from './SearchSuggestions.tsx'

/** Beim Verlassen der Suche mitgeben, damit Dokumentseite und Viewer hierher zurückführen. Das label ist ein Schlüssel des Katalogs. */
const ORIGIN_STATE: OriginState = { origin: { path: SEARCH_PATH, label: 'search.title' } }

/**
 * Die Suchmaske nach der SuchePage der Vorlage, ein Modul über alle Kerne: ein Eingabefeld, gesucht wird in allen
 * angewählten Kernen zugleich, zu denen der User die Such-Rolle hat. Die Kernschalter sind keine Seiten, sondern
 * Filter, welche Kerne der User durchsucht, merkt sich seine UsersSetting. Je Kern läuft seine eigene Suche mit eigener
 * Laufnummer, eigenem Fehler und eigener Blätterleiste: Ein langsamer Kern hält die anderen nicht auf. Über der Eingabe
 * die Filter als Chips, darunter die Treffer je Kern in seiner Darstellung.
 *
 * Nie ins Leere suchen: ohne Suchwort ab drei Zeichen und ohne Filter wird nicht einmal gefragt. Höchstens sechs
 * Suchwörter, dieselbe Grenze wie im Backend. „fsc.“ in der Eingabe öffnet die Dateiarten als Kacheln, die Wahl wird
 * der Chip fileSubClassKey. Ein exakt getippter Schlüssel springt direkt zur Seite des Treffers. Vorbelegungen kommen
 * per Router-State (Hook-Werte aus Dokumentseite und Treffern), nie über die Adresse.
 *
 * Beim Tippen läuft je Kern neben der Suche die Vervollständigung: Ihre Filterangebote stehen über der Eingabe, Tab
 * loggt das erste ein, Klick jedes. Ihre kompakten Treffer zeigt die Seite nicht, die Suche darunter läuft ohnehin.
 */
export function Search() {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const navigate = useNavigate()
  const location = useLocation()
  const { hasAccessRole } = useSession()
  const { fileSubClasses } = useMasterData()
  const { settingValue, changeSetting } = useUsersSettings()
  const prefill = useSearchPrefill()

  const cores = searchCoresOf(hasAccessRole)
  const [chosenCoreKeys, setChosenCoreKeys] = useState<string[] | null>(null)
  const [searchText, setSearchText] = useState('')
  const [filters, setFilters] = useState<SearchFilter[]>([])
  const [dateRanges, setDateRanges] = useState<SearchDateRangeFilter[]>([])
  const [resultByCore, setResultByCore] = useState<Record<string, SearchResultPage>>({})
  const [errorByCore, setErrorByCore] = useState<Record<string, string | null>>({})
  const [offersByCore, setOffersByCore] = useState<Record<string, SearchFilterOffer[]>>({})
  const [running, setRunning] = useState(0)
  /** Laufnummern je Kern gegen veraltete Antworten, Suche und Vervollständigung getrennt */
  const runByCore = useRef<Record<string, number>>({})
  const suggestRunByCore = useRef<Record<string, number>>({})
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

  // Das Feld hat den Cursor, sobald man hier landet, auch beim Klick auf die Lupe, wenn man schon hier steht
  useEffect(() => {
    inputRef.current?.focus()
    inputRef.current?.select()
  }, [location.key])

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
  const searchable =
    !fileSubClassMode && !tooManyWords && (hasSearchWord(searchText) || filters.length > 0 || dateRanges.length > 0)
  /** Vervollständigt wird nur mit einem Suchwort: Ohne Eingabe gibt es keinen Anfang, den ein Wert treffen könnte */
  const suggestable = !fileSubClassMode && !tooManyWords && hasSearchWord(searchText)

  /** Die Angebote aller angewählten Kerne in Kernreihenfolge, ohne schon gesetzte Filter, ohne Doppelte */
  const visibleOffers: SearchCoreFilterOffer[] = []
  for (const core of activeCores) {
    for (const offer of offersByCore[core.key] ?? []) {
      const asFilter = { fieldName: offer.fieldName, value: offer.value }
      if (
        !filters.some((existing) => isSameSearchFilter(existing, asFilter)) &&
        !visibleOffers.some((existing) => existing.offer.fieldName === offer.fieldName && existing.offer.value === offer.value)
      ) {
        visibleOffers.push({ coreKey: core.key, offer })
      }
    }
  }

  function addFilter(filter: SearchFilter) {
    setFilters((previous) => (previous.some((existing) => isSameSearchFilter(existing, filter)) ? previous : [...previous, filter]))
  }

  function removeFilter(filter: SearchFilter) {
    setFilters((previous) => previous.filter((existing) => !isSameSearchFilter(existing, filter)))
  }

  function addDateRange(dateRange: SearchDateRangeFilter) {
    setDateRanges((previous) => [...previous.filter((existing) => existing.fieldName !== dateRange.fieldName), dateRange])
  }

  function removeDateRange(fieldName: string) {
    setDateRanges((previous) => previous.filter((existing) => existing.fieldName !== fieldName))
  }

  function pickFileSubClass(fileSubClass: FileSubClass) {
    addFilter({
      fieldName: FILE_SUB_CLASS_FIELD_NAME,
      value: fileSubClass.key,
      label: fileSubClassNameOf(fileSubClass, language),
    })
    setSearchText('')
  }

  /** Ein Angebot einloggen: Der Wert wird Chip, das Suchwort hat seinen Zweck erfüllt und geht aus dem Feld */
  function pickOffer(offer: SearchFilterOffer) {
    addFilter({ fieldName: offer.fieldName, value: offer.value })
    setSearchText('')
  }

  function runSuggest(core: SearchCore, text: string, currentFilters: SearchFilter[], currentRanges: SearchDateRangeFilter[]) {
    const run = (suggestRunByCore.current[core.key] ?? 0) + 1
    suggestRunByCore.current[core.key] = run
    core
      .suggest(searchRequestOf(text, currentFilters, currentRanges, 0))
      .then(({ data }) => {
        if (suggestRunByCore.current[core.key] === run) {
          setOffersByCore((previous) => ({ ...previous, [core.key]: data.filterOffers }))
        }
      })
      .catch((error: unknown) => {
        // Beiwerk: Die Suche läuft weiter, ihren Fehler zeigt sie selbst. In die Konsole gehört er trotzdem.
        console.warn(`Vervollständigung (${core.key}) fehlgeschlagen:`, error instanceof RequestError ? error.message : error)
      })
  }

  function runSearch(core: SearchCore, page: number, text: string, currentFilters: SearchFilter[], currentRanges: SearchDateRangeFilter[]) {
    const run = (runByCore.current[core.key] ?? 0) + 1
    runByCore.current[core.key] = run
    setRunning((count) => count + 1)
    core
      .search(searchRequestOf(text, currentFilters, currentRanges, page))
      .then(({ data }) => {
        if (runByCore.current[core.key] !== run) {
          return
        }
        // Direktsprung: Passt ein Schlüssel exakt auf das Getippte, war es eine id, dann direkt zur Seite
        const typed = text.trim().toLowerCase()
        if (core.directPathOf !== null && typed !== '' && page === 0) {
          const direct = data.hits.find((hit) => core.idOf(hit.document)?.toLowerCase() === typed)
          const path = direct ? core.directPathOf(direct.document) : null
          if (path !== null) {
            navigate(path, { state: ORIGIN_STATE })
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
  const dateRangesKey = JSON.stringify(dateRanges)
  const activeCoresKey = activeCoreKeys.join(',')

  // Eine Abfrage je Tipp-Pause, je angewähltem Kern eine Suche ab Seite 0 und eine Vervollständigung
  useEffect(() => {
    if (!searchable) {
      setResultByCore({})
      setErrorByCore({})
    }
    if (!suggestable) {
      setOffersByCore({})
    }
    if (!searchable && !suggestable) {
      return
    }
    const timer = setTimeout(() => {
      for (const core of activeCores) {
        if (searchable) {
          runSearch(core, 0, searchText, filters, dateRanges)
        }
        if (suggestable) {
          runSuggest(core, searchText, filters, dateRanges)
        }
      }
    }, SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
    // die Kerne stehen über activeCoresKey drin, filters und dateRanges über ihre Schlüssel
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchText, filtersKey, dateRangesKey, activeCoresKey, searchable, suggestable])

  function keyDown(event: KeyboardEvent<HTMLInputElement>) {
    // Tab loggt das erste Angebot ein, wie in der Vorlage; ohne Angebot bleibt Tab, was es ist
    if (event.key === 'Tab' && !event.shiftKey && visibleOffers.length > 0) {
      event.preventDefault()
      pickOffer(visibleOffers[0].offer)
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

  const coreToggles = (
    <span className="search-core-toggles">
      {cores.length > 1 && (
        <button
          type="button"
          className={allActive ? 'search-core-toggle active' : 'search-core-toggle'}
          aria-pressed={allActive}
          onClick={() => chooseCores(allActive ? [] : cores.map((core) => core.key))}
        >
          {t('search.allCores')}
        </button>
      )}
      {cores.map((core) => {
        const active = activeCoreKeys.includes(core.key)
        return (
          <button
            key={core.key}
            type="button"
            className={active ? 'search-core-toggle active' : 'search-core-toggle'}
            aria-pressed={active}
            onClick={() => toggleCore(core.key)}
          >
            {t(`search.cores.${core.labelKey}`)}
          </button>
        )
      })}
    </span>
  )

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
        sticky
      />
      <div className="main-content">
        <ContentBox title={t('search.title')} actions={coreToggles}>
          {cores.length === 0 ? (
            <p className="mb-0">{t('search.noCores')}</p>
          ) : (
            <div className="d-flex flex-column gap-3">
              <p className="form-text mb-0">{t('search.intro')}</p>
              <SearchFilterEditor
                cores={activeCores}
                filters={filters}
                dateRanges={dateRanges}
                onAddFilter={addFilter}
                onRemoveFilter={removeFilter}
                onAddDateRange={addDateRange}
                onRemoveDateRange={removeDateRange}
                language={language}
              />
              <SearchSuggestions offers={visibleOffers} language={language} onPick={pickOffer} />
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
              result={resultByCore[core.key] ?? null}
              errorMessage={errorByCore[core.key] ?? null}
              busy={running > 0}
              onPage={(page) => runSearch(core, page, searchText, filters, dateRanges)}
              onFilter={addFilter}
            />
          ))}
      </div>
    </>
  )
}

interface SearchCoreResultsProps {
  core: SearchCore
  result: SearchResultPage | null
  errorMessage: string | null
  busy: boolean
  onPage: (page: number) => void
  onFilter: (filter: SearchFilter) => void
}

/** Die Treffer eines Kerns: Titel mit Zahl, der Fehler oder Hinweis, die Liste in der Darstellung des Kerns, unten das Blättern */
function SearchCoreResults({ core, result, errorMessage, busy, onPage, onFilter }: SearchCoreResultsProps) {
  const { t } = useTranslation()
  const coreName = t(`search.cores.${core.labelKey}`)
  const total = result?.totalFound ?? 0
  const size = result?.size ?? 1
  const page = result?.page ?? 0
  const pages = Math.min(Math.ceil(total / size), SEARCH_MAX_PAGES)
  const ResultList = core.ResultList

  const footer =
    result !== null && result.hint === null ? (
      <div className="d-flex flex-wrap align-items-center gap-2">
        <span>{t('resolve.total', { count: total })}</span>
        {pages > 1 && (
          <>
            <span>{t('resolve.page', { page: page + 1, pages })}</span>
            <button type="button" className="button-other" disabled={busy || page === 0} onClick={() => onPage(page - 1)}>
              {t('resolve.previous')}
            </button>
            <button
              type="button"
              className="button-other"
              disabled={busy || page + 1 >= pages}
              onClick={() => onPage(page + 1)}
            >
              {t('resolve.next')}
            </button>
          </>
        )}
        {total > pages * size && (
          <span className="small text-muted">{t('search.results.moreThanReachable', { reachable: pages * size })}</span>
        )}
      </div>
    ) : undefined

  let body
  if (errorMessage !== null) {
    body = <p className="text-danger mb-0">{errorMessage}</p>
  } else if (result === null) {
    body = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('search.running')} />
  } else if (result.hint !== null) {
    body = (
      <p className="mb-0 text-muted">
        {t(`search.hint.${result.hint}`, { min: SEARCH_MIN_CHARACTERS, max: SEARCH_MAX_WORDS })}
      </p>
    )
  } else if (result.hits.length === 0) {
    body = <p className="mb-0 text-muted">{t('resolve.empty')}</p>
  } else {
    body = <ResultList hits={result.hits} origin={ORIGIN_STATE} onFilter={onFilter} />
  }

  return (
    <ContentBox title={result !== null && result.hint === null ? `${coreName} · ${t('resolve.total', { count: total })}` : coreName} footer={footer}>
      {body}
    </ContentBox>
  )
}
