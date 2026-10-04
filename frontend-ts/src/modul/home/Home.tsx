import { useTranslation } from 'react-i18next'
import type { IconName } from '../../branding/icons.ts'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { Tile } from '../../components/Tile.tsx'
import { ADMIN_ACCESS_ROLE_KEY } from '../accessrole/AccessRole.ts'
import { AccessRoleBadge } from '../accessrole/AccessRoleBadge.tsx'
import { DMS_ACCESS_ROLE_KEY } from '../dms/Dms.ts'
import { useSession } from '../login/sessionContext.ts'

interface HomeTile {
  icon: IconName
  /** Schlüssel des Titels im Sprachkatalog, unter home.tiles */
  titleKey: 'admin' | 'solrManager' | 'dms' | 'grant'
  to: string
  /**
   * Die AR, von denen eine reicht, um das Modul zu betreten: Wer im Modul etwas darf, sieht die Kachel. Was er darin
   * darf, entscheiden weitere AR im Modul (z. B. DMS_FULL, DMS_SUCHE). Leer: für alle, auch ohne Anmeldung. Dieselben
   * AR muss die Route in main.tsx verlangen (RequireLogin).
   */
  entryAccessRoleKeys: string[]
}

const HOME_TILES: HomeTile[] = [
  { icon: 'admin', titleKey: 'admin', to: '/admin', entryAccessRoleKeys: [ADMIN_ACCESS_ROLE_KEY] },
  { icon: 'solrManager', titleKey: 'solrManager', to: '/solrmanager', entryAccessRoleKeys: [ADMIN_ACCESS_ROLE_KEY] },
  { icon: 'dms', titleKey: 'dms', to: '/dms', entryAccessRoleKeys: [DMS_ACCESS_ROLE_KEY] },
  // Freigaben vorerst nur für ADMIN, später eine eigene AR, z. B. für die Datenpflege
  { icon: 'grant', titleKey: 'grant', to: '/grant', entryAccessRoleKeys: [ADMIN_ACCESS_ROLE_KEY] },
]

/**
 * Das i oben rechts in der Kachel: Im Hoverlay die AR, von denen eine das Modul öffnet, als Badges. Die AR, die der
 * User hat, voll, die anderen gedimmt, dazu der Hinweis, dass weitere AR im Modul bestimmen, was er darin darf.
 */
function HomeTileEntryAccessRolesHint({ entryAccessRoleKeys }: { entryAccessRoleKeys: string[] }) {
  const { t } = useTranslation()
  const { hasAccessRole } = useSession()
  const { accessRoles } = useMasterData()
  if (entryAccessRoleKeys.length === 0) {
    return null
  }
  const anyMissing = entryAccessRoleKeys.some((key) => !hasAccessRole(key))

  const text = (
    <span className="tile-entry-access-roles">
      <span>{t('home.entryAccessRoles.oneOf', { count: entryAccessRoleKeys.length })}</span>
      <span className="tile-entry-access-roles-badges">
        {entryAccessRoleKeys.map((key) => {
          const accessRole = accessRoles?.find((candidate) => candidate.key === key)
          return (
            <span key={key} className={hasAccessRole(key) ? undefined : 'tile-entry-access-role-missing'}>
              {/* ohne eigenes Hoverlay: ein Hoverlay im Hoverlay geht nicht */}
              {accessRole ? <AccessRoleBadge badge={accessRole} size="full" withHoverlay={false} /> : <code>{key}</code>}
            </span>
          )
        })}
      </span>
      {anyMissing && <span className="small">{t('home.entryAccessRoles.missingHint')}</span>}
      <span className="small">{t('home.entryAccessRoles.insideHint')}</span>
    </span>
  )

  return (
    <Hoverlay text={text} alignRight>
      <span className="tile-corner-hint" tabIndex={0} aria-label={t('home.entryAccessRoles.symbolLabel')}>
        <Icon name="info" />
      </span>
    </Hoverlay>
  )
}

/** Startseite: Kacheln zu den Modulen, ohne subheader. Jeder sieht nur die Kacheln der Module, in denen er etwas darf. */
export function Home() {
  const { t } = useTranslation()
  const { sessionInfo, hasAccessRole } = useSession()
  const visibleTiles = HOME_TILES.filter(
    (tile) => tile.entryAccessRoleKeys.length === 0 || tile.entryAccessRoleKeys.some(hasAccessRole),
  )

  return (
    <div className="main-content">
      <div className="row g-3">
        {visibleTiles.map((tile) => (
          <div key={tile.to} className="col-6 col-md-4 col-lg-3">
            <Tile
              icon={tile.icon}
              title={t(`home.tiles.${tile.titleKey}`)}
              to={tile.to}
              cornerHint={<HomeTileEntryAccessRolesHint entryAccessRoleKeys={tile.entryAccessRoleKeys} />}
            />
          </div>
        ))}
      </div>
      {sessionInfo !== null && visibleTiles.length === 0 && (
        <p className="mb-0">{sessionInfo.loggedIn ? t('home.noModules') : t('home.noModulesAnonymous')}</p>
      )}
    </div>
  )
}
