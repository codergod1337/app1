import { request } from '../../api/client.ts'
import type { PackSymbol } from '../../components/PackSymbol.ts'

/** Key der Admin-Rolle, gleich wie im Backend (SecurityConfig: /api/rest/v1/admin/**) */
export const ADMIN_ACCESS_ROLE_KEY = 'ADMIN'

/** Verlaufsart der Badge. Wie jede Art aussieht, steht in basic.css (in jedem Theme gleich). */
export type BadgeGradient = 'FLAT' | 'LINEAR' | 'DIAGONAL' | 'BEVEL' | 'RADIAL' | 'METAL'

export const BADGE_GRADIENTS: { gradient: BadgeGradient; label: string }[] = [
  { gradient: 'FLAT', label: 'einfarbig' },
  { gradient: 'LINEAR', label: 'Verlauf oben → unten' },
  { gradient: 'DIAGONAL', label: 'Verlauf schräg' },
  { gradient: 'BEVEL', label: '3D gewölbt' },
  { gradient: 'RADIAL', label: 'von der Mitte nach außen' },
  { gradient: 'METAL', label: 'Metall' },
]

/** Alles, was die Badge einer AccessRole zum Anzeigen braucht. Farben immer Hex, null: Standard aus basic.css. */
export interface AccessRoleBadgeData {
  key: string
  /** mehrsprachiges JSON, z. B. {"de":"Verwalter"} */
  displayName: string
  /** mehrsprachiges JSON wie displayName, erscheint im Hoverlay */
  description: string | null
  badgeTextColor: string | null
  /** Schatten hinter der Schrift, für Kontrast auf jedem Hintergrund */
  badgeTextShadowColor: string | null
  badgeGradientStart: string | null
  badgeGradientEnd: string | null
  badgeBorderColor: string | null
  badgeShadowColor: string | null
  badgeGradient: BadgeGradient | null
  /** CSS-Klassen der Badge, z. B. ["screws","shine"] */
  badgeStyles: string[] | null
  symbol: PackSymbol | null
  /** Deckkraft in Prozent, 25 bis 99. null: volle Deckkraft */
  badgeOpacity: number | null
}

/** Eine AccessRole, wie sie das Backend liefert (AccessRole.java). */
export interface AccessRole extends AccessRoleBadgeData {
  /** z. B. ADMIN: nur A–Z, 0–9 und _, beginnt und endet mit einem Großbuchstaben */
  key: string
  /** true, wenn der key im Code direkt verwendet wird */
  system: boolean
  listingPosition: number
}

/** Daten für eine neue AccessRole: alle Felder, Pflicht sind key und displayName. */
export type NewAccessRoleData = AccessRole

// ===== Endpunkte des AccessRoleAdminController im Backend. Gelesen wird über die Stammdaten (useMasterData). =====

const ACCESS_ROLE_ADMIN_PATH = '/api/rest/v1/admin/accessrole'

export function createAccessRole(newAccessRoleData: NewAccessRoleData) {
  return request<AccessRole>('POST', ACCESS_ROLE_ADMIN_PATH, newAccessRoleData)
}

/** Alle Felder, der key bestimmt die Rolle und ändert sich nie. system darf nur von false auf true. */
export function updateAccessRole(changedAccessRoleData: AccessRole) {
  return request<AccessRole>('PUT', ACCESS_ROLE_ADMIN_PATH, changedAccessRoleData)
}

/**
 * Jeder key bekommt seine neue Position, z. B. { ADMIN: 1, FILE_READ: 2 }. Das Frontend zählt durch.
 * Zurück kommen alle Rollen in neuer Reihenfolge.
 */
export function changeAccessRolePositions(newPositionByKey: Record<string, number>) {
  return request<AccessRole[]>('POST', `${ACCESS_ROLE_ADMIN_PATH}/position`, newPositionByKey)
}

/** Nur per key. system-Rollen lehnt das Backend mit 409 ab. */
export function deleteAccessRole(key: string) {
  return request<AccessRole>('DELETE', ACCESS_ROLE_ADMIN_PATH, { key })
}
