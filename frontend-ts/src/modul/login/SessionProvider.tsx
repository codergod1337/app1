import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { setUnauthorizedHandler } from '../../api/client.ts'
import { ANONYMOUS_SESSION, getSessionInfo, logoutSession, refreshSession, type SessionInfo } from './Session.ts'
import { SessionContext, type SessionState } from './sessionContext.ts'

/** Name des Schlosses: Über alle Tabs dieser Seite erneuert immer nur einer gleichzeitig. */
const REFRESH_LOCK = 'app1-session-refresh'

/** Frühestens nach 5 Sekunden erneuern, auch wenn das Token schon fast abgelaufen ist */
const MIN_REFRESH_DELAY_MS = 5000

/**
 * Bringt die Sitzung auf den neuesten Stand, über alle Tabs immer nur einer gleichzeitig (Web Locks API des Browsers).
 *
 * Zuerst wird nachgesehen: Hat ein anderer Tab schon erneuert, liegen die neuen Cookies bereits im Browser, und die
 * Sitzung zeigt einen anderen Ablauf als knownTokenExpiresAt. Dann bleibt es dabei. Sonst ein Refresh, der nie
 * wiederholt wird: Zwei Refreshs mit demselben Token sähen für das Backend wie Diebstahl aus.
 */
async function refreshSessionInfo(knownTokenExpiresAt: string | null): Promise<SessionInfo> {
  const refreshInThisTab = async (): Promise<SessionInfo> => {
    const currentSessionInfo = (await getSessionInfo()).data
    if (currentSessionInfo.loggedIn && currentSessionInfo.tokenExpiresAt !== knownTokenExpiresAt) {
      return currentSessionInfo
    }
    try {
      await refreshSession()
    } catch {
      // kein, abgelaufenes oder widerrufenes Refresh-Cookie: nicht angemeldet
      return ANONYMOUS_SESSION
    }
    return (await getSessionInfo()).data
  }
  // Die Web Locks API gibt es nur auf https und localhost, sonst ohne Schloss
  return navigator.locks ? navigator.locks.request(REFRESH_LOCK, refreshInThisTab) : refreshInThisTab()
}

/** Wann der nächste Refresh fällig ist: bei 2/3 der Restlaufzeit des Tokens. null ohne Anmeldung */
function nextRefreshTime(sessionInfo: SessionInfo): number | null {
  if (!sessionInfo.loggedIn || sessionInfo.tokenExpiresAt === null) {
    return null
  }
  const remainingMs = new Date(sessionInfo.tokenExpiresAt).getTime() - Date.now()
  return Date.now() + Math.max(MIN_REFRESH_DELAY_MS, (remainingMs * 2) / 3)
}

/**
 * Hält die Sitzung für alle Module. Beim Start: Sitzung laden, ohne Anmeldung genau ein Refresh-Versuch, weil das
 * Refresh-Cookie noch leben kann. Danach erneuert er rechtzeitig vor Ablauf des Tokens, und bei einer 401 aus einem
 * Request (z. B. Sperrliste nach geänderten Rechten) einmal sofort.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  // null, solange die Sitzung beim Start lädt
  const [session, setSession] = useState<{ sessionInfo: SessionInfo; nextRefreshAt: number | null } | null>(null)
  const knownTokenExpiresAt = session?.sessionInfo.tokenExpiresAt ?? null
  const nextRefreshAt = session?.nextRefreshAt ?? null
  // Für den 401-Handler, der unabhängig vom Rendern gerufen wird
  const knownTokenExpiresAtRef = useRef<string | null>(null)

  const applySessionInfo = useCallback((sessionInfo: SessionInfo) => {
    setSession({ sessionInfo, nextRefreshAt: nextRefreshTime(sessionInfo) })
  }, [])

  useEffect(() => {
    knownTokenExpiresAtRef.current = knownTokenExpiresAt
  }, [knownTokenExpiresAt])

  // Beim Start
  useEffect(() => {
    let active = true
    refreshSessionInfo(null)
      .catch(() => ANONYMOUS_SESSION)
      .then((sessionInfo) => {
        if (active) {
          applySessionInfo(sessionInfo)
        }
      })
    return () => {
      active = false
    }
  }, [applySessionInfo])

  // Rechtzeitig vor Ablauf des Tokens erneuern
  useEffect(() => {
    if (nextRefreshAt === null) {
      return
    }
    const timer = setTimeout(() => {
      refreshSessionInfo(knownTokenExpiresAt)
        .catch(() => ANONYMOUS_SESSION)
        .then(applySessionInfo)
    }, nextRefreshAt - Date.now())
    return () => clearTimeout(timer)
  }, [nextRefreshAt, knownTokenExpiresAt, applySessionInfo])

  // Bei einer 401 aus einem Request einmal erneuern, der Client wiederholt den Request dann
  useEffect(() => {
    setUnauthorizedHandler(async () => {
      const sessionInfo = await refreshSessionInfo(knownTokenExpiresAtRef.current).catch(() => ANONYMOUS_SESSION)
      applySessionInfo(sessionInfo)
      return sessionInfo.loggedIn
    })
    return () => setUnauthorizedHandler(null)
  }, [applySessionInfo])

  const reloadSession = useCallback(async () => {
    applySessionInfo((await getSessionInfo()).data)
  }, [applySessionInfo])

  const logout = useCallback(async () => {
    try {
      await logoutSession()
    } finally {
      applySessionInfo(ANONYMOUS_SESSION)
    }
  }, [applySessionInfo])

  const sessionState = useMemo<SessionState>(() => {
    const sessionInfo = session?.sessionInfo ?? null
    return {
      sessionInfo,
      loggedIn: sessionInfo?.loggedIn ?? false,
      nextRefreshAt,
      hasAccessRole: (accessRoleKey) => sessionInfo?.accessRoleKeys.includes(accessRoleKey) ?? false,
      reloadSession,
      logout,
    }
  }, [session, nextRefreshAt, reloadSession, logout])

  return <SessionContext.Provider value={sessionState}>{children}</SessionContext.Provider>
}
