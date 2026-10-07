import { request } from '../../api/client.ts'

/** Wofür eine Vorlage da ist (GraphMailTemplateType.java). Je Typ gibt es genau eine Vorlage. */
export type GraphMailTemplateType = 'LOGIN_2FA'

/** Eine Mailvorlage (GraphMailTemplate.java). Die mehrsprachigen Felder sind JSON wie {"de":"…","en":"…","bg":"…"}. */
export interface GraphMailTemplate {
  /** normale Key-Regel, z. B. LOGIN_2FA, ändert sich nie */
  key: string
  /** wird beim Anlegen gesetzt, ändert sich nie */
  type: GraphMailTemplateType
  displayName: string
  description: string
  /** der Betreff je Sprache, reiner Text mit Platzhaltern {gruppe.name} */
  subject: string
  /** der Text je Sprache als HTML mit Platzhaltern {gruppe.name} */
  htmlBody: string
  listingPosition: number
  createdAt?: string
  updatedAt?: string
}

/** Eine Platzhaltergruppe, wie der Editor sie zeigt: Key und die Namen hinter dem Punkt (GraphMailPlaceholderGroupForClient.java) */
export interface GraphMailPlaceholderGroupForClient {
  groupKey: string
  placeholderNames: string[]
}

/** Ein Vorlagentyp mit seinen Platzhaltergruppen (GraphMailTemplateTypeForClient.java) */
export interface GraphMailTemplateTypeForClient {
  type: GraphMailTemplateType
  placeholderGroups: GraphMailPlaceholderGroupForClient[]
}

/** Das Muster: Betreff und Text in einer Sprache mit Beispielwerten (GraphMailTemplatePreview.java) */
export interface GraphMailTemplatePreview {
  subject: string
  htmlBody: string
}

/** Was der Preview-Endpunkt braucht: Typ, Sprache und die Texte, so wie sie gerade im Formular stehen */
export interface GraphMailTemplatePreviewRequest {
  type: GraphMailTemplateType
  language: string
  subject: string
  htmlBody: string
}

// ===== Endpunkte des GraphMailTemplateAdminController =====

const GRAPH_MAIL_TEMPLATE_ADMIN_PATH = '/api/rest/v1/admin/graph/mailtemplate'

export function getAllGraphMailTemplates() {
  return request<GraphMailTemplate[]>('GET', GRAPH_MAIL_TEMPLATE_ADMIN_PATH)
}

export function createGraphMailTemplate(newGraphMailTemplateData: GraphMailTemplate) {
  return request<GraphMailTemplate>('POST', GRAPH_MAIL_TEMPLATE_ADMIN_PATH, newGraphMailTemplateData)
}

export function updateGraphMailTemplate(changedGraphMailTemplateData: GraphMailTemplate) {
  return request<GraphMailTemplate>('PUT', GRAPH_MAIL_TEMPLATE_ADMIN_PATH, changedGraphMailTemplateData)
}

/** Alle Positionen auf einmal: {key: position}. Zurück kommen alle Vorlagen in neuer Reihenfolge. */
export function changeGraphMailTemplatePositions(newPositionByKey: Record<string, number>) {
  return request<GraphMailTemplate[]>('POST', `${GRAPH_MAIL_TEMPLATE_ADMIN_PATH}/position`, newPositionByKey)
}

export function deleteGraphMailTemplate(key: string) {
  return request<GraphMailTemplate>('DELETE', GRAPH_MAIL_TEMPLATE_ADMIN_PATH, { key })
}

/** Das Muster zu den Texten im Formular. Nichts wird gespeichert oder verschickt. */
export function renderGraphMailTemplatePreview(previewRequest: GraphMailTemplatePreviewRequest) {
  return request<GraphMailTemplatePreview>('POST', `${GRAPH_MAIL_TEMPLATE_ADMIN_PATH}/preview`, previewRequest)
}

// ===== Endpunkt des GraphMailTemplateTypeAdminController =====

const GRAPH_MAIL_TEMPLATE_TYPE_ADMIN_PATH = '/api/rest/v1/admin/graph/mailtemplatetype'

export function getAllGraphMailTemplateTypes() {
  return request<GraphMailTemplateTypeForClient[]>('GET', GRAPH_MAIL_TEMPLATE_TYPE_ADMIN_PATH)
}
