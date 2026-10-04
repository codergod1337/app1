import type { EntityState } from '../entitystate/EntityState.ts'
import { translate } from '../../components/multilingual.ts'
import { GRANT_ENTITY_STATE_ACTIVE } from './Grant.ts'

/** Der Anzeigename eines Freigabe-Status aus den Stammdaten, sonst der key */
export function grantEntityStateDisplayName(entityStates: readonly EntityState[] | null, status: string): string {
  const entityState = entityStates?.find((candidate) => candidate.key === status)
  const name = entityState ? translate(entityState.displayName) : ''
  return name !== '' ? name : status
}

/** Ein Zeitpunkt in der Sprache der Oberfläche, null als „unbegrenzt“ (der Aufrufer gibt den Text) */
export function grantDateTimeOrUnlimited(iso: string | null, language: string, unlimited: string): string {
  return iso === null ? unlimited : new Date(iso).toLocaleString(language)
}

/** Wirksam heißt Status ACTIVE und Ablauf noch nicht erreicht: dieselbe Regel wie im Backend */
export function isGrantInForce(grant: { status: string; validUntil: string | null }): boolean {
  return (
    grant.status === GRANT_ENTITY_STATE_ACTIVE && (grant.validUntil === null || new Date(grant.validUntil) > new Date())
  )
}

/** datetime-local (Ortszeit ohne Zone) nach ISO 8601 in UTC, leer heißt unbegrenzt */
export function validUntilLocalInputToIso(local: string): string | null {
  if (local === '') {
    return null
  }
  const date = new Date(local)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}
