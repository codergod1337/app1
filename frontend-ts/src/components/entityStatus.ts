import { DEFAULT_LANGUAGE, type LanguageCode } from '../branding/languages.ts'
import type { MultilingualText } from './multilingual.ts'

/**
 * Die Status, die jede Entity tragen kann. Im Backend ein freier String, hier steht der Name in jeder Sprache.
 * Vorerst nur Deutsch.
 */
export const ENTITY_STATUSES = ['ACTIVE', 'PENDING_ADMIN_ACTION', 'SUSPENDED', 'ARCHIVED', 'DEPRECATED', 'DELETED']

const ENTITY_STATUS_NAMES: Record<string, MultilingualText> = {
  ACTIVE: { de: 'aktiv' },
  PENDING_ADMIN_ACTION: { de: 'wartet auf Admin' },
  SUSPENDED: { de: 'gesperrt' },
  ARCHIVED: { de: 'archiviert' },
  DEPRECATED: { de: 'veraltet' },
  DELETED: { de: 'gelöscht' },
}

/** Der Name des Status in der gewünschten Sprache. Fehlt er, die Standardsprache, ist der Status unbekannt, er selbst. */
export function entityStatusName(status: string, language: LanguageCode = DEFAULT_LANGUAGE): string {
  const names = ENTITY_STATUS_NAMES[status]
  return names?.[language] ?? names?.[DEFAULT_LANGUAGE] ?? status
}
