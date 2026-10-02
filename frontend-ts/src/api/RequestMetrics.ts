export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export type RequestState = 'pending' | 'done' | 'error'

/** Messwerte eines Backend-Requests. Der zentrale Client legt für jeden Request genau einen Eintrag an. */
export interface RequestMetrics {
  id: number
  method: HttpMethod
  /** z. B. /api/rest/v1/users */
  path: string
  state: RequestState
  /** HTTP-Status, null solange der Request unterwegs ist oder wenn keine Antwort kam */
  status: number | null
  /** Zeitpunkt des Absendens (Date.now()) */
  startedAt: number
  /** vom Absenden bis die Antwort fertig verarbeitet ist, null solange unterwegs */
  durationMs: number | null
  /** Größe des gesendeten Bodys in Bytes, 0 ohne Body */
  bytesSent: number
  /** Größe der empfangenen Antwort in Bytes, null solange unterwegs */
  bytesReceived: number | null
  /** was geschickt wurde, nur wenn das Speichern der Inhalte eingeschaltet ist */
  requestBody?: unknown
  /** was zurückkam, nur wenn das Speichern der Inhalte eingeschaltet ist */
  responseBody?: unknown
  /** lesbare Fehlermeldung, nur bei state 'error' */
  errorMessage?: string
}
