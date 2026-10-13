import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { usersProfilePath } from '../modul/profile/profileDisplay.ts'
import { getUsersByGuid, type Users } from '../modul/users/Users.ts'
import { Hoverlay } from './Hoverlay.tsx'

/** guid → der geladene User, null wenn es ihn nicht gibt oder er nicht lesbar ist; einmal geholt bleibt es für die Sitzung */
const usersByGuid = new Map<string, Promise<Users | null>>()

function loadUsers(guid: string): Promise<Users | null> {
  let pending = usersByGuid.get(guid)
  if (pending === undefined) {
    pending = getUsersByGuid(guid)
      .then(({ data }) => data)
      .catch(() => null)
    usersByGuid.set(guid, pending)
  }
  return pending
}

interface UsersGuidNameProps {
  guid: string
}

/**
 * Ein User aus seiner guid (User 2026-10-10): Das Backend liefert überall nur die guid, angezeigt wird Vorname und
 * Nachname, sonst der Benutzername, sonst die E-Mail. Im Hoverlay der Benutzername, sonst die E-Mail. Ein Klick führt
 * zum Profil des Users. Solange der Name lädt oder wenn es den User nicht gibt, steht die guid. Jede guid wird je
 * Sitzung nur einmal geholt.
 */
export function UsersGuidName({ guid }: UsersGuidNameProps) {
  // der geladene User samt seiner guid: Wechselt die guid, passt der Stand nicht mehr und die guid steht, bis der neue da ist
  const [loaded, setLoaded] = useState<{ guid: string; users: Users | null } | null>(null)

  useEffect(() => {
    let cancelled = false
    void loadUsers(guid).then((users) => {
      if (!cancelled) {
        setLoaded({ guid, users })
      }
    })
    return () => {
      cancelled = true
    }
  }, [guid])

  const users = loaded !== null && loaded.guid === guid ? loaded.users : null
  if (users === null) {
    return <code>{guid}</code>
  }
  const fullName = [users.vorname, users.nachname].filter(Boolean).join(' ')
  const shown = fullName || users.username || users.email
  const hint = users.username ? `@${users.username}` : users.email
  return (
    <Hoverlay text={hint}>
      <Link to={usersProfilePath(users.guid)}>{shown}</Link>
    </Hoverlay>
  )
}
