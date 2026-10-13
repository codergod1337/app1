import { useEffect, useState } from 'react'
import { request, RequestError } from '../../api/client.ts'
import i18n from '../../branding/i18n.ts'
import type { AbasInfosysAction } from '../abasmanager/AbasInfosys.ts'
import { ARTIKELMAPPE_DISPLAY_CONTEXT_KEY } from '../artikel/Artikel.ts'

/**
 * Ein hinterlegter Infosystem-Aufruf (AbasInfosys.java, Tabelle abas_infosysteme, User 2026-10-10): die Endung der URL
 * (Gruppe und Name) und der Request-Body als Vorlage mit Platzhaltern {{name}} für das, was je Aufruf wechselt, meist
 * eine Nummer. Jede Infosystem-Anfrage geht über so einen Aufruf (User 2026-10-11): Ein Subfetch-Eintrag im Mapping
 * zeigt mit seinem Key hierher und füllt nur die Platzhalter, der Tab „Ausführen“ füllt sie von Hand, die Artikelmappe
 * aus den Feldern des Artikels, die Belegnummer aus der Eingabe (ow1/BELEG2ID wie in der Vorlage).
 */
export interface AbasInfosys {
  /** normale Key-Regel, ändert sich nie */
  key: string
  /** mehrsprachiges JSON */
  displayName: string
  infosysGroup: string
  infosysName: string
  requestBodyTemplate: string
  listingPosition: number
  /** die Anzeigeorte, wo der Aufruf von selbst erscheint: ARTIKELMAPPE heißt ein Tab der Mappe; null oder leer heißt nirgends */
  displayContextKeys: string[] | null
}

/** Die Anzeigeorte, die es heute gibt; andere Keys sind erlaubt, sie wirken nur nirgends */
export const KNOWN_ABAS_INFOSYS_DISPLAY_CONTEXT_KEYS: readonly string[] = [ARTIKELMAPPE_DISPLAY_CONTEXT_KEY]

/** Ob der Aufruf an diesem Ort von selbst erscheint */
export function isAbasInfosysShownIn(abasInfosys: Pick<AbasInfosys, 'displayContextKeys'>, displayContextKey: string): boolean {
  return (abasInfosys.displayContextKeys ?? []).includes(displayContextKey)
}

/** Ein Platzhalter in der Vorlage: {{kartikel}} */
const PLACEHOLDER = /\{\{([A-Za-z0-9_]+)}}/g

/** Die Namen der Platzhalter einer Vorlage in Reihenfolge, ohne Doppelte: das, was ein Aufruf eingeben muss */
export function abasInfosysPlaceholderNamesOf(requestBodyTemplate: string): string[] {
  const names: string[] = []
  for (const match of requestBodyTemplate.matchAll(PLACEHOLDER)) {
    if (!names.includes(match[1])) {
      names.push(match[1])
    }
  }
  return names
}

/** Die Vorlage eines neuen Aufrufs: das Feld als Platzhalter, der Startknopf ohne Wert */
export function abasInfosysTemplateOf(fieldNames: string[], buttonFieldNames: string[]): string {
  const actions = [
    ...fieldNames.map((fieldName) => ({ _type: 'SetFieldValue', fieldName, value: `{{${fieldName}}}` })),
    ...buttonFieldNames.map((fieldName) => ({ _type: 'SetFieldValue', fieldName })),
  ]
  return JSON.stringify({ actions }, null, 2)
}

/**
 * Die Aktionen eines Aufrufs, wie das Backend sie an ABAS schickt: die Vorlage, jeder Platzhalter durch seinen Wert
 * ersetzt; null bei unlesbarer Vorlage oder fehlendem Wert. Nur zum Anzeigen der Anfrage, geschickt wird über den Key.
 */
export function abasInfosysActionsOf(abasInfosys: AbasInfosys, valueByPlaceholderName: Record<string, string>): AbasInfosysAction[] | null {
  let parsed: unknown
  try {
    parsed = JSON.parse(abasInfosys.requestBodyTemplate)
  } catch {
    return null
  }
  if (typeof parsed !== 'object' || parsed === null || !Array.isArray((parsed as { actions?: unknown }).actions)) {
    return null
  }
  const actions: AbasInfosysAction[] = []
  for (const raw of (parsed as { actions: unknown[] }).actions) {
    if (typeof raw !== 'object' || raw === null) {
      return null
    }
    const action: AbasInfosysAction = {}
    for (const [field, value] of Object.entries(raw as Record<string, unknown>)) {
      if (typeof value !== 'string') {
        action[field] = value
        continue
      }
      let missing = false
      action[field] = value.replace(PLACEHOLDER, (_match, name: string) => {
        const replacement = valueByPlaceholderName[name]
        if (replacement === undefined) {
          missing = true
          return ''
        }
        return replacement
      })
      if (missing) {
        return null
      }
    }
    actions.push(action)
  }
  return actions
}

/** Die Anfrage eines Aufrufs als Text für die Vorschau: POST …/infosys/data/<gruppe>/<name> */
export function abasInfosysRequestTextOf(abasInfosys: Pick<AbasInfosys, 'infosysGroup' | 'infosysName'>, baseUrl: string | null): string {
  return `POST ${baseUrl ?? '…'}/infosys/data/${abasInfosys.infosysGroup}/${abasInfosys.infosysName}`
}

// ===== Endpunkte des AbasInfosysAdminController: die hinterlegten Aufrufe =====

const ABAS_INFOSYS_ADMIN_PATH = '/api/rest/v1/admin/abas/infosys'

export function getAllAbasInfosys() {
  return request<AbasInfosys[]>('GET', ABAS_INFOSYS_ADMIN_PATH)
}

export function createAbasInfosys(newAbasInfosysData: AbasInfosys) {
  return request<AbasInfosys>('POST', ABAS_INFOSYS_ADMIN_PATH, newAbasInfosysData)
}

export function updateAbasInfosys(changedAbasInfosysData: AbasInfosys) {
  return request<AbasInfosys>('PUT', ABAS_INFOSYS_ADMIN_PATH, changedAbasInfosysData)
}

export function deleteAbasInfosys(key: string) {
  return request<AbasInfosys>('DELETE', ABAS_INFOSYS_ADMIN_PATH, { key })
}

/**
 * Einen hinterlegten Aufruf ausführen und die rohe Antwort holen (AbasInfosysRunRequest.java): der einzige Weg zu
 * einem Infosystem. 400 bei fehlendem Platzhalter oder wenn ABAS die Eingabe ablehnt, 404 ohne den Aufruf, 503 wenn
 * ABAS nicht antwortet.
 */
export function runAbasInfosys(abasInfosysKey: string, valueByPlaceholderName: Record<string, string>) {
  return request<unknown>('POST', `${ABAS_INFOSYS_ADMIN_PATH}/raw`, { abasInfosysKey, valueByPlaceholderName })
}

/** Die hinterlegten Aufrufe, einmal geladen; null solange sie laden, dazu der Fehler, wenn das Laden scheiterte */
export function useAbasInfosysList(): { abasInfosysList: AbasInfosys[] | null; errorMessage: string | null; reload: () => void } {
  const [abasInfosysList, setAbasInfosysList] = useState<AbasInfosys[] | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [reloadCount, setReloadCount] = useState(0)
  useEffect(() => {
    let active = true
    getAllAbasInfosys()
      .then((result) => {
        if (active) {
          setAbasInfosysList(result.data)
          setErrorMessage(null)
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setAbasInfosysList([])
          setErrorMessage(error instanceof RequestError ? error.message : i18n.t('common.unknownError'))
        }
      })
    return () => {
      active = false
    }
  }, [reloadCount])
  return { abasInfosysList, errorMessage, reload: () => setReloadCount((count) => count + 1) }
}
