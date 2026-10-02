import type { HttpMethod, RequestMetrics } from './RequestMetrics.ts'
import { isKeepBodies, saveRequest } from './requestStore.ts'

/** Ergebnis eines erfolgreichen Requests: die Daten und die Messwerte. */
export interface RequestResult<T> {
  data: T
  metrics: RequestMetrics
}

/** Fehler eines Requests: Status 4xx/5xx, keine Verbindung oder unlesbare Antwort. Trägt die Messwerte mit. */
export class RequestError extends Error {
  readonly status: number | null
  readonly metrics: RequestMetrics

  constructor(message: string, metrics: RequestMetrics) {
    super(message)
    this.name = 'RequestError'
    this.status = metrics.status
    this.metrics = metrics
  }
}

let nextId = 1

/** Offene Pfade: Eine 401 dort ist eine echte Absage (z. B. falsches Passwort), kein abgelaufenes Token. */
const PUBLIC_PATH = '/api/rest/v1/public/'

/**
 * Wird bei einer 401 auf einem geschützten Pfad gerufen, z. B. weil das JWT abgelaufen ist oder auf der Sperrliste
 * steht. true: Die Sitzung wurde erneuert, der Request wird genau einmal wiederholt.
 * Setzt der SessionProvider. So kennt der Client das Login-Modul nicht.
 */
let unauthorizedHandler: (() => Promise<boolean>) | null = null

export function setUnauthorizedHandler(handler: (() => Promise<boolean>) | null) {
  unauthorizedHandler = handler
}

/**
 * Der einzige Weg ins Backend. Schickt den Body als JSON, misst Dauer und Bytes und trägt jeden Request in den
 * requestStore ein. Wirft RequestError bei jedem Fehler. Bei einer 401 auf einem geschützten Pfad wird die Sitzung
 * erneuert und der Request einmal wiederholt.
 */
export async function request<T>(method: HttpMethod, path: string, body?: unknown): Promise<RequestResult<T>> {
  try {
    return await sendRequest<T>(method, path, body)
  } catch (error) {
    if (
      error instanceof RequestError &&
      error.status === 401 &&
      !path.startsWith(PUBLIC_PATH) &&
      unauthorizedHandler !== null &&
      (await unauthorizedHandler())
    ) {
      return sendRequest<T>(method, path, body)
    }
    throw error
  }
}

/** Ein einzelner Request ohne Wiederholung */
async function sendRequest<T>(method: HttpMethod, path: string, body?: unknown): Promise<RequestResult<T>> {
  const requestText = body === undefined ? undefined : JSON.stringify(body)
  const keepBodies = isKeepBodies()

  let metrics: RequestMetrics = {
    id: nextId++,
    method,
    path,
    state: 'pending',
    status: null,
    startedAt: Date.now(),
    durationMs: null,
    bytesSent: requestText === undefined ? 0 : new TextEncoder().encode(requestText).byteLength,
    bytesReceived: null,
    requestBody: keepBodies ? body : undefined,
  }
  saveRequest(metrics)
  const start = performance.now()

  let response: Response
  let responseBuffer: ArrayBuffer
  try {
    response = await fetch(path, {
      method,
      headers: requestText === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: requestText,
      credentials: 'same-origin',
    })
    responseBuffer = await response.arrayBuffer()
  } catch {
    // Keine Antwort vom Server, z. B. Backend nicht gestartet
    metrics = {
      ...metrics,
      state: 'error',
      durationMs: performance.now() - start,
      errorMessage: 'keine Verbindung zum Server',
    }
    saveRequest(metrics)
    throw new RequestError(metrics.errorMessage ?? '', metrics)
  }

  const responseText = new TextDecoder().decode(responseBuffer)
  let data: unknown = undefined
  if (responseText !== '') {
    try {
      data = JSON.parse(responseText)
    } catch {
      // Keine JSON-Antwort: als Text weitergeben
      data = responseText
    }
  }

  metrics = {
    ...metrics,
    state: response.ok ? 'done' : 'error',
    status: response.status,
    durationMs: performance.now() - start,
    bytesReceived: responseBuffer.byteLength,
    responseBody: keepBodies ? data : undefined,
    errorMessage: response.ok ? undefined : readErrorMessage(data, response),
  }
  saveRequest(metrics)

  if (!response.ok) {
    throw new RequestError(metrics.errorMessage ?? '', metrics)
  }
  return { data: data as T, metrics }
}

/** Meldung aus der Fehlerantwort von Spring (message, sonst error), sonst der Statustext. */
function readErrorMessage(data: unknown, response: Response): string {
  if (typeof data === 'object' && data !== null) {
    const errorBody = data as { message?: unknown; error?: unknown }
    if (typeof errorBody.message === 'string' && errorBody.message !== '') {
      return errorBody.message
    }
    if (typeof errorBody.error === 'string' && errorBody.error !== '') {
      return errorBody.error
    }
  }
  return response.statusText || `Fehler ${response.status}`
}
