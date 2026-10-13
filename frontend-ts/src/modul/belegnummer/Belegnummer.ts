import { request } from '../../api/client.ts'

/** Das Modul Belegnummer, für jeden Angemeldeten (User 2026-10-11, nach der Bezugsnummer der Vorlage) */
export const BELEGNUMMER_PATH = '/belegnummer'

/**
 * Die Antwort der Belegnummer (AbasBelegnummerLookupResult.java): was die Nummer ist und wo sie in ABAS steht. found
 * false ist kein Fehler, ABAS hat geantwortet und kennt die Nummer nur nicht.
 */
export interface AbasBelegnummerLookupResult {
  belegnummer: string
  found: boolean
  /** die Sternzeit des Objekts, der Weg in die ABAS-GUI */
  sternzeit: string | null
  /** der rohe Typ, wie ABAS ihn nennt, z. B. obj:data:4:5 */
  objectType: string | null
  /** der Key unseres Mappings zu diesem Typ, der Klartext; null ohne Mapping */
  responseMappingKey: string | null
  /** das Ziel dieses Mappings (SOLR_ARTIKEL): daran erkennt die Seite, in welche Mappe die primaryId führt */
  responseMappingTargetKey: string | null
  /** die id, die das Mapping vergibt, beim Artikel die Artikelnummer */
  primaryId: string | null
}

// ===== Endpunkte des AbasBelegnummerController (abas) =====

/** Eine Nummer nachschlagen: der hinterlegte Aufruf BELEG2ID und dann das Objekt hinter der Sternzeit. 400 ohne Nummer, 503 wenn ABAS nicht antwortet. */
export function lookupBelegnummer(belegnummer: string) {
  return request<AbasBelegnummerLookupResult>('POST', '/api/rest/v1/abas/belegnummer', { belegnummer })
}
