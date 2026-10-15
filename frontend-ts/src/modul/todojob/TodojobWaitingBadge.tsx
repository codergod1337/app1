import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useSession } from '../login/sessionContext.ts'
import { getTodojobWaitingCounts, type TodojobWaitingCounts } from './Todojob.ts'

/** So oft fragt der header nach, wie viele Jobs warten */
const POLL_INTERVAL_MS = 120_000

/**
 * Die Zahl im header neben dem Benutzermenü (User 2026-10-16): bei wie vielen Jobs der Ball beim User liegt, als
 * Servant und als Owner. Nur wenn etwas wartet; der Klick führt zum Schreibtisch im Profil.
 */
export function TodojobWaitingBadge() {
  const { t } = useTranslation()
  const { sessionInfo } = useSession()
  const [counts, setCounts] = useState<TodojobWaitingCounts | null>(null)
  // empfangen darf jeder Angemeldete (User 2026-10-16)
  const allowed = sessionInfo?.loggedIn === true

  useEffect(() => {
    if (!allowed) {
      return
    }
    let active = true
    function poll() {
      getTodojobWaitingCounts()
        .then(({ data }) => {
          if (active) {
            setCounts(data)
          }
        })
        .catch(() => {
          // ohne Antwort bleibt die alte Zahl stehen
        })
    }
    poll()
    const timer = window.setInterval(poll, POLL_INTERVAL_MS)
    return () => {
      active = false
      window.clearInterval(timer)
    }
  }, [allowed])

  if (!allowed || counts === null) {
    return null
  }
  const total = counts.servantCount + counts.ownerCount
  if (total === 0) {
    return null
  }
  return (
    <Hoverlay text={t('todojob.waiting.hint', { servant: counts.servantCount, owner: counts.ownerCount })} alignRight>
      <Link className="todojob-waiting" to="/profile" aria-label={t('todojob.waiting.label', { count: total })}>
        <Icon name="todojob" />
        <span className="todojob-waiting-count">{total}</span>
      </Link>
    </Hoverlay>
  )
}
