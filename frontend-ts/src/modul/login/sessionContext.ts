import { createContext, useContext } from 'react'
import type { SessionInfo } from './Session.ts'

/** Was jedes Modul über die Sitzung erfährt: useSession() */
export interface SessionState {
  /** null, solange die Sitzung beim Start lädt */
  sessionInfo: SessionInfo | null
  loggedIn: boolean
  /** Zeitpunkt (ms) des nächsten Refresh, für den Countdown im Header. null: keiner geplant */
  nextRefreshAt: number | null
  hasAccessRole: (accessRoleKey: string) => boolean
  /** Sitzung neu laden, z. B. direkt nach dem Login */
  reloadSession: () => Promise<void>
  logout: () => Promise<void>
}

export const SessionContext = createContext<SessionState | null>(null)

/** Die Sitzung für alle Module. Nur innerhalb des SessionProvider. */
export function useSession(): SessionState {
  const sessionState = useContext(SessionContext)
  if (sessionState === null) {
    throw new Error('useSession nur innerhalb des SessionProvider')
  }
  return sessionState
}
