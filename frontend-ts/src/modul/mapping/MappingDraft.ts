import { useEffect, useState } from 'react'
import { isPlainObject } from '../../components/jsonElementIdentity.ts'
import { toKeyCharacters } from '../../components/keyRule.ts'
import { type AbasInfosys, abasInfosysActionsOf } from '../infosys/AbasInfosys.ts'
import { evaluateLine, evaluatePath, valueText } from './jsonPath.ts'
import {
  fullPathOf,
  genericPath,
  getResponseMappingsByObjectType,
  ignoredPathsOf,
  matchConditionsOf,
  newResponseMappingLine,
  newResponseMappingWithLines,
  pathRelativeToTableRow,
  responseObjectTypeOf,
  type ResponseMappingFetchWay,
  type ResponseMappingLine,
  type ResponseMappingWithLines,
  sourceOf,
  STERNZEIT_PARAMETER_NAME,
  subfetchInputParametersOf,
  tableColumnNameOf,
  tableColumnsOf,
  tableLineOf,
} from './ResponseMapping.ts'

/** Womit die Antwort geholt wurde, die ein Entwurf zerlegt; ein Infosystem immer als hinterlegter Aufruf mit den Werten seiner Platzhalter */
export type MappingDraftFetch =
  | { kind: 'JSON'; sourceName: string }
  | { kind: 'STERNZEIT'; sternzeit: string }
  | { kind: 'INFOSYS'; abasInfosysKey: string; valueByPlaceholderName: Record<string, string> }

/**
 * Ein Mapping im Aufbau (User 2026-10-10): das Gesamt-Mapping der Antwort, die der Baum zeigt. Jeder Klick auf ein
 * Feld wird ein Eintrag mit leerem Key, die Werte rechnet das Blatt live aus der Antwort; ein Klick in eine Liste, zu
 * der schon ein Tabellen-Eintrag steht, wird dessen Spalte; „Ignorieren“ nimmt den Knoten aus dem Baum; ein Feld lässt
 * sich zum kennzeichnenden Feld machen. Gibt es zum Typ der Antwort schon Mappings, stehen sie zur Wahl: eines
 * übernehmen und ändern, oder neu. Ein Subfetch-Eintrag wird im Blatt angelegt und von Hand geholt, seine Antwort
 * bekommt ihren eigenen Entwurf darüber im Stapel.
 */
export interface MappingDraft {
  mainFetch: MappingDraftFetch
  /** die geholte Antwort, aus der das Blatt die Werte rechnet */
  response: unknown
  value: ResponseMappingWithLines
}

/** Wo der Entwurf der geholten Antwort im Stapel steht */
export interface MappingDraftFetchOutcome {
  draftIndex: number
}

/** Das Ergebnis, wenn ein Subfetch-Eintrag seine Anfrage baut: die Anfrage, oder der Grund, warum nicht */
export type SubfetchRequestOutcome = { request: MappingDraftFetch } | { error: 'noInfosys' | 'missingInput' | 'noRow'; name: string }

/**
 * Die Anfrage eines Subfetch-Eintrags gegen die gezeigte Antwort (User 2026-10-10, der Subfetch von Hand): jede
 * Eingabe aus dem JSON (erster Treffer ihrer Adresse) oder statisch. Sternzeit-Fetch: die Eingabe sternzeit.
 * Infosystem: der hinterlegte Aufruf mit seinem Key, die Platzhalter seiner Vorlage mit den Eingaben gefüllt. Ein
 * Subfetch je Zeile nimmt von Hand die erste Zeile seiner Tabelle als Bezug (User 2026-10-13).
 */
export function subfetchRequestOf(line: ResponseMappingLine, response: unknown, abasInfosysList: readonly AbasInfosys[], lines: readonly ResponseMappingLine[]): SubfetchRequestOutcome {
  let scope: unknown = response
  const tableLine = tableLineOf(line, lines)
  if (tableLine !== null) {
    const firstRow = evaluateLine(response, tableLine.sourcePath, null).values[0]
    if (firstRow === undefined || firstRow === null) {
      return { error: 'noRow', name: tableLine.key }
    }
    scope = firstRow
  }
  const valueByName: Record<string, string> = {}
  for (const parameter of subfetchInputParametersOf(line)) {
    if (parameter.path !== null) {
      const found = evaluatePath(scope, parameter.path)[0]
      if (found === undefined || found === null || valueText(found) === '') {
        return { error: 'missingInput', name: parameter.name }
      }
      valueByName[parameter.name] = valueText(found)
    } else if (parameter.value !== null) {
      valueByName[parameter.name] = parameter.value
    }
  }
  if (line.kind === 'SUBFETCH_STERNZEIT') {
    const sternzeit = valueByName[STERNZEIT_PARAMETER_NAME]
    return sternzeit === undefined ? { error: 'missingInput', name: STERNZEIT_PARAMETER_NAME } : { request: { kind: 'STERNZEIT', sternzeit } }
  }
  const abasInfosys = abasInfosysList.find((candidate) => candidate.key === line.abasInfosysKey)
  if (abasInfosys === undefined) {
    return { error: 'noInfosys', name: line.abasInfosysKey ?? '' }
  }
  // lässt sich die Vorlage mit den Eingaben füllen? Sonst fehlt ein Platzhalter, das Backend sagte dasselbe
  if (abasInfosysActionsOf(abasInfosys, valueByName) === null) {
    return { error: 'missingInput', name: Object.keys(valueByName).join(', ') }
  }
  return { request: { kind: 'INFOSYS', abasInfosysKey: abasInfosys.key, valueByPlaceholderName: valueByName } }
}

/** Die Kopffelder einer ABAS-Antwort, je Feldname ein Objekt mit value und text */
const HEAD_FIELDS_PATH = '$.content.data.head.fields'

/**
 * Die Namen der Custom-Felder im Kopf der Antwort: jedes Feld, dessen Name mit y beginnt, hat die Firma selbst in ABAS
 * angelegt. Jedes davon wird immer gespeichert (User 2026-10-11), auch wenn die Mappe es heute nicht zeigt.
 */
export function customFieldNamesOf(response: unknown): string[] {
  const fields = evaluatePath(response, HEAD_FIELDS_PATH)[0]
  return isPlainObject(fields) ? Object.keys(fields).filter((name) => name.startsWith('y')) : []
}

/** Der Feldname aus der Adresse eines Kopffeld-Werts: $.content.data.head.fields.ykategorie.value → ykategorie */
export function customFieldNameOfPath(path: string): string {
  return path.slice(HEAD_FIELDS_PATH.length + 1).replace(/\.value$/, '')
}

/** Die gespeicherten Mappings zum Typ der gezeigten Antwort, geladen nach jedem Fetch */
export interface ExistingResponseMappings {
  responseObjectType: string
  mappings: ResponseMappingWithLines[]
  errorMessage: string | null
}

function withLines(draft: MappingDraft, lines: ResponseMappingLine[]): MappingDraft {
  return { ...draft, value: { ...draft.value, lines: lines.map((line, index) => ({ ...line, position: index + 1 })) } }
}

/** Der Fetchweg, wie er ins Mapping geschrieben wird */
function fetchWayOf(fetch: MappingDraftFetch): ResponseMappingFetchWay {
  switch (fetch.kind) {
    case 'JSON':
      return { kind: 'JSON' }
    case 'STERNZEIT':
      return { kind: 'STERNZEIT', sternzeit: fetch.sternzeit }
    case 'INFOSYS':
      return { kind: 'INFOSYS', abasInfosysKey: fetch.abasInfosysKey, valueByPlaceholderName: fetch.valueByPlaceholderName }
  }
}

/** Passt ein gespeichertes Mapping zur Antwort: jedes kennzeichnende Feld steht so in der Antwort */
export function responseMappingMatchesResponse(mapping: ResponseMappingWithLines, response: unknown): boolean {
  return matchConditionsOf(mapping.responseMapping).every((condition) => {
    const found = evaluatePath(response, condition.path)[0]
    return found !== undefined && valueText(found) === condition.equals
  })
}

/** Der Zustand der Entwürfe und ihre Handgriffe, einmal je Tab */
export function useMappingDraft() {
  const [drafts, setDrafts] = useState<MappingDraft[]>([])
  const [currentIndex, setCurrentIndex] = useState(0)
  const [existing, setExisting] = useState<ExistingResponseMappings | null>(null)
  const current = drafts[currentIndex] ?? null
  const currentObjectType = current?.value.responseMapping.responseObjectType ?? ''

  // zum Typ der gezeigten Antwort die gespeicherten Mappings holen, damit sich eines übernehmen lässt
  useEffect(() => {
    if (currentObjectType === '') {
      return
    }
    let active = true
    getResponseMappingsByObjectType(currentObjectType)
      .then((result) => {
        if (active) {
          setExisting({ responseObjectType: currentObjectType, mappings: result.data, errorMessage: null })
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setExisting({ responseObjectType: currentObjectType, mappings: [], errorMessage: error instanceof Error ? error.message : String(error) })
        }
      })
    return () => {
      active = false
    }
  }, [currentObjectType])

  function replaceDraftAt(draftIndex: number, changed: MappingDraft) {
    setDrafts((previous) => previous.map((draft, index) => (index === draftIndex ? changed : draft)))
  }

  function replaceCurrent(changed: MappingDraft) {
    replaceDraftAt(currentIndex, changed)
  }

  function freshDraftOf(fetch: MappingDraftFetch, response: unknown): MappingDraft {
    const objectType = responseObjectTypeOf(response) ?? ''
    return { mainFetch: fetch, response, value: newResponseMappingWithLines(objectType, fetchWayOf(fetch)) }
  }

  /** Nach jeder Hauptantwort: ein neuer Anfang, der Stapel beginnt mit diesem Entwurf */
  function recordFetch(fetch: MappingDraftFetch, response: unknown): MappingDraftFetchOutcome {
    setDrafts([freshDraftOf(fetch, response)])
    setCurrentIndex(0)
    return { draftIndex: 0 }
  }

  /**
   * Die Antwort eines von Hand geholten Subfetch-Eintrags des gezeigten Entwurfs (User 2026-10-10): Sie bekommt ihren
   * Entwurf direkt darüber im Stapel, was darüber stand, fällt weg, denn es reicht, einen Subfetch aktuell zu halten.
   * Der Eintrag selbst bleibt, wie er ist. Gibt den Platz des neuen Entwurfs zurück.
   */
  function recordSubfetch(fetch: MappingDraftFetch, response: unknown): number {
    const fresh = freshDraftOf(fetch, response)
    const draftIndex = currentIndex + 1
    setDrafts((previous) => [...previous.slice(0, draftIndex), fresh])
    setCurrentIndex(draftIndex)
    return draftIndex
  }

  /** Ein gespeichertes Mapping zum Typ übernehmen: Speichern ändert es dann (PUT), die Werte kommen aus der gezeigten Antwort */
  function adoptExisting(mapping: ResponseMappingWithLines) {
    if (current !== null) {
      replaceCurrent({ ...current, value: { responseMapping: { ...mapping.responseMapping }, lines: mapping.lines.map((line) => ({ ...line })) } })
    }
  }

  /** Wieder ein leeres Mapping zum Typ der gezeigten Antwort */
  function startFresh() {
    if (current !== null) {
      replaceCurrent({ ...current, value: newResponseMappingWithLines(currentObjectType, fetchWayOf(current.mainFetch)) })
    }
  }

  /**
   * Ein Klick auf ein Feld einer Antwort: ein Eintrag mit leerem Key im Entwurf dieser Antwort (ohne Angabe der
   * gezeigte), der dann auch gezeigt wird; ein zweiter Klick nimmt ihn wieder heraus. Steht zu der Liste, in der das
   * Feld liegt, ein Tabellen-Eintrag, wird das Feld dessen Spalte (ein zweiter Klick nimmt die Spalte heraus). Listen
   * und Objekte tun nichts, dafür gibt es „Ignorieren“.
   */
  function recordClick(path: string, value: unknown, draftIndex = currentIndex) {
    const draft = drafts[draftIndex] ?? null
    if (draft === null || (typeof value === 'object' && value !== null)) {
      return
    }
    const source = sourceOf(path)
    setCurrentIndex(draftIndex)
    const tableLine = source.elementPath === null ? undefined : draft.value.lines.find((line) => line.kind === 'TABLE' && line.sourcePath === source.sourcePath)
    if (tableLine !== undefined && source.elementPath !== null) {
      const elementPath = source.elementPath
      const columns = tableColumnsOf(tableLine)
      const changedColumns = columns.some((column) => column.elementPath === elementPath)
        ? columns.filter((column) => column.elementPath !== elementPath)
        : [...columns, { name: tableColumnNameOf(elementPath), elementPath }]
      replaceDraftAt(
        draftIndex,
        withLines(draft, draft.value.lines.map((line) => (line === tableLine ? { ...line, tableColumnsJson: JSON.stringify(changedColumns) } : line))),
      )
      return
    }
    const existingLine = draft.value.lines.find(
      (line) => line.kind === 'VALUE' && line.sourcePath === source.sourcePath && line.elementPath === source.elementPath,
    )
    if (existingLine !== undefined) {
      replaceDraftAt(draftIndex, withLines(draft, draft.value.lines.filter((line) => line !== existingLine)))
      return
    }
    const line = { ...newResponseMappingLine('VALUE', draft.value.lines.length + 1), sourcePath: source.sourcePath, elementPath: source.elementPath }
    replaceDraftAt(draftIndex, withLines(draft, [...draft.value.lines, line]))
  }

  /**
   * Der nächste Klick im Baum setzt die Adresse einer Subfetch-Eingabe (User 2026-10-10, „im Baum wählen“): die
   * Eingabe bekommt den allgemeinen Pfad, ein statischer Wert fällt dabei weg.
   */
  function setSubfetchInputPath(lineId: number | null, parameterName: string, path: string, draftIndex = currentIndex) {
    const draft = drafts[draftIndex] ?? null
    if (draft === null) {
      return
    }
    replaceDraftAt(
      draftIndex,
      withLines(
        draft,
        draft.value.lines.map((line) => {
          if (line.id !== lineId) {
            return line
          }
          // beim Subfetch je Zeile gilt die Adresse je Zeilenelement: der Teil hinter der Liste der Tabelle
          const tableLine = tableLineOf(line, draft.value.lines)
          const generic = tableLine === null ? genericPath(path) : (pathRelativeToTableRow(path, tableLine.sourcePath) ?? genericPath(path))
          const parameters = subfetchInputParametersOf(line)
          const changed = parameters.some((parameter) => parameter.name === parameterName)
            ? parameters.map((parameter) => (parameter.name === parameterName ? { ...parameter, path: generic, value: null } : parameter))
            : [...parameters, { name: parameterName, path: generic, value: null }]
          return { ...line, subfetchInputParametersJson: JSON.stringify(changed) }
        }),
      ),
    )
  }

  /** Den Knoten und alles darunter aus dem Baum nehmen; ein Eintrag darauf fällt weg. Im Entwurf dieser Antwort, ohne Angabe im gezeigten. */
  function ignorePath(path: string, draftIndex = currentIndex) {
    const draft = drafts[draftIndex] ?? null
    if (draft === null) {
      return
    }
    const generic = genericPath(path)
    const ignored = ignoredPathsOf(draft.value.responseMapping)
    if (ignored.includes(generic)) {
      return
    }
    const changed = withLines(draft, draft.value.lines.filter((line) => !fullPathOf(line).startsWith(generic)))
    setCurrentIndex(draftIndex)
    replaceDraftAt(draftIndex, {
      ...changed,
      value: { ...changed.value, responseMapping: { ...changed.value.responseMapping, ignoredPathsJson: JSON.stringify([...ignored, generic]) } },
    })
  }

  /** Aus einem Eintrag ein kennzeichnendes Feld machen: Adresse gleich dem Wert aus der Antwort, der Eintrag fällt weg */
  function makeCondition(line: ResponseMappingLine) {
    if (current === null) {
      return
    }
    const first = evaluateLine(current.response, line.sourcePath, line.elementPath).values[0]
    const conditions = [...matchConditionsOf(current.value.responseMapping), { path: fullPathOf(line), equals: first === undefined ? '' : valueText(first) }]
    const changed = withLines(current, current.value.lines.filter((candidate) => candidate !== line))
    replaceCurrent({
      ...changed,
      value: { ...changed.value, responseMapping: { ...changed.value.responseMapping, matchConditionsJson: JSON.stringify(conditions) } },
    })
  }

  /** Jede Änderung im Editor */
  function changeCurrent(value: ResponseMappingWithLines) {
    if (current !== null) {
      replaceCurrent({ ...current, value })
    }
  }

  /** Nach dem Speichern: der Entwurf ist erledigt, darunter geht es weiter */
  function removeCurrent() {
    setDrafts((previous) => previous.filter((_draft, index) => index !== currentIndex))
    setCurrentIndex(Math.max(0, currentIndex - 1))
  }

  function reset() {
    setDrafts([])
    setCurrentIndex(0)
  }

  /** Was mit dem Knoten im Entwurf dieser Antwort (ohne Angabe im gezeigten) passiert ist: gemappt, kennzeichnend, ignoriert (auch als Teil eines ignorierten Knotens), oder nichts */
  function decisionOf(path: string, draftIndex = currentIndex): 'mapped' | 'condition' | 'ignored' | null {
    const draft = drafts[draftIndex] ?? null
    if (draft === null) {
      return null
    }
    const generic = genericPath(path)
    if (draft.value.lines.some((line) => fullPathOf(line) === generic)) {
      return 'mapped'
    }
    // eine Spalte eines Tabellen-Eintrags: die Liste plus die Adresse je Element
    if (draft.value.lines.some((line) => line.kind === 'TABLE' && tableColumnsOf(line).some((column) => `${line.sourcePath}.${column.elementPath}` === generic))) {
      return 'mapped'
    }
    if (matchConditionsOf(draft.value.responseMapping).some((condition) => condition.path === generic)) {
      return 'condition'
    }
    const ignored = ignoredPathsOf(draft.value.responseMapping).some(
      (candidate) => generic === candidate || generic.startsWith(`${candidate}.`) || generic.startsWith(`${candidate}[`),
    )
    return ignored ? 'ignored' : null
  }

  /**
   * Die Werte der y-Felder im Kopf der Antwort dieses Entwurfs (ohne Angabe des gezeigten), die noch kein Eintrag
   * speichert: Als kennzeichnendes Feld oder als Eingabe eines Subfetchs ist ein Feld nicht gespeichert, ein
   * ignoriertes bleibt ignoriert.
   */
  function unmappedCustomFieldPathsOf(draftIndex = currentIndex): string[] {
    const draft = drafts[draftIndex] ?? null
    if (draft === null) {
      return []
    }
    return customFieldNamesOf(draft.response)
      .map((name) => `${HEAD_FIELDS_PATH}.${name}.value`)
      .filter((path) => {
        const decision = decisionOf(path, draftIndex)
        return decision === null || decision === 'condition'
      })
  }

  /**
   * Je y-Feld ohne Eintrag ein VALUE-Eintrag mit dem Key <Mapping-Key>_<YNAME> (User 2026-10-11, „y-Felder aufnehmen“):
   * die Custom-Felder werden immer gespeichert. Ohne Key des Mappings nur <YNAME>, den Key ergänzt man dann im Blatt.
   */
  function recordCustomFields(draftIndex = currentIndex): number {
    const draft = drafts[draftIndex] ?? null
    if (draft === null) {
      return 0
    }
    const paths = unmappedCustomFieldPathsOf(draftIndex)
    if (paths.length === 0) {
      return 0
    }
    const mappingKey = draft.value.responseMapping.key
    const lines = paths.map((path, index) => {
      const name = customFieldNameOfPath(path)
      const key = toKeyCharacters(mappingKey === '' ? name : `${mappingKey}_${name}`)
      return { ...newResponseMappingLine('VALUE', draft.value.lines.length + index + 1), sourcePath: path, key }
    })
    setCurrentIndex(draftIndex)
    replaceDraftAt(draftIndex, withLines(draft, [...draft.value.lines, ...lines]))
    return lines.length
  }

  return {
    drafts,
    currentIndex,
    current,
    existing: existing !== null && existing.responseObjectType === currentObjectType ? existing : null,
    recordFetch,
    recordSubfetch,
    setSubfetchInputPath,
    adoptExisting,
    startFresh,
    recordClick,
    ignorePath,
    makeCondition,
    changeCurrent,
    selectDraft: setCurrentIndex,
    removeCurrent,
    reset,
    decisionOf,
    unmappedCustomFieldPathsOf,
    recordCustomFields,
  }
}
