import type { RequestMetrics } from './RequestMetrics.ts'

/** Nur die letzten Requests behalten, sonst läuft der Speicher voll. */
const MAX_ENTRIES = 100

let entries: RequestMetrics[] = []
let keepBodies = false
const listeners = new Set<() => void>()

/** Inhalte (requestBody, responseBody) speichern oder nur die Größen. Standard: nur die Größen. */
export function setKeepBodies(keep: boolean): void {
  keepBodies = keep
}

export function isKeepBodies(): boolean {
  return keepBodies
}

/** Alle gemerkten Requests, der neueste zuletzt. Bei jeder Änderung ein neues Array, damit React sie erkennt. */
export function getRequests(): RequestMetrics[] {
  return entries
}

/** Meldet sich für Änderungen an. Gibt die Funktion zum Abmelden zurück. */
export function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** Neuer Request oder neuer Stand eines vorhandenen (gleiche id). */
export function saveRequest(metrics: RequestMetrics): void {
  const exists = entries.some((entry) => entry.id === metrics.id)
  const updated = exists
    ? entries.map((entry) => (entry.id === metrics.id ? metrics : entry))
    : [...entries, metrics]
  entries = updated.slice(-MAX_ENTRIES)
  listeners.forEach((listener) => listener())
}
