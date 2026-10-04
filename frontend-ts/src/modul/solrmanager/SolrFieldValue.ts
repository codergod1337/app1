import type { SolrField, SolrFieldType } from './SolrField.ts'

/** Der rohe Wert einer Eingabe (SolrFieldValueInput): ein Text je Feld, bei multiValued eine Liste. Umgerechnet wird erst beim Senden. */
export type SolrFieldRawValue = string | string[]

/**
 * Macht aus den rohen Eingaben das Dokument fürs Backend: je Feld sein Wert im JSON-Typ des Feldes (Zahl, boolean,
 * Zeitpunkt als ISO 8601 in UTC, sonst Text), multiValued als Liste. Was sich nicht umrechnen lässt (keine Zahl, kein
 * Datum), geht als Text, damit das Backend es beanstandet und nicht stillschweigend eine Null entsteht.
 */
export function solrFieldDocument(
  solrFields: readonly SolrField[],
  rawValues: Readonly<Record<string, SolrFieldRawValue>>,
): Record<string, unknown> {
  const document: Record<string, unknown> = {}
  for (const solrField of solrFields) {
    const raw = rawValues[solrField.name]
    if (raw === undefined) {
      continue
    }
    if (solrField.multiValued) {
      const values = (Array.isArray(raw) ? raw : [raw])
        .map((entry) => convertValue(solrField, entry))
        .filter((entry) => entry !== undefined)
      if (values.length > 0) {
        document[solrField.name] = values
      }
    } else {
      const value = convertValue(solrField, Array.isArray(raw) ? (raw[0] ?? '') : raw)
      if (value !== undefined) {
        document[solrField.name] = value
      }
    }
  }
  return document
}

function convertValue(solrField: SolrField, raw: string): unknown {
  return convertSolrFieldValue(solrField.type, raw)
}

/** Ein roher Text im JSON-Typ des Feldtyps, undefined wenn leer. Auch für das Auflösen (SolrResolve.ts). */
export function convertSolrFieldValue(type: SolrFieldType, raw: string): unknown {
  const text = raw.trim()
  if (text === '') {
    return undefined
  }
  switch (type) {
    case 'BOOLEAN':
      return text === 'true' ? true : text === 'false' ? false : text
    case 'LONG':
    case 'DOUBLE': {
      const number = Number(text)
      return Number.isFinite(number) ? number : text
    }
    case 'DATE': {
      const date = new Date(text)
      return Number.isNaN(date.getTime()) ? text : date.toISOString()
    }
    default:
      return text
  }
}
