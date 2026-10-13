import { type FormEvent, type ReactNode, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { downloadTextFile } from '../../components/base64File.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { translate } from '../../components/multilingual.ts'
import { showToast } from '../../components/toastStore.ts'
import { fetchAbasSternzeitRaw, getAbasErpBaseUrl, isValidSternzeit } from '../abasmanager/AbasSternzeit.ts'
import { abasInfosysActionsOf, abasInfosysPlaceholderNamesOf, abasInfosysRequestTextOf, runAbasInfosys, useAbasInfosysList } from '../infosys/AbasInfosys.ts'
import { type MappingDraftFetch, subfetchRequestOf, useMappingDraft } from './MappingDraft.ts'
import { MappingDraftBox } from './MappingDraftBox.tsx'
import { MappingJsonTreeBox } from './MappingJsonTreeBox.tsx'
import { fullPathOf, type ResponseMappingLine } from './ResponseMapping.ts'
import type { SubfetchInputPick } from './ResponseMappingEditor.tsx'

/** Steht in der Vorschau, solange die Basis-URL noch nicht da ist oder ABAS nicht eingerichtet ist */
const BASE_URL_PLACEHOLDER = '<abas>'

/** Welche Box zuletzt geholt hat, damit Laufanzeige und Fehler dort stehen */
type FetchSource = 'sternzeit' | 'infosys'

/** Die geholte Antwort mit der Anfrage, die sie geliefert hat, und dem Platz ihres Entwurfs im Stapel */
interface FetchedResponse {
  source: FetchSource
  requestText: string
  /** die geholte Sternzeit, der Dateiname des Downloads; null bei einem Infosystem */
  sternzeit: string | null
  value: unknown
  draftIndex: number
}

/** Die Antwort eines von Hand geholten Subfetch-Eintrags, unter der Hauptantwort; nur eine zur Zeit (User 2026-10-10) */
interface SubfetchedResponse {
  lineKey: string
  requestText: string
  value: unknown
  draftIndex: number
}

/** Welche Stelle der Baum anspringen soll, in welchem Baum (dem Entwurf) */
interface RevealRequest {
  path: string
  nonce: number
  draftIndex: number
}

/** Feldnamen aus einer Eingabe, getrennt durch Komma oder Leerzeichen */
function fieldNamesOf(text: string): string[] {
  return text
    .split(/[\s,]+/)
    .map((name) => name.trim())
    .filter((name) => name !== '')
}

/**
 * Der Tab „ABAS holen“: oben die Sternzeit mit der URL, die das Backend an ABAS schickt, darunter ein hinterlegter
 * Infosystem-Aufruf mit den Werten seiner Platzhalter (User 2026-10-11: jede Infosystem-Anfrage geht über das Modul
 * Infosysteme, Gruppe, Name und Aktionen stehen in der Vorlage), jeweils mit Vorschau der Anfrage; unten die Antwort
 * als Baum. Das Layout ist vorläufig (User 2026-10-10: erst bauen, dann das Layout).
 */
export function MappingAbasFetchTab() {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const [baseUrl, setBaseUrl] = useState<string | null>(null)
  const [sternzeit, setSternzeit] = useState('')
  const [headFieldsText, setHeadFieldsText] = useState('')
  const [tableFieldsText, setTableFieldsText] = useState('')
  const [selectedInfosysKey, setSelectedInfosysKey] = useState('')
  const [valueByName, setValueByName] = useState<Record<string, string>>({})
  const [busySource, setBusySource] = useState<FetchSource | null>(null)
  const [failed, setFailed] = useState<{ source: FetchSource; message: string } | null>(null)
  const [fetched, setFetched] = useState<FetchedResponse | null>(null)
  /** zählt jedes Holen, damit die Baum-Box mit Suche und Auswahl neu startet */
  const [fetchCount, setFetchCount] = useState(0)
  const [subfetched, setSubfetched] = useState<SubfetchedResponse | null>(null)
  const [subfetchCount, setSubfetchCount] = useState(0)
  const [subfetchBusy, setSubfetchBusy] = useState(false)
  const mappingDraft = useMappingDraft()
  /** die hinterlegten Infosystem-Aufrufe: der einzige Weg zu einem Infosystem, auch für die Subfetches */
  const { abasInfosysList, errorMessage: listErrorMessage } = useAbasInfosysList()
  /** die Quelle, die ein Baum anspringen soll, nach einem Klick auf einen Wert im Blatt */
  const [reveal, setReveal] = useState<RevealRequest | null>(null)
  /** welche Subfetch-Eingabe der nächste Klick im Baum setzt („im Baum wählen“, User 2026-10-10) */
  const [subfetchInputPick, setSubfetchInputPick] = useState<SubfetchInputPick | null>(null)

  function messageOf(error: unknown): string {
    return error instanceof RequestError ? error.message : t('common.unknownError')
  }

  // die Basis-URL für die Vorschau, einmal beim Betreten
  useEffect(() => {
    let current = true
    getAbasErpBaseUrl()
      .then((result) => {
        if (current) {
          setBaseUrl(result.data.baseUrl)
        }
      })
      .catch(() => {
        if (current) {
          setBaseUrl(null)
        }
      })
    return () => {
      current = false
    }
  }, [])

  const base = baseUrl ?? BASE_URL_PLACEHOLDER

  // ===== Sternzeit =====

  const sternzeitTrimmed = sternzeit.trim()
  const sternzeitValid = isValidSternzeit(sternzeitTrimmed)
  const headFieldNames = fieldNamesOf(headFieldsText)
  const tableFieldNames = fieldNamesOf(tableFieldsText)
  const headFieldsParam = headFieldNames.length === 0 ? '*' : headFieldNames.join(',')
  const tableFieldsParam = tableFieldNames.length === 0 ? '' : `&tableFields=${tableFieldNames.join(',')}`
  const sternzeitUrl = `${base}/obj/data/byId/${sternzeitTrimmed === '' ? '(…)' : sternzeitTrimmed}?headFields=${headFieldsParam}${tableFieldsParam}`

  // ===== Infosystem: ein hinterlegter Aufruf, je Platzhalter ein Wert =====

  const abasInfosys = abasInfosysList?.find((candidate) => candidate.key === selectedInfosysKey) ?? null
  const placeholders = abasInfosys === null ? [] : abasInfosysPlaceholderNamesOf(abasInfosys.requestBodyTemplate)
  const valueByPlaceholderName = Object.fromEntries(placeholders.map((name) => [name, (valueByName[name] ?? '').trim()]))
  const infosysComplete = abasInfosys !== null && placeholders.every((name) => valueByPlaceholderName[name] !== '')
  const infosysActions = abasInfosys === null || !infosysComplete ? null : abasInfosysActionsOf(abasInfosys, valueByPlaceholderName)
  const infosysRequestText = abasInfosys === null ? `POST ${base}/infosys/data/…/…` : abasInfosysRequestTextOf(abasInfosys, base)

  /** Die Anfrage eines Aufrufs als Text, für die Vorschau und die Überschrift der Antwort */
  function infosysRequestTextOf(abasInfosysKey: string): string {
    const found = abasInfosysList?.find((candidate) => candidate.key === abasInfosysKey)
    return found === undefined ? `POST ${base}/infosys/data/…/… (${abasInfosysKey})` : abasInfosysRequestTextOf(found, base)
  }

  // ===== Holen =====

  /** Holen, zeigen und den Entwurf fortschreiben: neuer Anfang oder, wenn die Anfrage einen angeklickten Wert nutzt, ein Schritt */
  function runFetch(source: FetchSource, requestText: string, draftFetch: MappingDraftFetch, send: () => Promise<{ data: unknown }>) {
    setBusySource(source)
    setFailed(null)
    send()
      .then((result) => {
        const { draftIndex } = mappingDraft.recordFetch(draftFetch, result.data)
        setFetched({ source, requestText, sternzeit: draftFetch.kind === 'STERNZEIT' ? draftFetch.sternzeit : null, value: result.data, draftIndex })
        setFetchCount((count) => count + 1)
        // eine neue Hauptantwort: der alte Subfetch darunter gehört nicht mehr dazu
        setSubfetched(null)
        setSubfetchInputPick(null)
        showToast({ kind: 'info', title: t('mapping.draft.started') })
      })
      .catch((error: unknown) => {
        setFailed({ source, message: messageOf(error) })
      })
      .finally(() => setBusySource(null))
  }

  /**
   * Einen Subfetch-Eintrag des gezeigten Entwurfs von Hand holen (User 2026-10-10): die Anfrage aus dem Eintrag und der
   * Antwort des Entwurfs, die Antwort als zweiter Baum unter der Hauptantwort, ihr Entwurf direkt über dem gezeigten.
   */
  function fetchSubfetch(line: ResponseMappingLine) {
    const draft = mappingDraft.current
    if (draft === null || subfetchBusy) {
      return
    }
    const outcome = subfetchRequestOf(line, draft.response, abasInfosysList ?? [], draft.value.lines)
    if ('error' in outcome) {
      let title: string
      if (outcome.error === 'noInfosys') {
        title = t('mapping.abasFetch.subfetchNoInfosys', { key: line.key || line.kind, infosys: outcome.name })
      } else if (outcome.error === 'noRow') {
        title = t('mapping.abasFetch.subfetchNoRow', { key: line.key || line.kind, table: outcome.name })
      } else {
        title = t('mapping.abasFetch.subfetchNoValue', { key: line.key || line.kind, name: outcome.name })
      }
      showToast({ kind: 'error', title })
      return
    }
    const perRow = line.tableLineKey !== null && line.tableLineKey !== ''
    const request = outcome.request
    let requestText: string
    let send: () => Promise<{ data: unknown }>
    if (request.kind === 'STERNZEIT') {
      requestText = `GET ${base}/obj/data/byId/${request.sternzeit}?headFields=*`
      send = () => fetchAbasSternzeitRaw(request.sternzeit, [], [])
    } else if (request.kind === 'INFOSYS') {
      requestText = infosysRequestTextOf(request.abasInfosysKey)
      send = () => runAbasInfosys(request.abasInfosysKey, request.valueByPlaceholderName)
    } else {
      return
    }
    setSubfetchBusy(true)
    send()
      .then((result) => {
        const draftIndex = mappingDraft.recordSubfetch(request, result.data)
        setSubfetched({ lineKey: line.key || line.kind, requestText, value: result.data, draftIndex })
        setSubfetchCount((count) => count + 1)
        showToast({ kind: 'info', title: t(perRow ? 'mapping.draft.subfetchFetchedFirstRow' : 'mapping.draft.subfetchFetched') })
      })
      .catch((error: unknown) => {
        showToast({ kind: 'error', title: t('mapping.abasFetch.subfetchFailed'), text: messageOf(error) })
      })
      .finally(() => setSubfetchBusy(false))
  }

  function fetchSternzeit(event: FormEvent) {
    event.preventDefault()
    if (!sternzeitValid || busySource !== null) {
      return
    }
    runFetch('sternzeit', `GET ${sternzeitUrl}`, { kind: 'STERNZEIT', sternzeit: sternzeitTrimmed }, () =>
      fetchAbasSternzeitRaw(sternzeitTrimmed, headFieldNames, tableFieldNames),
    )
  }

  /**
   * Die zuletzt geholte Sternzeit-Antwort als Datei (User 2026-10-13): Dateiname die geholte Sternzeit, z. B.
   * (214271,2,0).json, Inhalt allein die rohe Antwort von ABAS, nichts drum herum. Der Name kommt aus der Antwort,
   * nicht aus dem Eingabefeld, denn ein Klick im Baum ändert das Feld.
   */
  function downloadSternzeitResponse() {
    if (fetched === null || fetched.sternzeit === null) {
      return
    }
    downloadTextFile(`${fetched.sternzeit}.json`, JSON.stringify(fetched.value, null, 2), 'application/json')
  }

  function fetchInfosys(event: FormEvent) {
    event.preventDefault()
    if (abasInfosys === null || infosysActions === null || busySource !== null) {
      return
    }
    runFetch('infosys', infosysRequestText, { kind: 'INFOSYS', abasInfosysKey: abasInfosys.key, valueByPlaceholderName }, () =>
      runAbasInfosys(abasInfosys.key, valueByPlaceholderName),
    )
  }

  /**
   * Ein Klick in eine Antwort: wird gerade eine Subfetch-Eingabe gewählt, bekommt sie die Adresse des Knotens; sonst in
   * den Entwurf dieser Antwort, und eine angeklickte Sternzeit steht gleich im Sternzeit-Feld
   */
  function selectInResponse(draftIndex: number, path: string, value: unknown) {
    if (subfetchInputPick !== null) {
      mappingDraft.setSubfetchInputPath(subfetchInputPick.lineId, subfetchInputPick.parameterName, path, draftIndex)
      setSubfetchInputPick(null)
      showToast({ kind: 'info', title: t('mapping.draft.inputPathPicked', { name: subfetchInputPick.parameterName }) })
      return
    }
    mappingDraft.recordClick(path, value, draftIndex)
    if (typeof value === 'string' && isValidSternzeit(value)) {
      setSternzeit(value)
    }
  }

  /** Die Baum-Box einer Antwort, gebunden an den Entwurf dieser Antwort im Stapel */
  function treeBoxOf(key: number, value: unknown, title: ReactNode, draftIndex: number) {
    return (
      <MappingJsonTreeBox
        key={key}
        value={value}
        title={title}
        onSelect={(path, nodeValue) => selectInResponse(draftIndex, path, nodeValue)}
        onIgnore={mappingDraft.current === null ? undefined : (path) => mappingDraft.ignorePath(path, draftIndex)}
        decisionOf={mappingDraft.current === null ? undefined : (path) => mappingDraft.decisionOf(path, draftIndex)}
        reveal={reveal !== null && reveal.draftIndex === draftIndex ? reveal : null}
      />
    )
  }

  function footerOf(source: FetchSource) {
    if (busySource === source) {
      return (
        <span>
          <span className="spinner-border spinner-border-sm me-2" role="status" aria-label={t('common.loading')} />
          {t('mapping.abasFetch.fetching')}
        </span>
      )
    }
    if (failed !== null && failed.source === source) {
      return <span className="text-danger">{t('mapping.abasFetch.failed', { error: failed.message })}</span>
    }
    if (source === 'infosys' && listErrorMessage !== null) {
      return <span className="text-danger">{listErrorMessage}</span>
    }
    if (baseUrl === null) {
      return <span className="text-danger">{t('mapping.abasFetch.baseUrlUnknown')}</span>
    }
    return <span>{t('mapping.abasFetch.previewHint')}</span>
  }

  return (
    <>
      <ContentBox title={t('mapping.abasFetch.sternzeitTitle')} footer={footerOf('sternzeit')}>
        <form className="d-flex flex-column gap-2" onSubmit={fetchSternzeit}>
          <div className="row g-2">
            <div className="col-12 col-md-4">
              <label className="form-label mb-1" htmlFor="mapping-abas-sternzeit">
                {t('mapping.abasFetch.sternzeitLabel')}
              </label>
              <input
                id="mapping-abas-sternzeit"
                className="form-control form-control-sm font-monospace"
                value={sternzeit}
                placeholder={t('mapping.abasFetch.sternzeitPlaceholder')}
                onChange={(event) => setSternzeit(event.target.value)}
              />
            </div>
            <div className="col-12 col-md-4">
              <label className="form-label mb-1" htmlFor="mapping-abas-head-fields">
                {t('mapping.abasFetch.headFieldsLabel')}
              </label>
              <input
                id="mapping-abas-head-fields"
                className="form-control form-control-sm font-monospace"
                value={headFieldsText}
                placeholder={t('mapping.abasFetch.headFieldsPlaceholder')}
                onChange={(event) => setHeadFieldsText(event.target.value)}
              />
            </div>
            <div className="col-12 col-md-4">
              <label className="form-label mb-1" htmlFor="mapping-abas-table-fields">
                {t('mapping.abasFetch.tableFieldsLabel')}
              </label>
              <input
                id="mapping-abas-table-fields"
                className="form-control form-control-sm font-monospace"
                value={tableFieldsText}
                placeholder={t('mapping.abasFetch.tableFieldsPlaceholder')}
                onChange={(event) => setTableFieldsText(event.target.value)}
              />
            </div>
          </div>
          <div>
            <div className="small">{t('mapping.abasFetch.previewLabel')}</div>
            <code className="text-break">GET {sternzeitUrl}</code>
          </div>
          <div className="d-flex flex-wrap gap-2">
            <button type="submit" className="button-other" disabled={!sternzeitValid || busySource !== null}>
              <Icon name="abasManager" /> {t('mapping.abasFetch.fetch')}
            </button>
            <button
              type="button"
              className="button-other"
              disabled={fetched === null || fetched.sternzeit === null || busySource !== null}
              onClick={downloadSternzeitResponse}
            >
              <Icon name="download" /> {t('mapping.abasFetch.downloadJson')}
            </button>
          </div>
        </form>
      </ContentBox>

      <ContentBox title={t('mapping.abasFetch.infosysTitle')} footer={footerOf('infosys')}>
        <form className="d-flex flex-column gap-2" onSubmit={fetchInfosys}>
          <div className="row g-2">
            <div className="col-12 col-md-6">
              <label className="form-label mb-1" htmlFor="mapping-abas-infosys-call">
                {t('infosys.run.call')}
              </label>
              <select
                id="mapping-abas-infosys-call"
                className="form-select form-select-sm"
                value={selectedInfosysKey}
                disabled={abasInfosysList === null}
                onChange={(event) => {
                  setSelectedInfosysKey(event.target.value)
                  setValueByName({})
                }}
              >
                <option value="">{abasInfosysList !== null && abasInfosysList.length === 0 ? t('infosys.run.noCalls') : t('infosys.run.chooseCall')}</option>
                {(abasInfosysList ?? []).map((candidate) => (
                  <option key={candidate.key} value={candidate.key}>
                    {candidate.key}: {translate(candidate.displayName, language) || candidate.key} ({candidate.infosysGroup}/{candidate.infosysName})
                  </option>
                ))}
              </select>
            </div>
          </div>
          {abasInfosys !== null && (
            <div className="row g-2">
              {placeholders.length === 0 && <div className="col-12 text-body-secondary small">{t('infosys.calls.form.noPlaceholders')}</div>}
              {placeholders.map((name) => (
                <div key={name} className="col-12 col-md-4">
                  <label className="form-label mb-1" htmlFor={`mapping-abas-infosys-${name}`}>
                    <code>{name}</code>
                  </label>
                  <input
                    id={`mapping-abas-infosys-${name}`}
                    className="form-control form-control-sm font-monospace"
                    value={valueByName[name] ?? ''}
                    placeholder={t('infosys.run.inputPlaceholder', { name })}
                    onChange={(event) => setValueByName((current) => ({ ...current, [name]: event.target.value }))}
                  />
                </div>
              ))}
            </div>
          )}
          <div>
            <div className="small">{t('mapping.abasFetch.previewLabel')}</div>
            <code className="text-break">{infosysRequestText}</code>
            {abasInfosys !== null && <pre className="mb-0 mt-1 small">{JSON.stringify({ actions: infosysActions ?? JSON.parse(abasInfosys.requestBodyTemplate) }, null, 2)}</pre>}
          </div>
          <div>
            <button type="submit" className="button-other" disabled={abasInfosys === null || infosysActions === null || busySource !== null}>
              <Icon name="abasManager" /> {t('mapping.abasFetch.fetch')}
            </button>
          </div>
        </form>
      </ContentBox>

      <MappingDraftBox
        mappingDraft={mappingDraft}
        onJumpToLine={(line) => setReveal({ path: fullPathOf(line), nonce: (reveal?.nonce ?? 0) + 1, draftIndex: mappingDraft.currentIndex })}
        onFetchSubfetch={fetchSubfetch}
        onPickSubfetchInput={setSubfetchInputPick}
        subfetchInputPick={subfetchInputPick}
      />

      {fetched !== null &&
        treeBoxOf(
          fetchCount,
          fetched.value,
          <>
            {t('mapping.abasFetch.responseTitle')} <code className="small text-break">{fetched.requestText}</code>
          </>,
          fetched.draftIndex,
        )}

      {subfetched !== null &&
        treeBoxOf(
          subfetchCount,
          subfetched.value,
          <>
            {t('mapping.abasFetch.subfetchTitle', { key: subfetched.lineKey })} <code className="small text-break">{subfetched.requestText}</code>
          </>,
          subfetched.draftIndex,
        )}
    </>
  )
}
