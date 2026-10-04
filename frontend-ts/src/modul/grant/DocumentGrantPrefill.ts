import { useLocation } from 'react-router'
import type { SolrExactResolveFieldValuesByFieldName } from '../solrmanager/SolrExactResolve.ts'

/** Der Tab „Dateifreigaben“, das Ziel einer Vorbelegung */
export const DOCUMENT_GRANTS_TAB_PATH = '/grant/documents'

/**
 * Womit ein anderes Modul den Tab „Dateifreigaben“ öffnet, z. B. die Dokumentseite des DMS mit Kern DMS und der id des
 * Dokuments: Der Tab stellt den Kern ein, trägt die Werte als Bedingungen ein und löst sofort auf, der User wählt nur
 * noch den Empfänger. Kommt als Router-State mit, nie über die URL (Vorgabe 2026-10-03); nach einem Reload ist die
 * Vorbelegung weg und der Tab verhält sich wie immer.
 */
export interface DocumentGrantPrefill {
  coreKey: string
  fieldValuesByFieldName: SolrExactResolveFieldValuesByFieldName
}

/** State beim Navigieren: navigate(DOCUMENT_GRANTS_TAB_PATH, { state: { documentGrantPrefill, origin } }) */
export interface DocumentGrantPrefillState {
  documentGrantPrefill: DocumentGrantPrefill
}

/** Die mitgegebene Vorbelegung, null ohne eine (Tab-Link, direkter Aufruf, Reload) oder bei einer, die nicht passt */
export function useDocumentGrantPrefill(): DocumentGrantPrefill | null {
  const state: unknown = useLocation().state
  if (typeof state !== 'object' || state === null || !('documentGrantPrefill' in state)) {
    return null
  }
  const prefill: unknown = state.documentGrantPrefill
  if (
    typeof prefill !== 'object' ||
    prefill === null ||
    !('coreKey' in prefill) ||
    !('fieldValuesByFieldName' in prefill) ||
    typeof prefill.coreKey !== 'string' ||
    prefill.coreKey === '' ||
    typeof prefill.fieldValuesByFieldName !== 'object' ||
    prefill.fieldValuesByFieldName === null
  ) {
    return null
  }
  const fieldValuesByFieldName: SolrExactResolveFieldValuesByFieldName = {}
  for (const [name, values] of Object.entries(prefill.fieldValuesByFieldName as Record<string, unknown>)) {
    if (Array.isArray(values) && values.every((entry) => typeof entry === 'string')) {
      fieldValuesByFieldName[name] = values
    }
  }
  if (Object.keys(fieldValuesByFieldName).length === 0) {
    return null
  }
  return { coreKey: prefill.coreKey, fieldValuesByFieldName }
}
