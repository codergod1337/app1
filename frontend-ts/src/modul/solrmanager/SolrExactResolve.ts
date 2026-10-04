import type { SolrField, SolrFieldType } from './SolrField.ts'
import { convertSolrFieldValue } from './SolrFieldValue.ts'
import type { SolrHook } from './SolrHook.ts'

/**
 * Ein Feld, nach dem sich exakt auflösen lässt: ein normales Feld eines Kerns oder ein Hook (in jedem Kern ein string).
 * Das Backend sagt je User, welche davon er benutzen darf (exactResolveAllowedFieldAndHookNamesOfCoreForCurrentUser),
 * die Maske baut daraus ihre Auswahl.
 */
export interface SolrExactResolveField {
  name: string
  type: SolrFieldType
  hook: boolean
}

/** Die exakten Werte einer Auflösung je Feldname, als roher Text: mehrere Werte ODER, mehrere Felder UND */
export type SolrExactResolveFieldValuesByFieldName = Record<string, string[]>

/**
 * Die auflösbaren Felder eines Kerns aus den Stammdaten: erst die indexierten Felder des Kerns in Listenreihenfolge,
 * dann alle Hooks. Mit allowedFieldNames nur die genannten, in der Reihenfolge der Stammdaten.
 */
export function exactResolveFieldsOfCore(
  solrFields: readonly SolrField[],
  solrHooks: readonly SolrHook[],
  coreKey: string,
  allowedFieldNames?: readonly string[],
): SolrExactResolveField[] {
  const fields: SolrExactResolveField[] = []
  for (const solrField of solrFields) {
    if (solrField.coreKey === coreKey && solrField.indexed) {
      fields.push({ name: solrField.name, type: solrField.type, hook: false })
    }
  }
  for (const solrHook of solrHooks) {
    fields.push({ name: solrHook.key, type: 'STRING', hook: true })
  }
  return allowedFieldNames === undefined ? fields : fields.filter((field) => allowedFieldNames.includes(field.name))
}

/**
 * Macht aus den rohen Werten das JSON fürs Backend: je Feld eine Liste im Typ des Feldes (Zahl, boolean, Zeitpunkt als
 * ISO 8601, sonst Text). Was sich nicht umrechnen lässt, geht als Text, damit das Backend es beanstandet. Felder ohne
 * Werte fallen weg.
 */
export function exactResolveFieldValuesForBackend(
  fields: readonly SolrExactResolveField[],
  fieldValuesByFieldName: Readonly<SolrExactResolveFieldValuesByFieldName>,
): Record<string, unknown[]> {
  const fieldValues: Record<string, unknown[]> = {}
  for (const field of fields) {
    const raw = fieldValuesByFieldName[field.name]
    if (raw === undefined) {
      continue
    }
    const values = raw.map((entry) => convertSolrFieldValue(field.type, entry)).filter((entry) => entry !== undefined)
    if (values.length > 0) {
      fieldValues[field.name] = values
    }
  }
  return fieldValues
}

/** Die Werte einer gespeicherten Dateifreigabe (JSON-Text aus dem Register) zum Anzeigen: je Feld seine Werte als Text */
export function exactResolveFieldValuesFromRegisterJson(fieldValuesJson: string): SolrExactResolveFieldValuesByFieldName {
  try {
    const parsed: unknown = JSON.parse(fieldValuesJson)
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return {}
    }
    const fieldValuesByFieldName: SolrExactResolveFieldValuesByFieldName = {}
    for (const [name, value] of Object.entries(parsed as Record<string, unknown>)) {
      fieldValuesByFieldName[name] = (Array.isArray(value) ? value : [value]).map((entry) => String(entry))
    }
    return fieldValuesByFieldName
  } catch {
    return {}
  }
}
