import { request } from '../../api/client.ts'

/** Das Modul Mapping, Eintritt mit AR ADMIN */
export const MAPPING_PATH = '/mapping'

/** Was ein Eintrag tut (ResponseMappingLineKind.java): ein Wert, eine Tabelle in ein Feld, oder ein Subfetch per Sternzeit oder Infosystem */
export type ResponseMappingLineKind = 'VALUE' | 'TABLE' | 'SUBFETCH_STERNZEIT' | 'SUBFETCH_INFOSYS'

/** Die beiden Arten eines Subfetchs, die erste Wahl beim Anlegen (User 2026-10-10) */
export type ResponseMappingSubfetchKind = 'SUBFETCH_STERNZEIT' | 'SUBFETCH_INFOSYS'

export function isSubfetchKind(kind: ResponseMappingLineKind): kind is ResponseMappingSubfetchKind {
  return kind === 'SUBFETCH_STERNZEIT' || kind === 'SUBFETCH_INFOSYS'
}

/**
 * Eine Eingabe eines Subfetchs (ResponseMappingSubfetchInputParameter.java): der Wert aus dem JSON der Antwort (path)
 * oder statisch (value); beides null heißt ohne Wert senden. Beim Sternzeit-Fetch heißt die eine Eingabe sternzeit,
 * beim Infosystem je Platzhalter der Vorlage eine.
 */
export interface ResponseMappingSubfetchInputParameter {
  name: string
  path: string | null
  value: string | null
}

export const STERNZEIT_PARAMETER_NAME = 'sternzeit'

/** Eine Spalte eines Tabellen-Eintrags (ResponseMappingTableColumn.java): der Name im JSON-Objekt der Zeile und die Adresse je Element */
export interface ResponseMappingTableColumn {
  name: string
  elementPath: string
}

/** Welche Rolle der Wert eines Eintrags als id des Dokuments spielt (ResponseMappingLineIdRole.java): PK, seine Erweiterung, oder ein weiterer eindeutiger Wert */
export type ResponseMappingLineIdRole = 'PRIMARY' | 'COMPOSITE' | 'ALTERNATIVE'

/** Wie die Antwort geholt wird (ResponseMappingFetchWayKind.java) */
export type ResponseMappingFetchWayKind = 'STERNZEIT' | 'INFOSYS' | 'JSON'

/**
 * Der Fetchweg eines Mappings (ResponseMappingFetchWay.java), als JSON in fetchWayJson: bei STERNZEIT die
 * Beispiel-Sternzeit, bei INFOSYS der hinterlegte Aufruf mit Beispielwerten seiner Platzhalter (User 2026-10-11: jede
 * Infosystem-Anfrage über einen hinterlegten Aufruf)
 */
export interface ResponseMappingFetchWay {
  kind: ResponseMappingFetchWayKind
  sternzeit?: string
  abasInfosysKey?: string
  valueByPlaceholderName?: Record<string, string>
}

/** Ein Feld, das zusammen mit dem Typ die Antwort kennzeichnet (ResponseMappingMatchCondition.java) */
export interface ResponseMappingMatchCondition {
  path: string
  equals: string
}

/**
 * Ein Eintrag eines Mappings (ResponseMappingLine.java): die Quelle im JSON, optional eine Bedingung, der Key und
 * seine Rolle als id. Mit elementPath ist sourcePath eine Liste und der Wert die Liste aller Elementwerte; bei TABLE ist
 * sourcePath die Liste und tableColumnsJson die Spalten je Zeile; bei einem Subfetch ist sourcePath leer, die Eingaben
 * stehen in subfetchInputParametersJson, beim Infosystem zeigt abasInfosysKey auf den hinterlegten Aufruf. Ein Subfetch
 * mit tableLineKey läuft je Zeile des TABLE-Eintrags mit diesem Key (User 2026-10-13): seine Eingabe-Adressen gelten
 * je Zeilenelement, die Unterantwort hängt in der Zeile unter seinem Key. Vor dem Speichern ist id null oder eine
 * negative Entwurfsnummer.
 */
export interface ResponseMappingLine {
  id: number | null
  position: number
  kind: ResponseMappingLineKind
  sourcePath: string
  elementPath: string | null
  onlyIfPath: string | null
  onlyIfEquals: string | null
  key: string
  idRole: ResponseMappingLineIdRole | null
  abasInfosysKey: string | null
  subfetchInputParametersJson: string | null
  tableColumnsJson: string | null
  tableLineKey: string | null
}

let nextNewLineId = -1

/** Ein neuer Eintrag mit Entwurfsnummer als id; der Aufrufer setzt Quelle und Position */
export function newResponseMappingLine(kind: ResponseMappingLineKind, position: number): ResponseMappingLine {
  return {
    id: nextNewLineId--,
    position,
    kind,
    sourcePath: '',
    elementPath: null,
    onlyIfPath: null,
    onlyIfEquals: null,
    key: '',
    idRole: null,
    abasInfosysKey: null,
    subfetchInputParametersJson: kind === 'SUBFETCH_STERNZEIT' ? JSON.stringify([{ name: STERNZEIT_PARAMETER_NAME, path: null, value: null }]) : null,
    tableColumnsJson: kind === 'TABLE' ? '[]' : null,
    tableLineKey: null,
  }
}

/** Der TABLE-Eintrag, je dessen Zeile ein Subfetch läuft; null, wenn der Subfetch für den Kopf läuft oder die Tabelle fehlt */
export function tableLineOf(line: Pick<ResponseMappingLine, 'tableLineKey'>, lines: readonly ResponseMappingLine[]): ResponseMappingLine | null {
  if (line.tableLineKey === null || line.tableLineKey === '') {
    return null
  }
  return lines.find((candidate) => candidate.kind === 'TABLE' && candidate.key === line.tableLineKey) ?? null
}

/** Die Eingaben eines Subfetch-Eintrags, gelesen; leer bei einem anderen Eintrag oder unlesbarem JSON */
export function subfetchInputParametersOf(line: Pick<ResponseMappingLine, 'subfetchInputParametersJson'>): ResponseMappingSubfetchInputParameter[] {
  return arrayOf(line.subfetchInputParametersJson)
    .filter(isRecord)
    .filter((parameter) => typeof parameter.name === 'string' && parameter.name !== '')
    .map((parameter) => ({
      name: String(parameter.name),
      path: typeof parameter.path === 'string' && parameter.path !== '' ? parameter.path : null,
      value: typeof parameter.value === 'string' && parameter.value !== '' ? parameter.value : null,
    }))
}

/** Die Spalten eines Tabellen-Eintrags, gelesen; leer bei einem anderen Eintrag oder unlesbarem JSON */
export function tableColumnsOf(line: Pick<ResponseMappingLine, 'tableColumnsJson'>): ResponseMappingTableColumn[] {
  return arrayOf(line.tableColumnsJson)
    .filter(isRecord)
    .filter((column) => typeof column.name === 'string' && typeof column.elementPath === 'string')
    .map((column) => ({ name: String(column.name), elementPath: String(column.elementPath) }))
}

/**
 * Der Name einer Tabellenspalte aus der Adresse je Element, nach der Regel der Feldnamen (User 2026-10-10):
 * fields.elem.value wird elem, fields.elem.text wird elemText, sonst das letzte Glied
 */
export function tableColumnNameOf(elementPath: string): string {
  const segments = elementPath.split('.').filter((segment) => segment !== '')
  const last = segments[segments.length - 1] ?? ''
  const before = segments[segments.length - 2] ?? ''
  if ((last === 'value' || last === 'text') && before !== '' && before !== 'fields') {
    return last === 'value' ? before : `${before}Text`
  }
  return last
}

/** Das Gesamt-Mapping einer Antwort (ResponseMapping.java); die JSON-Spalten kommen als Text, die Leser unten zerlegen sie */
export interface ResponseMapping {
  id: number | null
  /** Name, Key-Regel, ändert sich nie */
  key: string
  responseObjectType: string
  matchConditionsJson: string
  targetKey: string | null
  ignoredPathsJson: string
  fetchWayJson: string
}

/** Mapping und Einträge als Einheit, so geht es zum Backend und kommt zurück (ResponseMappingWithLines.java) */
export interface ResponseMappingWithLines {
  responseMapping: ResponseMapping
  lines: ResponseMappingLine[]
}

// ===== Endpunkte des ResponseMappingAdminController =====

const RESPONSE_MAPPING_ADMIN_PATH = '/api/rest/v1/admin/mapping/response'

export function getAllResponseMappings() {
  return request<ResponseMappingWithLines[]>('GET', RESPONSE_MAPPING_ADMIN_PATH)
}

export function getResponseMapping(id: number) {
  return request<ResponseMappingWithLines>('GET', `${RESPONSE_MAPPING_ADMIN_PATH}/${id}`)
}

/** Alle Mappings zu einem Typ, auch leer: das Frontend fragt das nach jedem Fetch mit dem Typ aus der Antwort */
export function getResponseMappingsByObjectType(responseObjectType: string) {
  return request<ResponseMappingWithLines[]>('POST', `${RESPONSE_MAPPING_ADMIN_PATH}/by-object-type`, { responseObjectType })
}

/** Neu samt Einträgen: 400 bei ungültigem Key, Typ, Ziel, Bedingung oder Eintrag, 409 wenn der Key vergeben ist */
export function createResponseMapping(newResponseMappingData: ResponseMappingWithLines) {
  return request<ResponseMappingWithLines>('POST', RESPONSE_MAPPING_ADMIN_PATH, newResponseMappingData)
}

/** Die id im Mapping bestimmt es, der Key ändert sich nie, die Einträge werden ersetzt */
export function updateResponseMapping(changedResponseMappingData: ResponseMappingWithLines) {
  return request<ResponseMappingWithLines>('PUT', RESPONSE_MAPPING_ADMIN_PATH, changedResponseMappingData)
}

export function deleteResponseMapping(id: number) {
  return request<ResponseMappingWithLines>('DELETE', RESPONSE_MAPPING_ADMIN_PATH, { id })
}

/**
 * Neue Positionen der Einträge eines gespeicherten Mappings nach Drag and Drop, wie bei den AR: jede Eintrags-id mit
 * ihrer Position, alle in einem Aufruf (positionsByKey). Nur für Einträge, die schon eine id haben; ein Entwurf nimmt
 * seine Reihenfolge beim Anlegen mit.
 */
export function changeResponseMappingLinePositions(newPositionById: Record<string, number>) {
  return request<ResponseMappingWithLines>('POST', `${RESPONSE_MAPPING_ADMIN_PATH}/line/position`, newPositionById)
}

// ===== Lesen und Schreiben der JSON-Spalten =====

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function arrayOf(json: string | null): unknown[] {
  if (json === null || json.trim() === '') {
    return []
  }
  try {
    const parsed: unknown = JSON.parse(json)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function matchConditionsOf(responseMapping: ResponseMapping): ResponseMappingMatchCondition[] {
  return arrayOf(responseMapping.matchConditionsJson)
    .filter(isRecord)
    .map((condition) => ({
      path: typeof condition.path === 'string' ? condition.path : '',
      equals: typeof condition.equals === 'string' ? condition.equals : '',
    }))
}

export function ignoredPathsOf(responseMapping: ResponseMapping): string[] {
  return arrayOf(responseMapping.ignoredPathsJson).filter((path): path is string => typeof path === 'string')
}

export function fetchWayOf(responseMapping: ResponseMapping): ResponseMappingFetchWay | null {
  try {
    const parsed: unknown = JSON.parse(responseMapping.fetchWayJson)
    if (!isRecord(parsed) || typeof parsed.kind !== 'string') {
      return null
    }
    const values = parsed.valueByPlaceholderName
    return {
      kind: parsed.kind === 'INFOSYS' ? 'INFOSYS' : parsed.kind === 'STERNZEIT' ? 'STERNZEIT' : 'JSON',
      sternzeit: typeof parsed.sternzeit === 'string' ? parsed.sternzeit : undefined,
      abasInfosysKey: typeof parsed.abasInfosysKey === 'string' ? parsed.abasInfosysKey : undefined,
      valueByPlaceholderName: isRecord(values)
        ? Object.fromEntries(Object.entries(values).filter((entry): entry is [string, string] => typeof entry[1] === 'string'))
        : undefined,
    }
  } catch {
    return null
  }
}

/** Ein leeres Mapping für einen neuen Entwurf */
export function newResponseMappingWithLines(responseObjectType: string, fetchWay: ResponseMappingFetchWay): ResponseMappingWithLines {
  return {
    responseMapping: {
      id: null,
      key: '',
      responseObjectType,
      matchConditionsJson: '[]',
      targetKey: null,
      ignoredPathsJson: '[]',
      fetchWayJson: JSON.stringify(fetchWay),
    },
    lines: [],
  }
}

// ===== Adressen =====

/** Jedes Listenelement im Pfad zu [*]: aus $.content.data.table[id=(367,3,0)].fields.zn.value wird $.content.data.table[*].fields.zn.value */
export function genericPath(path: string): string {
  return path.replace(/\[[^\]]*\]/g, '[*]')
}

/**
 * Eine Adresse aus dem Baum als Adresse je Zeile einer Tabelle (User 2026-10-13, Subfetch je Zeile): der Teil hinter
 * der Liste der Tabelle, aus $.content.data.table[*].fields.yqmappqm2pplan.value wird fields.yqmappqm2pplan.value.
 * null, wenn die Adresse nicht in dieser Tabelle liegt.
 */
export function pathRelativeToTableRow(path: string, tableSourcePath: string): string | null {
  const generic = genericPath(path)
  const base = genericPath(tableSourcePath)
  return generic.startsWith(`${base}.`) ? generic.slice(base.length + 1) : null
}

/**
 * Die Quelle eines Eintrags aus einem Pfad im Baum: ohne Liste ist es der Pfad; mit Liste die Liste als sourcePath und
 * der Rest je Element als elementPath, $.content.data.table[*] und fields.zn.value
 */
export function sourceOf(path: string): { sourcePath: string; elementPath: string | null } {
  const generic = genericPath(path)
  const list = generic.indexOf('[*]')
  if (list < 0) {
    return { sourcePath: generic, elementPath: null }
  }
  const rest = generic.slice(list + 3)
  return { sourcePath: generic.slice(0, list + 3), elementPath: rest.startsWith('.') ? rest.slice(1) : rest }
}

/** Die ganze Adresse eines Eintrags, Liste und Element zusammen */
export function fullPathOf(line: Pick<ResponseMappingLine, 'sourcePath' | 'elementPath'>): string {
  return line.elementPath === null || line.elementPath === '' ? line.sourcePath : `${line.sourcePath}.${line.elementPath}`
}

/** Der Typ der Antwort, wie ABAS ihn in content.data.meta.objectType liefert; null, wenn das JSON ihn nicht trägt */
export function responseObjectTypeOf(json: unknown): string | null {
  if (!isRecord(json) || !isRecord(json.content) || !isRecord(json.content.data) || !isRecord(json.content.data.meta)) {
    return null
  }
  const objectType = json.content.data.meta.objectType
  return typeof objectType === 'string' && objectType !== '' ? objectType : null
}
