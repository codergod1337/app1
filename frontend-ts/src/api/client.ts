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

export interface RequestOptions {
  /**
   * Der Request überlebt das Verlassen der Seite (Tab zu, Navigation weg), für einen letzten Wert beim Abschied. Nur für
   * kleine Bodys, der Browser erlaubt dafür etwa 64 KB.
   */
  keepalive?: boolean
}

/**
 * Der Weg ins Backend für alles außer Dateien. Schickt den Body als JSON, misst Dauer und Bytes und trägt jeden
 * Request in den requestStore ein. Wirft RequestError bei jedem Fehler. Bei einer 401 auf einem geschützten Pfad wird
 * die Sitzung erneuert und der Request einmal wiederholt.
 */
export function request<T>(
  method: HttpMethod,
  path: string,
  body?: unknown,
  options?: RequestOptions,
): Promise<RequestResult<T>> {
  return withSessionRetry(path, () => sendRequest<T>(method, path, body, options))
}

/** Meldet während einer Übertragung, wie viele Bytes schon beim Server sind */
export type UploadProgress = (sentBytes: number, totalBytes: number) => void

/**
 * Der Weg für Dateien: Multipart als FormData, der Browser setzt den Content-Type mit der Grenze selbst. Sonst wie
 * request: Messwerte, requestStore, RequestError, eine Wiederholung nach erneuerter Sitzung. Über XMLHttpRequest
 * statt fetch, weil nur das die gesendeten Bytes meldet (onProgress). Nach 100 % arbeitet der Server noch (Tika,
 * SFTP, Solr), die Antwort kommt danach.
 */
export function upload<T>(path: string, formData: FormData, onProgress?: UploadProgress): Promise<RequestResult<T>> {
  return withSessionRetry(path, () => sendUpload<T>(path, formData, onProgress))
}

/** Bei einer 401 auf einem geschützten Pfad die Sitzung erneuern und den Request genau einmal wiederholen */
async function withSessionRetry<T>(path: string, send: () => Promise<RequestResult<T>>): Promise<RequestResult<T>> {
  try {
    return await send()
  } catch (error) {
    if (
      error instanceof RequestError &&
      error.status === 401 &&
      !path.startsWith(PUBLIC_PATH) &&
      unauthorizedHandler !== null &&
      (await unauthorizedHandler())
    ) {
      return send()
    }
    throw error
  }
}

/** Ein einzelner Request ohne Wiederholung, der Body als JSON. */
async function sendRequest<T>(
  method: HttpMethod,
  path: string,
  body?: unknown,
  options?: RequestOptions,
): Promise<RequestResult<T>> {
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
      keepalive: options?.keepalive === true,
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
  const data = parseResponse(responseText)
  metrics = {
    ...metrics,
    state: response.ok ? 'done' : 'error',
    status: response.status,
    durationMs: performance.now() - start,
    bytesReceived: responseBuffer.byteLength,
    responseBody: keepBodies ? data : undefined,
    errorMessage: response.ok ? undefined : readErrorMessage(data, response.status, response.statusText),
  }
  saveRequest(metrics)

  if (!response.ok) {
    throw new RequestError(metrics.errorMessage ?? '', metrics)
  }
  return { data: data as T, metrics }
}

/** Ein einzelner Upload ohne Wiederholung, Multipart über XMLHttpRequest, damit der Fortschritt zu haben ist. */
function sendUpload<T>(path: string, formData: FormData, onProgress?: UploadProgress): Promise<RequestResult<T>> {
  const keepBodies = isKeepBodies()
  let metrics: RequestMetrics = {
    id: nextId++,
    method: 'POST',
    path,
    state: 'pending',
    status: null,
    startedAt: Date.now(),
    durationMs: null,
    bytesSent: formDataBytes(formData),
    bytesReceived: null,
    requestBody: keepBodies ? formDataSummary(formData) : undefined,
  }
  saveRequest(metrics)
  const start = performance.now()

  return new Promise<RequestResult<T>>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', path)
    xhr.responseType = 'text'
    if (onProgress) {
      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable) {
          onProgress(event.loaded, event.total)
        }
      }
    }
    xhr.onload = () => {
      const responseText = xhr.responseText
      const data = parseResponse(responseText)
      const ok = xhr.status >= 200 && xhr.status < 300
      metrics = {
        ...metrics,
        state: ok ? 'done' : 'error',
        status: xhr.status,
        durationMs: performance.now() - start,
        bytesReceived: new TextEncoder().encode(responseText).byteLength,
        responseBody: keepBodies ? data : undefined,
        errorMessage: ok ? undefined : readErrorMessage(data, xhr.status, xhr.statusText),
      }
      saveRequest(metrics)
      if (ok) {
        resolve({ data: data as T, metrics })
      } else {
        reject(new RequestError(metrics.errorMessage ?? '', metrics))
      }
    }
    xhr.onerror = () => {
      metrics = {
        ...metrics,
        state: 'error',
        durationMs: performance.now() - start,
        errorMessage: 'keine Verbindung zum Server',
      }
      saveRequest(metrics)
      reject(new RequestError(metrics.errorMessage ?? '', metrics))
    }
    xhr.send(formData)
  })
}

/** JSON, sonst der Text, bei leerer Antwort undefined */
function parseResponse(responseText: string): unknown {
  if (responseText === '') {
    return undefined
  }
  try {
    return JSON.parse(responseText)
  } catch {
    return responseText
  }
}

/** Die Bytes eines Multipart-Bodys ohne die Grenzen: Dateien nach Größe, Texte nach Länge */
function formDataBytes(formData: FormData): number {
  let bytes = 0
  for (const [, value] of formData) {
    bytes += value instanceof File ? value.size : new TextEncoder().encode(value).byteLength
  }
  return bytes
}

/** Fürs Protokoll: je Teil der Name, bei einer Datei ihr Name und ihre Größe, nie ihr Inhalt */
function formDataSummary(formData: FormData): unknown {
  return Array.from(formData, ([name, value]) =>
    value instanceof File ? { name, file: value.name, size: value.size } : { name, text: value },
  )
}

/** Meldung aus der Fehlerantwort von Spring (message, sonst error), sonst der Statustext. */
function readErrorMessage(data: unknown, status: number, statusText: string): string {
  if (typeof data === 'object' && data !== null) {
    const errorBody = data as { message?: unknown; error?: unknown }
    if (typeof errorBody.message === 'string' && errorBody.message !== '') {
      return errorBody.message
    }
    if (typeof errorBody.error === 'string' && errorBody.error !== '') {
      return errorBody.error
    }
  }
  return statusText || `Fehler ${status}`
}
