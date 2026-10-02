import { request } from '../../api/client.ts'
import { sha256Hex } from '../../components/sha256Hex.ts'

// Passwörter kommen hier im Klartext an und gehen nur als SHA-256 hinaus, so kann kein Aufrufer das Hashen vergessen.

// ===== Endpunkt des UsersCredentialsController (jeder bei sich selbst) =====

/** Der User ändert sein eigenes Passwort, das alte muss stimmen. */
export async function changeUsersCredentialsPassword(usersGuid: string, oldPassword: string, newPassword: string) {
  return request<void>('PUT', '/api/rest/v1/userscredentials/password', {
    usersGuid,
    oldPassword: await sha256Hex(oldPassword),
    newPassword: await sha256Hex(newPassword),
  })
}

// ===== Endpunkt des UsersCredentialsAdminController =====

/** Admin setzt das Passwort eines Users oder setzt es zurück, das alte ist egal. */
export async function setUsersCredentialsPassword(usersGuid: string, newPassword: string) {
  return request<void>('PUT', '/api/rest/v1/admin/userscredentials/password', {
    usersGuid,
    newPassword: await sha256Hex(newPassword),
  })
}
