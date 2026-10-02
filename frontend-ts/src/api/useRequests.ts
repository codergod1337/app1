import { useSyncExternalStore } from 'react'
import type { RequestMetrics } from './RequestMetrics.ts'
import { getRequests, subscribe } from './requestStore.ts'

/** Alle gemerkten Requests. Die Komponente rendert neu, sobald sich einer ändert. */
export function useRequests(): RequestMetrics[] {
  return useSyncExternalStore(subscribe, getRequests)
}

/** true, solange irgendein Request unterwegs ist, z. B. für einen globalen Ladespinner. */
export function useAnyRequestPending(): boolean {
  return useSyncExternalStore(subscribe, () => getRequests().some((entry) => entry.state === 'pending'))
}
