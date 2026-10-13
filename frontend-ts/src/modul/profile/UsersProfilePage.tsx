import { useParams } from 'react-router'
import { ProfileOverview } from './ProfileOverview.tsx'

/**
 * Das Profil eines anderen Users unter /profile/user/<guid> (User 2026-10-10): Wohin jeder angezeigte Name führt.
 * Dieselbe Seite wie das eigene Profil, nur mit der fremden guid; was nur dem Angemeldeten gehört, blendet die Seite aus.
 */
export function UsersProfilePage() {
  const { guid } = useParams()
  return <ProfileOverview key={guid} usersGuid={guid} />
}
