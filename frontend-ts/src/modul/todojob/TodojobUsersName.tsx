import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { usersDisplayName } from '../profile/profileDisplay.ts'
import { getUsersByGuid, type Users } from '../users/Users.ts'

/** Einmal geladen, für die ganze Sitzung: Owner und Servants wiederholen sich auf jeder Karte */
const usersByGuid = new Map<string, Promise<Users | null>>()

function loadUsers(guid: string): Promise<Users | null> {
  let pending = usersByGuid.get(guid)
  if (!pending) {
    pending = getUsersByGuid(guid)
      .then(({ data }) => data)
      .catch(() => null)
    usersByGuid.set(guid, pending)
  }
  return pending
}

/** Der Name eines Users zu seiner guid, als Link in sein Profil; bis er da ist oder wenn es ihn nicht gibt, die kurze guid */
export function TodojobUsersName({ guid }: { guid: string }) {
  const [users, setUsers] = useState<Users | null>(null)

  useEffect(() => {
    let active = true
    loadUsers(guid).then((loaded) => {
      if (active) {
        setUsers(loaded)
      }
    })
    return () => {
      active = false
    }
  }, [guid])

  if (users === null) {
    return <span className="font-monospace">{guid.slice(0, 8)}</span>
  }
  return <Link to={`/profile/user/${encodeURIComponent(guid)}`}>{usersDisplayName(users)}</Link>
}
