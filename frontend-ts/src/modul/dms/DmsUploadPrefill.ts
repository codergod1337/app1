import { useLocation } from 'react-router'
import type { SolrHookValuesByKey } from '../solrmanager/SolrHookFields.tsx'

/** Die Upload-Maske des DMS, das Ziel einer Vorbelegung */
export const DMS_UPLOAD_PATH = '/dms/upload'

/**
 * Womit ein anderes Modul die Upload-Maske öffnet und steuert (User 2026-10-08, nach dem Muster der
 * DocumentGrantPrefill): jeder Parameter freiwillig, die Maske fragt jeden einzeln und verhält sich ohne ihn wie immer.
 * Kommt als Router-State mit, nie über die URL (Vorgabe 2026-10-03); nach einem Reload ist die Vorbelegung weg.
 *
 * Heute: die neue Fassung einer Versionskette (`versionChain`). Später kommen weitere Parameter als weitere Felder
 * dazu, z. B. die Dateiart aus einer Mappe oder die Hooks eines Artikels.
 */
export interface DmsUploadPrefill {
  /**
   * Neue Fassung: die Kette, in die die Datei gehört. Die Maske holt den Kopf, nimmt seine Dateiart gesperrt und
   * seine Hooks vorbelegt, nimmt genau eine Datei, lehnt dieselben Bytes ab und schickt versionChain mit; nach dem
   * Upload geht es zur neuen Fassung.
   */
  versionChain?: string
  /** die Dateiart vorbelegt */
  fileSubClassKey?: string
  /** die Dateiart ist nicht wählbar, nur gezeigt */
  lockFileSubClass?: boolean
  /** die Hooks vorbelegt, änderbar */
  hooks?: SolrHookValuesByKey
  /** höchstens so viele Dateien im Stapel */
  maxFiles?: number
}

/** State beim Navigieren: navigate(DMS_UPLOAD_PATH, { state: { dmsUploadPrefill, origin } }) */
export interface DmsUploadPrefillState {
  dmsUploadPrefill: DmsUploadPrefill
}

/** Die mitgegebene Vorbelegung, null ohne eine (Tab-Link, direkter Aufruf, Reload); unpassende Werte fallen weg */
export function useDmsUploadPrefill(): DmsUploadPrefill | null {
  const state: unknown = useLocation().state
  if (typeof state !== 'object' || state === null || !('dmsUploadPrefill' in state)) {
    return null
  }
  const raw: unknown = state.dmsUploadPrefill
  if (typeof raw !== 'object' || raw === null) {
    return null
  }
  const record = raw as Record<string, unknown>
  const prefill: DmsUploadPrefill = {}
  if (typeof record.versionChain === 'string' && record.versionChain !== '') {
    prefill.versionChain = record.versionChain
  }
  if (typeof record.fileSubClassKey === 'string' && record.fileSubClassKey !== '') {
    prefill.fileSubClassKey = record.fileSubClassKey
  }
  if (record.lockFileSubClass === true) {
    prefill.lockFileSubClass = true
  }
  if (typeof record.hooks === 'object' && record.hooks !== null) {
    const hooks: Record<string, string[]> = {}
    for (const [key, values] of Object.entries(record.hooks as Record<string, unknown>)) {
      if (Array.isArray(values) && values.every((entry) => typeof entry === 'string')) {
        hooks[key] = values
      }
    }
    prefill.hooks = hooks
  }
  if (typeof record.maxFiles === 'number' && Number.isInteger(record.maxFiles) && record.maxFiles > 0) {
    prefill.maxFiles = record.maxFiles
  }
  return Object.keys(prefill).length === 0 ? null : prefill
}
