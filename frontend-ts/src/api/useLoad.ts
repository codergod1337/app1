import { useCallback, useEffect, useState } from 'react'
import { RequestError, type RequestResult } from './client.ts'

export interface LoadState<T> {
  /** null solange das erste Mal geladen wird oder wenn ein Fehler kam */
  data: T | null
  /** null ohne Fehler */
  errorMessage: string | null
  /** lädt neu, z. B. nach dem Löschen eines Eintrags. Die alten Daten bleiben bis dahin stehen. */
  reload: () => void
}

/**
 * Lädt beim ersten Anzeigen und nach reload(), z. B. useLoad(getAllUsers).
 * Verschwindet die Komponente vor der Antwort, wird das Ergebnis verworfen.
 */
export function useLoad<T>(load: () => Promise<RequestResult<T>>): LoadState<T> {
  const [state, setState] = useState<{ data: T | null; errorMessage: string | null }>({
    data: null,
    errorMessage: null,
  })
  const [loadCount, setLoadCount] = useState(0)
  const reload = useCallback(() => setLoadCount((count) => count + 1), [])

  useEffect(() => {
    let active = true
    load()
      .then((result) => {
        if (active) {
          setState({ data: result.data, errorMessage: null })
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setState({
            data: null,
            errorMessage: error instanceof RequestError ? error.message : 'Unbekannter Fehler',
          })
        }
      })
    return () => {
      active = false
    }
  }, [load, loadCount])

  return { ...state, reload }
}
