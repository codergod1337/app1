import { request } from '../../api/client.ts'

/** Eine AR, die einem User einzeln zugewiesen ist (AccessRoleUsersAssignment.java). */
export interface AccessRoleUsersAssignment {
  id: number
  usersGuid: string
  accessRoleKey: string
  /** Zeitpunkt nach ISO-8601 */
  assignedAt: string
}

// ===== Endpunkte des AccessRoleUsersAssignmentAdminController =====

const ACCESS_ROLE_USERS_ASSIGNMENT_ADMIN_PATH = '/api/rest/v1/admin/accessroleusersassignment'

/** Alle Zuordnungen, für die Userliste */
export function getAllAccessRoleUsersAssignments() {
  return request<AccessRoleUsersAssignment[]>('GET', ACCESS_ROLE_USERS_ASSIGNMENT_ADMIN_PATH)
}

/** Die AR-Keys eines Users */
export function getAccessRoleKeysByUsersGuid(usersGuid: string) {
  return request<string[]>('GET', `${ACCESS_ROLE_USERS_ASSIGNMENT_ADMIN_PATH}/${usersGuid}`)
}

/** Setzt die AR eines Users auf genau diese Liste, eine leere entzieht alle. Zurück kommen die neuen Keys. */
export function changeAccessRoleUsersAssignments(usersGuid: string, accessRoleKeys: string[]) {
  return request<string[]>('PUT', ACCESS_ROLE_USERS_ASSIGNMENT_ADMIN_PATH, { usersGuid, accessRoleKeys })
}
