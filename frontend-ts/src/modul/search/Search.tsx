import { memo, useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import type { OriginState } from '../../components/Origin.ts'
import { Subheader, type SubheaderToggle } from '../../components/Subheader.tsx'
import { showToast } from '../../components/toastStore.ts'
import { ToggleSwitch } from '../../components/ToggleSwitch.tsx'
import { useUsersSettings } from '../../components/usersSettingsContext.ts'
import { FILE_SUB_CLASS_FIELD_NAME } from '../dms/Dms.ts'
import { fileSubClassesStartingWith, fileSubClassNameOf } from '../filesubclass/FileSubClass.ts'
import type { PickableSubClass } from '../filesubclass/FileSubClassPicker.tsx'
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
  hasSearchWord,
  isSameSearchFilter,
  SEARCH_ACTIVE_CORES_SETTING_KEY,
  SEARCH_ALL_WORDS_SETTING_KEY,
  SEARCH_DEBOUNCE_MS,
  SEARCH_MAX_OFFERS_PER_CORE,
  SEARCH_MAX_TEXT_COMPLETIONS,
  SEARCH_MAX_WORDS,
  SEARCH_MIN_CHARACTERS,
  SEARCH_PATH,
  SEARCH_TEXT_PARAM,
  searchFiltersOfParams,
  searchParamsOf,
  searchRequestOf,
  searchWordsForOffersOf,
  searchWordsOf,
  typedWordOf,
  useSearchPrefill,
  withoutSearchWord,
  withTypedWordCompleted,
  type SearchFilter,
  type SearchOffer,
  type SearchResultPage,
  type SearchTextCompletion,
} from './Search.ts'
import {
  SEARCH_CORES,
  SEARCH_RESOLVER_CORE_KEY,
  searchCoresOf,
  SPEZIAL_FILTER_ERSTER_ORDNUNG,
  spezialFilterErsterOrdnungOfField,
  type SearchCore,
  type SpezialFilterErsterOrdnung,
} from './searchCores.ts'
import { SearchFileSubClassTiles } from './SearchFileSubClassTiles.tsx'
import { SearchFilterChips } from './SearchFilterChips.tsx'
import { SearchHelp } from './SearchHelp.tsx'
import { cachedSearchResult, rememberSearchResult } from './searchResultCache.ts'
import { SearchSuggestions } from './SearchSuggestions.tsx'

/** Die id der Liste der Vervollständigungen unter dem Suchfeld, für aria-controls und aria-activedescendant */
const SEARCH_COMPLETION_LIST_ID = 'search-completion-list'

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
 * fileSubClassKey, „msc.“ ebenso die Nachrichtenarten mit messageSubClassKey: die spezialFilterErsterOrdnung (User
 * 2026-10-14), immer nur einer, und solange er steht, sucht nur sein Kern. Ein exakt getippter Schlüssel springt nicht mehr, er steht als deutlicher Hinweis da, Enter öffnet
 * seine Seite (User 2026-10-14: längere Artikelnummern haben oft eine gültige davor). Vorbelegungen kommen per
 * Router-State (Hook-Werte aus Dokumentseite und Treffern), nie über die Adresse.
 *
 * Das Backend liefert eine Seite der Treffer (app1.solr.search.hits, 100). Gibt es mehr, hängt „weitere laden“ im Fuß
 * der Box die nächste Seite unten an (User 2026-10-14); der Zwischenspeicher merkt sich die angehängten Seiten. Der
 * Treffergrund kommt immer mit, es gibt keinen Schalter dafür (User 2026-10-07).
 *
 * Die Angebote über der Eingabe (User 2026-10-09, nach der Vorlage erweitert 2026-10-13) kommen aus den Treffern, ohne
 * eigenen Request: Jeder Kern baut sie selbst (offersOf), mit Zusatz und Bild, die Kontakte bieten die Nummern jedes
 * Treffers mit dem Firmennamen. Klick oder Tab macht ein Angebot zum Filter und nimmt das Suchwort aus dem Text: ein
 * Wort weniger in der qf, ein exakter fq dazu. Die Vervollständigungen des gerade getippten Worts, heute die
 * E-Mail-Adressen der Kontakte, stehen wie in jeder Oberfläche als Liste direkt unter dem Suchfeld: Sie ersetzen das
 * Wort im Suchtext und werden nie ein Filter. Ist der Kern KONTAKTE abgewählt, sucht er als Auflöser still mit.
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
  const { fileSubClasses, messageSubClasses, solrCores, solrHooks } = useMasterData()
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
  /** die Kerne, deren nächste Seite gerade lädt */
  const [loadingMoreByCore, setLoadingMoreByCore] = useState<Record<string, boolean>>({})
  /** Suchtext und Chips der letzten Eingabe: Eine nächste Seite, die erst danach ankommt, wird verworfen */
  const latestQueryRef = useRef('')
  const inputRef = useRef<HTMLInputElement>(null)

  // Die angewählten Kerne: die Wahl des Users in dieser Sitzung, sonst seine gespeicherte, gefiltert nach dem, was
  // er darf, sonst alle. Verliert jemand eine Such-Rolle, gibt ein alter Eintrag ihm den Kern nicht zurück.
  const storedCoreKeys = (settingValue(SEARCH_ACTIVE_CORES_SETTING_KEY) ?? '')
    .split(',')
    .map((entry) => entry.trim())
    .filter((entry) => cores.some((core) => core.key === entry))
  // Ein gesetzter spezialFilterErsterOrdnung legt die Suche auf seinen Kern fest (User 2026-10-14): Nur dieser Kern ist
  // aktiv, die anderen Schalter sind gesperrt, bis der Chip weg ist. Die Wahl des Users bleibt dabei unberührt und gilt
  // wieder, sobald der Chip entfernt ist.
  const lockingSpezialFilter =
    filters.map((filter) => spezialFilterErsterOrdnungOfField(filter.fieldName)).find((entry) => entry !== null) ?? null
  const activeCoreKeys =
    lockingSpezialFilter !== null
      ? [lockingSpezialFilter.coreKey]
      : (chosenCoreKeys ?? (storedCoreKeys.length > 0 ? storedCoreKeys : cores.map((core) => core.key)))
  const activeCores = cores.filter((core) => activeCoreKeys.includes(core.key))
  const allActive = cores.length > 0 && cores.every((core) => activeCoreKeys.includes(core.key))

  function chooseCores(keys: string[]) {
    setChosenCoreKeys(keys)
    changeSetting(SEARCH_ACTIVE_CORES_SETTING_KEY, keys.join(',')).catch(() => {
      // nur eine Bequemlichkeit, die Suche läuft auch ohne gespeicherte Wahl
    })
  }

  // Der Schalter über dem Suchfeld (User 2026-10-14): an heißt jedes Wort muss vorhanden sein, aus heißt Hauptsache
  // ein Treffer. Die Wahl in dieser Sitzung, sonst die gespeicherte, ohne beides an.
  const [chosenAllWordsRequired, setChosenAllWordsRequired] = useState<boolean | null>(null)
  const allWordsRequired = chosenAllWordsRequired ?? settingValue(SEARCH_ALL_WORDS_SETTING_KEY) !== 'false'

  function chooseAllWordsRequired(checked: boolean) {
    setChosenAllWordsRequired(checked)
    changeSetting(SEARCH_ALL_WORDS_SETTING_KEY, String(checked)).catch(() => {
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
  /** die volle Zahl erreicht oder darüber: der Zähler wird rot und rät zu einem Filter */
  const wordsAtLimit = words.length >= SEARCH_MAX_WORDS
  // „fsc.“ oder „msc.“ am Anfang öffnet die Kacheln eines spezialFilterErsterOrdnung, nur mit der Such-Rolle seines Kerns
  const lowerSearchText = searchText.toLowerCase()
  const spezialFilterMode =
    SPEZIAL_FILTER_ERSTER_ORDNUNG.find(
      (entry) => lowerSearchText.startsWith(entry.prefix) && cores.some((core) => core.key === entry.coreKey),
    ) ?? null
  const spezialFilterNeedle = spezialFilterMode !== null ? searchText.slice(spezialFilterMode.prefix.length).trim().toLowerCase() : ''
  /** die Dateiarten oder Nachrichtenarten des offenen Modus, die der User lesen darf: nur die stehen als Filter zur Wahl */
  const subClassCatalog: readonly (PickableSubClass & { readAccessRoleKeys: string[] | null })[] =
    (spezialFilterMode?.fieldName === FILE_SUB_CLASS_FIELD_NAME ? fileSubClasses : messageSubClasses) ?? []
  const pickableSubClasses =
    spezialFilterMode === null ? [] : subClassCatalog.filter((subClass) => (subClass.readAccessRoleKeys ?? []).some(hasAccessRole))
  const searchable = spezialFilterMode === null && !tooManyWords && (hasSearchWord(searchText) || filters.length > 0)

  // Der Auflöser (User 2026-10-13, nach der Vorlage): Ist der Kern KONTAKTE abgewählt, sucht er mit Suchwort trotzdem
  // still mit, ohne Chips, nur für Angebote und Vervollständigungen. Nur mit seiner Such-Rolle, sonst gibt es ihn nicht.
  const resolverCore =
    !activeCoreKeys.includes(SEARCH_RESOLVER_CORE_KEY) && spezialFilterMode === null && !tooManyWords && hasSearchWord(searchText)
      ? (cores.find((core) => core.key === SEARCH_RESOLVER_CORE_KEY) ?? null)
      : null
  const suggestingCores = resolverCore !== null ? [...activeCores, resolverCore] : activeCores
  // Stabile Schlüssel statt Array-Referenzen, sonst rechnen useMemo und die Effekte bei jedem Render neu
  const suggestingCoresKey = suggestingCores.map((core) => core.key).join(',')
  const activeCoresKey = activeCoreKeys.join(',')
  const filtersKey = JSON.stringify(filters)

  /**
   * Die Filter-Angebote aller angewählten Kerne und des Auflösers, je Kern höchstens zehn, ohne schon gesetzte Filter.
   * Dasselbe (Feld, Wert) nur einmal (User 2026-10-13, wie in der Vorlage): Es bleibt das Angebot, das etwas zum
   * Wiedererkennen mitbringt (Zusatz oder Bild), sonst das erste in der Reihenfolge der Kerne. Neu gerechnet nur, wenn
   * sich Treffer, Suchwörter, Chips oder Kerne ändern, nicht bei jedem Render (User 2026-10-14).
   */
  const offerWords = searchWordsForOffersOf(searchText)
  const offerWordsKey = offerWords.join(' ')
  const offers = useMemo(() => {
    const collected: SearchOffer[] = []
    for (const core of suggestingCores) {
      let countOfCore = 0
      for (const hit of resultByCore[core.key]?.hits ?? []) {
        for (const offer of core.offersOf(hit, offerWords, solrHooks ?? [])) {
          if (countOfCore >= SEARCH_MAX_OFFERS_PER_CORE || filters.some((existing) => isSameSearchFilter(existing, { fieldName: offer.fieldName, value: offer.value }))) {
            continue
          }
          const index = collected.findIndex((existing) => existing.fieldName === offer.fieldName && existing.value === offer.value)
          if (index < 0) {
            collected.push(offer)
            countOfCore++
          } else if (collected[index].additionText === null && collected[index].previewBase64Png === null && (offer.additionText !== null || offer.previewBase64Png !== null)) {
            collected[index] = offer
          }
        }
      }
    }
    return collected
    // die Kerne, Wörter und Chips stehen über ihre Schlüssel drin
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resultByCore, suggestingCoresKey, offerWordsKey, filtersKey, solrHooks])

  /** Die Vervollständigungen des gerade getippten Worts aus den Treffern derselben Kerne, ohne Doppelte, höchstens fünf */
  const typedWord = typedWordOf(searchText)
  const completions = useMemo(() => {
    const collected: SearchTextCompletion[] = []
    if (typedWord === '') {
      return collected
    }
    for (const core of suggestingCores) {
      for (const hit of resultByCore[core.key]?.hits ?? []) {
        for (const completion of core.completionsOf(hit, typedWord)) {
          if (
            collected.length < SEARCH_MAX_TEXT_COMPLETIONS &&
            !collected.some((existing) => existing.completedText.toLowerCase() === completion.completedText.toLowerCase())
          ) {
            collected.push(completion)
          }
        }
      }
    }
    return collected
    // die Kerne stehen über ihren Schlüssel drin
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resultByCore, suggestingCoresKey, typedWord])

  // stabil, denn die Trefferlisten bekommen sie mit und sollen nicht bei jedem Tastendruck neu zeichnen
  const addFilter = useCallback((filter: SearchFilter) => {
    setFilters((previous) => (previous.some((existing) => isSameSearchFilter(existing, filter)) ? previous : [...previous, filter]))
  }, [])

  function removeFilter(filter: SearchFilter) {
    setFilters((previous) => previous.filter((existing) => !isSameSearchFilter(existing, filter)))
  }

  /**
   * Eine Kachel eines spezialFilterErsterOrdnung wählen: Ihr Chip ersetzt jeden anderen spezialFilterErsterOrdnung,
   * denn es darf immer nur einer aktiv sein (User 2026-10-14). Die Eingabe wird geleert.
   */
  function pickSpezialFilterErsterOrdnung(spezialFilter: SpezialFilterErsterOrdnung, subClass: PickableSubClass) {
    setFilters((previous) => [
      ...previous.filter((existing) => spezialFilterErsterOrdnungOfField(existing.fieldName) === null),
      { fieldName: spezialFilter.fieldName, value: subClass.key, label: fileSubClassNameOf(subClass, language) },
    ])
    setSearchText('')
  }

  /**
   * Ein Angebot einloggen: der Wert wird Chip (fq), Zusatz und Bild wandern mit. Das Suchwort, das ihn traf, geht aus
   * dem Suchtext, samt + davor; kam der Wert ohne Suchwort (die Nummern des Auflösers), wird die Eingabe geleert.
   */
  function pickOffer(offer: SearchOffer) {
    addFilter({
      fieldName: offer.fieldName,
      value: offer.value,
      label: offer.additionText ?? undefined,
      previewBase64Png: offer.previewBase64Png ?? undefined,
    })
    const word = offer.word
    setSearchText((previous) => (word === null ? '' : withoutSearchWord(previous, word)))
  }

  /** Eine Vervollständigung übernehmen: Das getippte Wort wird der ganze Wert, die Suche läuft damit, kein Filter */
  function pickCompletion(completion: SearchTextCompletion) {
    setSearchText((previous) => withTypedWordCompleted(previous, completion.completedText))
    inputRef.current?.focus()
  }

  // Die Vervollständigungen wie in jeder Oberfläche (User 2026-10-13): eine Liste direkt unter dem Suchfeld, solange
  // es den Fokus hat; Pfeiltasten wählen, Enter oder Tab übernimmt, Escape schließt sie für dieses Wort, die Maus geht
  // auch. Kein Filter, kein Chip.
  const [inputFocused, setInputFocused] = useState(false)
  const [dismissedTypedWord, setDismissedTypedWord] = useState<string | null>(null)
  const [completionCursor, setCompletionCursor] = useState({ typedWord: '', index: 0 })
  const completionListOpen = inputFocused && completions.length > 0 && dismissedTypedWord !== typedWord
  const activeCompletionIndex = completionCursor.typedWord === typedWord ? Math.min(completionCursor.index, completions.length - 1) : 0
  /** wo die Liste steht: unter dem Suchfeld, in Bildschirmkoordinaten, denn die ContentBox schneidet Überstehendes ab */
  const [completionListBox, setCompletionListBox] = useState<{ top: number; left: number; width: number } | null>(null)

  useEffect(() => {
    if (!completionListOpen) {
      return
    }
    function place() {
      const box = inputRef.current?.getBoundingClientRect()
      if (box) {
        setCompletionListBox({ top: box.bottom + 2, left: box.left, width: box.width })
      }
    }
    place()
    // die Seite rollt im main, nicht im Fenster: beim Rollen und bei neuer Größe mitwandern
    window.addEventListener('resize', place)
    window.addEventListener('scroll', place, true)
    return () => {
      window.removeEventListener('resize', place)
      window.removeEventListener('scroll', place, true)
    }
  }, [completionListOpen])

  function moveCompletionCursor(step: number) {
    setCompletionCursor({ typedWord, index: (activeCompletionIndex + step + completions.length) % completions.length })
  }

  function runSearch(core: SearchCore, text: string, currentFilters: SearchFilter[], allWords: boolean) {
    const run = (runByCore.current[core.key] ?? 0) + 1
    runByCore.current[core.key] = run
    const request = searchRequestOf(text, currentFilters, allWords)
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
        // kein Direktsprung mehr (User 2026-10-14): ein exakt getippter Schlüssel steht als Hinweis über den Treffern,
        // Enter öffnet ihn (directHit)
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

  /**
   * „weitere laden“ (User 2026-10-14): die nächste Seite desselben Suchtexts mit denselben Chips unten anhängen. Der
   * Zwischenspeicher merkt sich das Ganze unter der Abfrage der ersten Seite, so stellt „zurück zu Suche“ auch die
   * angehängten Seiten wieder her. Ändert sich die Eingabe, bevor die Seite ankommt, wird sie verworfen.
   */
  function loadMore(core: SearchCore) {
    const current = resultByCore[core.key]
    if (current === undefined || loadingMoreByCore[core.key]) {
      return
    }
    const run = runByCore.current[core.key] ?? 0
    const query = latestQueryRef.current
    const firstPageRequest = searchRequestOf(searchText, filters, allWordsRequired)
    setLoadingMoreByCore((previous) => ({ ...previous, [core.key]: true }))
    core
      .search(searchRequestOf(searchText, filters, allWordsRequired, current.page + 1))
      .then(({ data }) => {
        if (runByCore.current[core.key] !== run || latestQueryRef.current !== query) {
          return
        }
        const merged: SearchResultPage = { ...data, hits: [...current.hits, ...data.hits] }
        rememberSearchResult(usersGuid, core.key, firstPageRequest, merged)
        setResultByCore((previous) => ({ ...previous, [core.key]: merged }))
      })
      .catch((error: unknown) => {
        showToast({ kind: 'error', title: t('search.results.loadMoreFailed'), text: error instanceof RequestError ? error.message : undefined })
      })
      .finally(() => setLoadingMoreByCore((previous) => ({ ...previous, [core.key]: false })))
  }

  // „weitere laden“ als stabile Funktion für die Trefferlisten: Sie ruft immer die neueste loadMore, sonst zeichneten
  // die Listen bei jedem Tastendruck neu, nur weil die Funktion neu ist
  const loadMoreRef = useRef(loadMore)
  useEffect(() => {
    loadMoreRef.current = loadMore
  })
  const loadMoreOfCore = useCallback((core: SearchCore) => loadMoreRef.current(core), [])

  // die aktuelle Eingabe für loadMore: Eine Antwort zu einer älteren Eingabe gehört nicht mehr in die Liste
  useEffect(() => {
    latestQueryRef.current = JSON.stringify([searchText, filtersKey, allWordsRequired])
  }, [searchText, filtersKey, allWordsRequired])
  const resolverCoreKey = resolverCore?.key ?? ''

  // Der Stand in die Adresse, der Eintrag im Verlauf wird ersetzt, nicht vermehrt: Zurück führt aus der Suche heraus,
  // nicht durch jede getippte Taste. Der Router-State fällt dabei weg, die Vorbelegung ist da schon eingearbeitet.
  // Der Text erst nach der Tipp-Pause wie die Suche selbst (User 2026-10-14): Jedes Schreiben lässt den Router die
  // ganze Seite samt Header neu durchlaufen. Ein Chip ist ein Klick und geht sofort hinein.
  const writtenFiltersKeyRef = useRef(filtersKey)
  useEffect(() => {
    const params = searchParamsOf(searchText, filters)
    const nextSearch = params.size === 0 ? '' : `?${params.toString()}`
    if (nextSearch === location.search) {
      writtenSearchRef.current = nextSearch
      writtenFiltersKeyRef.current = filtersKey
      return
    }
    const delay = filtersKey !== writtenFiltersKeyRef.current ? 0 : SEARCH_DEBOUNCE_MS
    const timer = setTimeout(() => {
      writtenSearchRef.current = nextSearch
      writtenFiltersKeyRef.current = filtersKey
      setSearchParams(params, { replace: true })
    }, delay)
    return () => clearTimeout(timer)
    // filters über ihren Schlüssel; location.search und setSearchParams ändern sich nur durch diesen Effekt selbst
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchText, filtersKey])

  /**
   * Beim Verlassen der Suche mitgeben, mit dem aktuellen Stand in der Adresse, damit „zurück zu Suche“ ihn
   * wiederherstellt. Dasselbe Objekt, solange die Adresse gleich bleibt: Die Trefferlisten zeichnen sonst neu.
   */
  const originState: OriginState = useMemo(
    () => ({ origin: { path: `${SEARCH_PATH}${location.search}`, label: 'search.title' } }),
    [location.search],
  )

  // Ein exakt getippter Schlüssel (User 2026-10-14): kein Sprung, denn es gibt längere Artikelnummern mit einer gültigen
  // davor. Die Maske zeigt deutlich, dass die Eingabe genau ein Treffer ist; Enter oder der Knopf öffnet ihn, eine
  // weitere Ziffer sucht neu. Nur aus den angewählten Kernen, nie aus dem stillen Auflöser.
  const typedKey = searchText.trim().toLowerCase()
  const directHit = useMemo(() => {
    if (!searchable || typedKey === '') {
      return null
    }
    for (const core of activeCores) {
      if (core.directPathOf === null) {
        continue
      }
      const hit = (resultByCore[core.key]?.hits ?? []).find((candidate) => core.idOf(candidate.fieldValues)?.toLowerCase() === typedKey)
      const path = hit ? core.directPathOf(hit.fieldValues) : null
      if (hit && path !== null) {
        return { core, key: core.idOf(hit.fieldValues) ?? typedKey, path }
      }
    }
    return null
    // die Kerne stehen über ihren Schlüssel drin
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resultByCore, typedKey, activeCoresKey, searchable])

  function openDirectHit() {
    if (directHit !== null) {
      navigate(directHit.path, { state: originState })
    }
  }

  // Je angewähltem Kern eine Suche. Was der Zwischenspeicher schon hat, steht sofort da, ohne Tipp-Pause (Zurück aus
  // einem Treffer, Backspace auf Gesehenes); der Rest nach 250 ms Tipp-Pause.
  useEffect(() => {
    if (!searchable) {
      setResultByCore({})
      setErrorByCore({})
      return
    }
    // je Kern seine Abfrage: die angewählten mit Chips und dem Schalter „jedes Wort“, der stille Auflöser nur mit dem
    // Suchtext und immer „ein Wort reicht“: Fremde Chips (z. B. die Dateiart) kennt er nicht, und er soll Namen zu
    // Nummern auflösen, auch wenn nicht jedes Wort im Kontakt steht („vermes schraube“ findet Vermes)
    const planned: { core: SearchCore; filters: SearchFilter[]; allWords: boolean }[] = activeCores.map((core) => ({
      core,
      filters,
      allWords: allWordsRequired,
    }))
    if (resolverCore !== null) {
      planned.push({ core: resolverCore, filters: [], allWords: false })
    }
    const pendingSearch: typeof planned = []
    for (const plan of planned) {
      const cached = cachedSearchResult(usersGuid, plan.core.key, searchRequestOf(searchText, plan.filters, plan.allWords))
      if (cached !== undefined) {
        setResultByCore((previous) => ({ ...previous, [plan.core.key]: cached }))
        setErrorByCore((previous) => ({ ...previous, [plan.core.key]: null }))
      } else {
        pendingSearch.push(plan)
      }
    }
    if (pendingSearch.length === 0) {
      return
    }
    const timer = setTimeout(() => {
      for (const plan of pendingSearch) {
        runSearch(plan.core, searchText, plan.filters, plan.allWords)
      }
    }, SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
    // die Kerne stehen über activeCoresKey und resolverCoreKey drin, filters über ihren Schlüssel
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchText, filtersKey, activeCoresKey, resolverCoreKey, searchable, allWordsRequired])

  function keyDown(event: KeyboardEvent<HTMLInputElement>) {
    // die offene Liste der Vervollständigungen unter dem Feld zuerst, wie in jeder Oberfläche
    if (completionListOpen) {
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault()
        moveCompletionCursor(event.key === 'ArrowDown' ? 1 : -1)
        return
      }
      if (event.key === 'Enter' || (event.key === 'Tab' && !event.shiftKey)) {
        event.preventDefault()
        pickCompletion(completions[activeCompletionIndex])
        return
      }
      if (event.key === 'Escape') {
        event.preventDefault()
        setDismissedTypedWord(typedWord)
        return
      }
    }
    // Enter öffnet den exakt getippten Schlüssel (User 2026-10-14)
    if (event.key === 'Enter' && directHit !== null && spezialFilterMode === null) {
      event.preventDefault()
      openDirectHit()
      return
    }
    // Tab loggt das erste Angebot ein, wie in der Vorlage; ohne Angebot bleibt Tab, was es ist
    if (event.key === 'Tab' && !event.shiftKey && offers.length > 0) {
      event.preventDefault()
      pickOffer(offers[0])
      return
    }
    if (spezialFilterMode !== null && event.key === 'Enter') {
      event.preventDefault()
      const first = fileSubClassesStartingWith(pickableSubClasses, spezialFilterNeedle, language)[0]
      if (first !== undefined) {
        pickSpezialFilterErsterOrdnung(spezialFilterMode, first)
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
  // Steht ein spezialFilterErsterOrdnung, sind alle Schalter gesperrt, sein Kern leuchtet allein (User 2026-10-14).
  /** Name des Kerns aus den Stammdaten (look des Kerns), sonst aus dem Sprachkatalog */
  const coreLabelOf = (core: SearchCore) =>
    translate(solrCoreOfKey(solrCores, core.key)?.displayName ?? null, language) || t(`search.cores.${core.labelKey}`)
  const lockingCore = lockingSpezialFilter !== null ? (SEARCH_CORES.find((core) => core.key === lockingSpezialFilter.coreKey) ?? null) : null
  const lockHint = lockingCore !== null ? t('search.subClassLock', { core: coreLabelOf(lockingCore) }) : undefined
  const coreToggles: SubheaderToggle[] = [
    {
      key: 'all',
      label: t('search.allCores'),
      active: allActive,
      disabled: cores.length === 0 || lockingSpezialFilter !== null,
      hint: lockHint,
      onClick: () => chooseCores(allActive ? [] : cores.map((core) => core.key)),
    },
  ]
  for (const core of SEARCH_CORES) {
    const allowed = cores.some((candidate) => candidate.key === core.key)
    // Symbol und Farbe des Kerns aus den Stammdaten (look des Kerns), wie die Kernfarbe der Vorlage
    const solrCore = solrCoreOfKey(solrCores, core.key)
    coreToggles.push({
      key: core.key,
      label: coreLabelOf(core),
      symbol: solrCore?.look.symbol ?? undefined,
      color: solrCoreMainColor(solrCore),
      textColor: solrCoreTextColor(solrCore),
      active: allowed && activeCoreKeys.includes(core.key),
      disabled: !allowed || lockingSpezialFilter !== null,
      hint: !allowed ? t('search.missingRole', { role: core.searchAccessRoleKey }) : lockHint,
      onClick: () => toggleCore(core.key),
    })
  }

  // Nur Kerne mit Treffern bekommen ihre Box (User 2026-10-13); ein Kern mit Fehler bleibt sichtbar, sonst verschwände
  // der Fehler. Haben alle angewählten Kerne geantwortet und keiner trifft, sagt das eine Zeile unter der Eingabe.
  const shownResultCores = activeCores.filter(
    (core) => (resultByCore[core.key]?.hits.length ?? 0) > 0 || (errorByCore[core.key] ?? null) !== null,
  )
  const noHitsInAnyCore =
    searchable &&
    running === 0 &&
    activeCores.length > 0 &&
    activeCores.every((core) => resultByCore[core.key] !== undefined && (errorByCore[core.key] ?? null) === null) &&
    shownResultCores.length === 0

  let inputHint: string | null = null
  if (tooManyWords) {
    inputHint = t('search.hint.TOO_MANY_SEARCH_WORDS', { max: SEARCH_MAX_WORDS })
  } else if (spezialFilterMode === null && !searchable) {
    inputHint = t('search.hint.NO_SEARCH_WORD_AND_NO_FILTER', { min: SEARCH_MIN_CHARACTERS })
  } else if (noHitsInAnyCore) {
    inputHint = t('search.results.noHits')
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
        <ContentBox
          title={
            <span className="d-inline-flex align-items-center gap-2">
              {t('search.title')}
              {/* die Bedienung der Suche im Hoverlay statt als Text über dem Feld (User 2026-10-14); interaktiv, damit man
                  bei kleinem Fenster darin rollen kann */}
              <Hoverlay text={<SearchHelp />} interactive>
                <span className="search-help-symbol" tabIndex={0} aria-label={t('search.help.title')}>
                  <Icon name="details" />
                </span>
              </Hoverlay>
            </span>
          }
          footer={
            // die Zahl der Suchwörter im Fuß der Box (User 2026-10-13); ab der vollen Zahl rot und fett, dazu der Rat,
            // einen Filter zu setzen statt weiterer Wörter (User 2026-10-14)
            cores.length > 0 ? (
              <span className={wordsAtLimit ? 'search-words search-words-exceeded' : 'search-words'}>
                {t('search.words', { count: words.length, max: SEARCH_MAX_WORDS })}
                {wordsAtLimit && ` · ${t('search.wordsLimitHint')}`}
              </span>
            ) : undefined
          }
        >
          {cores.length === 0 ? (
            <p className="mb-0">{t('search.noCores')}</p>
          ) : (
            <div className="d-flex flex-column gap-3">
              <SearchFilterChips filters={filters} onRemove={removeFilter} origin={originState} language={language} />
              <SearchSuggestions offers={offers} firstOfferActive={!completionListOpen} origin={originState} language={language} onPickOffer={pickOffer} />
              {/* das Feld auf 70 % der Box, es nimmt ohnehin höchstens fünf Wörter; rechts daneben der Schalter (User 2026-10-14) */}
              <div className="search-input-row">
                <input
                  ref={inputRef}
                  type="search"
                  className="form-control search-input"
                  value={searchText}
                  placeholder={t('search.placeholder')}
                  aria-label={t('search.title')}
                  role="combobox"
                  aria-autocomplete="list"
                  aria-expanded={completionListOpen}
                  aria-controls={SEARCH_COMPLETION_LIST_ID}
                  aria-activedescendant={completionListOpen ? `${SEARCH_COMPLETION_LIST_ID}-${activeCompletionIndex}` : undefined}
                  onChange={(event) => setSearchText(event.target.value)}
                  onKeyDown={keyDown}
                  onFocus={() => setInputFocused(true)}
                  onBlur={() => setInputFocused(false)}
                />
                <ToggleSwitch
                  id="search-all-words-required"
                  checked={allWordsRequired}
                  onChange={chooseAllWordsRequired}
                  labelOn={t('search.allWords.on')}
                  labelOff={t('search.allWords.off')}
                />
                {running > 0 && (
                  <span className="spinner-border spinner-border-sm" role="status" aria-label={t('search.running')} />
                )}
              </div>
              {completionListOpen &&
                completionListBox !== null &&
                createPortal(
                  // das Dropdown von Bootstrap, an den Seitenkörper gehängt wie das Hoverlay, damit die ContentBox es
                  // nicht abschneidet; mousedown statt click, sonst nähme der Klick dem Feld vorher den Fokus
                  <ul
                    id={SEARCH_COMPLETION_LIST_ID}
                    className="dropdown-menu show search-completion-list"
                    role="listbox"
                    aria-label={t('search.completions.title')}
                    style={{ top: completionListBox.top, left: completionListBox.left, width: completionListBox.width }}
                  >
                    {completions.map((completion, index) => (
                      <li key={completion.completedText.toLowerCase()} id={`${SEARCH_COMPLETION_LIST_ID}-${index}`} role="option" aria-selected={index === activeCompletionIndex}>
                        <button
                          type="button"
                          tabIndex={-1}
                          className={index === activeCompletionIndex ? 'dropdown-item active search-completion-item' : 'dropdown-item search-completion-item'}
                          onMouseDown={(event) => {
                            event.preventDefault()
                            pickCompletion(completion)
                          }}
                          onMouseEnter={() => setCompletionCursor({ typedWord, index })}
                        >
                          <span className="search-completion-text">{completion.completedText}</span>
                          {completion.additionText && <span className="search-completion-addition">{completion.additionText}</span>}
                        </button>
                      </li>
                    ))}
                  </ul>,
                  document.body,
                )}
              {directHit !== null && (
                <div className="search-direct-hit" role="status">
                  <span>
                    {t('search.directHit.text', {
                      core: translate(solrCoreOfKey(solrCores, directHit.core.key)?.displayName ?? null, language) || t(`search.cores.${directHit.core.labelKey}`),
                      key: directHit.key,
                    })}
                  </span>
                  <button type="button" className="button-other" onClick={openDirectHit}>
                    {t('search.directHit.open')}
                  </button>
                </div>
              )}
              {inputHint !== null && <span className="small text-muted">{inputHint}</span>}
              {spezialFilterMode !== null && (
                <SearchFileSubClassTiles
                  subClasses={pickableSubClasses}
                  needle={spezialFilterNeedle}
                  hint={spezialFilterMode.prefix === 'fsc.' ? t('search.fsc.hint') : t('search.msc.hint')}
                  language={language}
                  onPick={(subClass) => pickSpezialFilterErsterOrdnung(spezialFilterMode, subClass)}
                />
              )}
            </div>
          )}
        </ContentBox>

        {searchable &&
          shownResultCores.map((core) => (
            <SearchCoreResults
              key={core.key}
              core={core}
              solrCore={solrCoreOfKey(solrCores, core.key)}
              result={resultByCore[core.key] ?? null}
              errorMessage={errorByCore[core.key] ?? null}
              origin={originState}
              onFilter={addFilter}
              loadingMore={loadingMoreByCore[core.key] === true}
              onLoadMore={loadMoreOfCore}
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
  /** die nächste Seite lädt gerade */
  loadingMore: boolean
  /** die nächste Seite dieses Kerns unten anhängen; stabil, sie bekommt den Kern mit */
  onLoadMore: (core: SearchCore) => void
}

/**
 * Die Treffer eines Kerns: Emblem und Zahl im Kopf, der Fehler, die Liste in der Darstellung des Kerns in seiner Farbe
 * (Marken des Treffergrunds, Rand der Treffer), unten die Gesamtzahl, wie viele davon geladen sind, und, wenn es mehr
 * gibt, der Knopf für die nächsten (User 2026-10-14).
 *
 * Mit memo: Die Box zeichnet nur neu, wenn ihre Treffer, ihr Fehler, ihr Laden oder die Herkunft sich ändern, nicht bei
 * jedem Tastendruck im Suchfeld (User 2026-10-14). Dafür gibt die Suchseite nur stabile Werte mit.
 */
const SearchCoreResults = memo(function SearchCoreResults({
  core,
  solrCore,
  result,
  errorMessage,
  origin,
  onFilter,
  loadingMore,
  onLoadMore,
}: SearchCoreResultsProps) {
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
        {total > shown && (
          <>
            <span className="small text-muted">{t('search.results.shown', { count: shown })}</span>
            <button type="button" className="button-other" disabled={loadingMore} onClick={() => onLoadMore(core)}>
              {loadingMore ? t('search.results.loadingMore') : t('search.results.loadMore', { count: Math.min(result.pageSize, total - shown) })}
            </button>
          </>
        )}
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
})
