import { useLoad } from '../../api/useLoad.ts'
import { getAllUsers } from './Users.ts'

/** Lädt alle User beim ersten Anzeigen und nach reload(). */
export function useAllUsers() {
  const { data, errorMessage, reload } = useLoad(getAllUsers)
  return { users: data, errorMessage, reload }
}
