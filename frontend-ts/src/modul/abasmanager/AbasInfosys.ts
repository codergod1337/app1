import { request } from '../../api/client.ts'

/**
 * Eine Aktion im Body einer Infosystem-Anfrage, ein freies Objekt wie ABAS es erwartet: _type zuerst, ohne Angabe
 * SetFieldValue, dazu z. B. fieldName und value, bei Tabellen rowSpec. Seit 2026-10-11 baut sie nur noch die Vorlage
 * eines hinterlegten Aufrufs (modul/infosys/AbasInfosys.ts, abasInfosysActionsOf), zum Anzeigen der Anfrage.
 */
export type AbasInfosysAction = Record<string, unknown>

/** Ein Infosystem aus der Liste, die ABAS liefert: Gruppe und Name, z. B. fe und PRODLIST */
export interface AbasInfosysEntry {
  group: string
  name: string
}

/** Ein Kopffeld eines Infosystems aus seinem Meta: name ist der fieldName einer Aktion, baseType BUTTON ist ein Knopf */
export interface AbasInfosysHeadField {
  name: string
  description: string
  baseType: string
}

// ===== Endpunkte des AbasInfosysAdminController, die rohen Abfragen bei ABAS fürs Anlegen eines Aufrufs =====
// Die hinterlegten Aufrufe und ihr Lauf: modul/infosys/AbasInfosys.ts. Jede Infosystem-Anfrage geht über einen Aufruf.

const ABAS_INFOSYS_ADMIN_PATH = '/api/rest/v1/admin/abas/infosys'

/** Alle Infosysteme bei ABAS, roh; lesbar über abasInfosysEntriesOf */
export function getAbasInfosysList() {
  return request<unknown>('GET', `${ABAS_INFOSYS_ADMIN_PATH}/list`)
}

/** Die Felder eines Infosystems, roh; lesbar über abasInfosysHeadFieldsOf. 404 bei unbekanntem Infosystem */
export function fetchAbasInfosysMeta(infosysGroup: string, infosysName: string) {
  return request<unknown>('POST', `${ABAS_INFOSYS_ADMIN_PATH}/meta`, { infosysGroup, infosysName })
}

// ===== Lesen der rohen Antworten =====

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/** content.data des Antwortbaums, oder null, wenn die Form nicht stimmt */
function dataOf(raw: unknown): Record<string, unknown> | null {
  if (!isRecord(raw) || !isRecord(raw.content) || !isRecord(raw.content.data)) {
    return null
  }
  return raw.content.data
}

/**
 * Eine Sammlung in beiden Antwortformen von ABAS: als Liste (application/json) oder als Map nach Name beziehungsweise
 * Link-Relation (application/abas.objects+json, die Form des Backends). Aus der Map werden die Werte genommen, Listen
 * darin ausgebreitet.
 */
function collectionOf(value: unknown): unknown[] {
  if (Array.isArray(value)) {
    return value
  }
  if (!isRecord(value)) {
    return []
  }
  const members: unknown[] = []
  for (const member of Object.values(value)) {
    if (Array.isArray(member)) {
      members.push(...member)
    } else {
      members.push(member)
    }
  }
  return members
}

/** Die Einträge der Liste: content.data.links[].description als „gruppe/NAME“; was nicht so aussieht, wird überlesen */
export function abasInfosysEntriesOf(rawList: unknown): AbasInfosysEntry[] {
  const data = dataOf(rawList)
  if (data === null) {
    return []
  }
  const entries: AbasInfosysEntry[] = []
  for (const link of collectionOf(data.links)) {
    if (!isRecord(link) || typeof link.description !== 'string') {
      continue
    }
    const slash = link.description.indexOf('/')
    if (slash <= 0 || slash === link.description.length - 1) {
      continue
    }
    entries.push({ group: link.description.slice(0, slash), name: link.description.slice(slash + 1) })
  }
  return entries
}

/** Die Kopffelder aus dem Meta: content.data.fields[] ohne tableColumn, mit name, description und type.baseType */
export function abasInfosysHeadFieldsOf(rawMeta: unknown): AbasInfosysHeadField[] {
  const data = dataOf(rawMeta)
  if (data === null) {
    return []
  }
  const headFields: AbasInfosysHeadField[] = []
  for (const field of collectionOf(data.fields)) {
    if (!isRecord(field) || typeof field.name !== 'string' || field.tableColumn === true) {
      continue
    }
    const baseType = isRecord(field.type) && typeof field.type.baseType === 'string' ? field.type.baseType : ''
    headFields.push({
      name: field.name,
      description: typeof field.description === 'string' ? field.description : '',
      baseType,
    })
  }
  return headFields
}
