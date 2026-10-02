import { request } from '../../api/client.ts'

/** Die ARC (Position) eines Users, höchstens eine pro User (AccessRoleCollectionUsersAssignment.java). */
export interface AccessRoleCollectionUsersAssignment {
  usersGuid: string
  accessRoleCollectionKey: string
  /** Zeitpunkt nach ISO-8601, beginnt bei einer anderen ARC neu */
  assignedAt: string
}

// ===== Endpunkte des AccessRoleCollectionUsersAssignmentAdminController =====

const ACCESS_ROLE_COLLECTION_USERS_ASSIGNMENT_ADMIN_PATH = '/api/rest/v1/admin/accessrolecollectionusersassignment'

/** Alle Zuordnungen, für die Matrix */
export function getAllAccessRoleCollectionUsersAssignments() {
  return request<AccessRoleCollectionUsersAssignment[]>('GET', ACCESS_ROLE_COLLECTION_USERS_ASSIGNMENT_ADMIN_PATH)
}

/** Setzt die ARC eines Users, eine vorhandene wird ersetzt. null entzieht sie, dann kommt auch null zurück. */
export function changeAccessRoleCollectionUsersAssignment(usersGuid: string, accessRoleCollectionKey: string | null) {
  return request<AccessRoleCollectionUsersAssignment | null>('PUT', ACCESS_ROLE_COLLECTION_USERS_ASSIGNMENT_ADMIN_PATH, {
    usersGuid,
    accessRoleCollectionKey,
  })
}
