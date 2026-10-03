import type { IconName } from '../../branding/icons.ts'
import { Tile } from '../../components/Tile.tsx'
import { ADMIN_ACCESS_ROLE_KEY } from '../accessrole/AccessRole.ts'
import { useSession } from '../login/sessionContext.ts'

interface HomeTile {
  icon: IconName
  title: string
  to: string
  /** Welche AR das Modul verlangt. null: für alle, auch ohne Anmeldung */
  accessRoleKey: string | null
}

const HOME_TILES: HomeTile[] = [
  { icon: 'admin', title: 'Administration', to: '/admin', accessRoleKey: ADMIN_ACCESS_ROLE_KEY },
  { icon: 'solrManager', title: 'SolrManager', to: '/solrmanager', accessRoleKey: ADMIN_ACCESS_ROLE_KEY },
]

/** Startseite: Kacheln zu den Modulen, ohne subheader. Jeder sieht nur die Kacheln, die er nutzen darf. */
export function Home() {
  const { sessionInfo, hasAccessRole } = useSession()
  const visibleTiles = HOME_TILES.filter((tile) => tile.accessRoleKey === null || hasAccessRole(tile.accessRoleKey))

  return (
    <div className="main-content">
      <div className="row g-3">
        {visibleTiles.map((tile) => (
          <div key={tile.to} className="col-6 col-md-4 col-lg-3">
            <Tile icon={tile.icon} title={tile.title} to={tile.to} />
          </div>
        ))}
      </div>
      {sessionInfo !== null && visibleTiles.length === 0 && (
        <p className="mb-0">
          {sessionInfo.loggedIn
            ? 'Für dich gibt es hier noch keine Module.'
            : 'Hier gibt es noch keine Module ohne Anmeldung. Melde dich an, um mehr zu sehen.'}
        </p>
      )}
    </div>
  )
}
