import { useLocation } from 'react-router'

/**
 * Woher man kam: der Tab, den man verlassen hat. Wird als Router-State mitgegeben, nicht in der URL.
 * Solange er da ist, zeigt der header den Knopf „zurück zu <label>“. Tab-Links geben keinen mit.
 */
export interface Origin {
  /** z. B. /admin/users */
  path: string
  /** Name des Tabs für den Knopf, z. B. User */
  label: string
}

/** State beim Verlassen eines Tabs: navigate(to, { state: { origin } }). Tiefere Seiten reichen ihn weiter. */
export interface OriginState {
  origin: Origin
}

/** Der mitgegebene origin, null wenn keiner da ist (auf einem Tab oder nach direktem Aufruf der URL). */
export function useOrigin(): Origin | null {
  const state: unknown = useLocation().state
  if (typeof state !== 'object' || state === null || !('origin' in state)) {
    return null
  }
  const origin: unknown = state.origin
  if (
    typeof origin === 'object' &&
    origin !== null &&
    'path' in origin &&
    'label' in origin &&
    typeof origin.path === 'string' &&
    typeof origin.label === 'string'
  ) {
    return { path: origin.path, label: origin.label }
  }
  return null
}
