import { useTranslation } from 'react-i18next'
import type { IconName } from '../../branding/icons.ts'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { Tile } from '../../components/Tile.tsx'
import { ABAS_MANAGER_PATH } from '../abasmanager/AbasSternzeit.ts'
import { ADMIN_ACCESS_ROLE_KEY } from '../accessrole/AccessRole.ts'
import { AccessRoleBadge } from '../accessrole/AccessRoleBadge.tsx'
import { ARCHIVIEREN_ENTRY_ACCESS_ROLE_KEYS, ARCHIVIEREN_PATH } from '../archivieren/Archivieren.ts'
import { ARTIKEL_ACCESS_ROLE_KEY, ARTIKEL_PATH, ARTIKEL_SEARCH_ACCESS_ROLE_KEY } from '../artikel/Artikel.ts'
import { BELEGNUMMER_PATH } from '../belegnummer/Belegnummer.ts'
import { DMS_ACCESS_ROLE_KEY, DMS_PUBLIC_ACCESS_ROLE_KEY, DMS_SEARCH_ACCESS_ROLE_KEY } from '../dms/Dms.ts'
import { INFOSYS_MANAGER_PATH } from '../infosys/InfosysManager.tsx'
import { KONTAKTE_SEARCH_ACCESS_ROLE_KEY } from '../kontakte/Kontakte.ts'
import { useSession } from '../login/sessionContext.ts'
import { MAPPING_PATH } from '../mapping/Mapping.tsx'
import { NACHRICHTEN_SEARCH_ACCESS_ROLE_KEY } from '../nachrichten/Nachrichten.ts'
import { REQUESTMONITOR_PATH } from '../requestmonitor/RequestMonitor.ts'
import { SEARCH_PATH } from '../search/Search.ts'
import { TESTFAKEDATA_PATH } from '../testfakedata/TestFakeData.ts'

interface HomeTile {
  icon: IconName
  /** Schlüssel des Titels im Sprachkatalog, unter home.tiles */
  titleKey: 'admin' | 'solrManager' | 'abasManager' | 'infosys' | 'mapping' | 'dms' | 'artikel' | 'belegnummer' | 'search' | 'grant' | 'graph' | 'archivieren' | 'testfakedata' | 'requestmonitor'
  to: string
  /**
   * Die AR, von denen eine reicht, um das Modul zu betreten: Wer im Modul etwas darf, sieht die Kachel. Was er darin
   * darf, entscheiden weitere AR im Modul (z. B. DMS_FULL, DMS_SUCHE). Leer: für alle, auch ohne Anmeldung, es sei
   * denn loginRequired. Dieselben AR muss die Route in main.tsx verlangen (RequireLogin).
   */
  entryAccessRoleKeys: string[]
  /** ohne AR, aber nur angemeldet: die Kachel erscheint erst nach dem Login, die Route ist RequireLogin ohne AR */
  loginRequired?: boolean
}

const HOME_TILES: HomeTile[] = [
  { icon: 'admin', titleKey: 'admin', to: '/admin', entryAccessRoleKeys: [ADMIN_ACCESS_ROLE_KEY] },
  { icon: 'solrManager', titleKey: 'solrManager', to: '/solrmanager', entryAccessRoleKeys: [ADMIN_ACCESS_ROLE_KEY] },
  // AbasManager: die Anbindung an das ABAS-ERP. Nur ADMIN, solange es nur Admin-Endpunkte gibt.
  { icon: 'abasManager', titleKey: 'abasManager', to: ABAS_MANAGER_PATH, entryAccessRoleKeys: [ADMIN_ACCESS_ROLE_KEY] },
  // Infosysteme: die hinterlegten Aufrufe bei ABAS, von den Subfetches des Mappings und später von Masken genutzt. Nur ADMIN.
  { icon: 'infosys', titleKey: 'infosys', to: INFOSYS_MANAGER_PATH, entryAccessRoleKeys: [ADMIN_ACCESS_ROLE_KEY] },
  // Mapping: JSON als Baum ansehen, später die Vorschriften. Nur ADMIN.
  { icon: 'mapping', titleKey: 'mapping', to: MAPPING_PATH, entryAccessRoleKeys: [ADMIN_ACCESS_ROLE_KEY] },
  { icon: 'dms', titleKey: 'dms', to: '/dms', entryAccessRoleKeys: [DMS_ACCESS_ROLE_KEY] },
  // Artikel: die Artikelmappe aus dem Kern ARTIKEL, für alle mit der AR ARTIKEL; die Suche darin braucht ARTIKEL_SUCHE
  { icon: 'artikel', titleKey: 'artikel', to: ARTIKEL_PATH, entryAccessRoleKeys: [ARTIKEL_ACCESS_ROLE_KEY] },
  // Belegnummer: eine Nummer bei ABAS nachschlagen, für jeden Angemeldeten ohne AR (User 2026-10-11)
  { icon: 'belegnummer', titleKey: 'belegnummer', to: BELEGNUMMER_PATH, entryAccessRoleKeys: [], loginRequired: true },
  // die Suche über alle Kerne: die Such-Rolle eines Kerns reicht, gesucht wird dann nur in diesem (User 2026-10-14)
  {
    icon: 'search',
    titleKey: 'search',
    to: SEARCH_PATH,
    entryAccessRoleKeys: [
      DMS_SEARCH_ACCESS_ROLE_KEY,
      ARTIKEL_SEARCH_ACCESS_ROLE_KEY,
      KONTAKTE_SEARCH_ACCESS_ROLE_KEY,
      NACHRICHTEN_SEARCH_ACCESS_ROLE_KEY,
    ],
  },
  // Freigaben: die Abholscheine des DMS, für ADMIN und die Internetfreigabe (User 2026-10-10)
  { icon: 'share', titleKey: 'grant', to: '/share', entryAccessRoleKeys: [ADMIN_ACCESS_ROLE_KEY, DMS_PUBLIC_ACCESS_ROLE_KEY] },
  // GraphManager: Mailvorlagen, später Postfächer. Nur ADMIN, solange es nur Admin-Endpunkte gibt.
  { icon: 'graph', titleKey: 'graph', to: '/graph', entryAccessRoleKeys: [ADMIN_ACCESS_ROLE_KEY] },
  // Archivieren: Mails aus den eigenen Postfächern ins Archiv, für die Rolle des Archivierens und ADMIN (User 2026-10-14)
  { icon: 'archivieren', titleKey: 'archivieren', to: ARCHIVIEREN_PATH, entryAccessRoleKeys: [...ARCHIVIEREN_ENTRY_ACCESS_ROLE_KEYS] },
  // Testdaten: Dummy-Daten für die Solr-Kerne, um früh die Performance einzuschätzen. Nur ADMIN (User 2026-10-14)
  { icon: 'testfakedata', titleKey: 'testfakedata', to: TESTFAKEDATA_PATH, entryAccessRoleKeys: [ADMIN_ACCESS_ROLE_KEY] },
  // Anfragenmonitor: die Map der Anfragen an den Login-Endpunkt, live. Nur ADMIN (User 2026-10-15)
  { icon: 'requestmonitor', titleKey: 'requestmonitor', to: REQUESTMONITOR_PATH, entryAccessRoleKeys: [ADMIN_ACCESS_ROLE_KEY] },
]

/**
 * Das i oben rechts in der Kachel: Im Hoverlay die AR, von denen eine das Modul öffnet, als Badges. Die AR, die der
 * User hat, voll, die anderen gedimmt, dazu der Hinweis, dass weitere AR im Modul bestimmen, was er darin darf.
 */
function HomeTileEntryAccessRolesHint({ entryAccessRoleKeys, loginRequired = false }: { entryAccessRoleKeys: string[]; loginRequired?: boolean }) {
  const { t } = useTranslation()
  const { hasAccessRole } = useSession()
  const { accessRoles } = useMasterData()
  if (entryAccessRoleKeys.length === 0) {
    if (!loginRequired) {
      return null
    }
    return (
      <Hoverlay text={<span className="tile-entry-access-roles">{t('home.entryAccessRoles.loginRequired')}</span>} alignRight>
        <span className="tile-corner-hint" tabIndex={0} aria-label={t('home.entryAccessRoles.symbolLabel')}>
          <Icon name="info" />
        </span>
      </Hoverlay>
    )
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
  const loggedIn = sessionInfo?.loggedIn === true
  const visibleTiles = HOME_TILES.filter((tile) =>
    tile.entryAccessRoleKeys.length === 0 ? !tile.loginRequired || loggedIn : tile.entryAccessRoleKeys.some(hasAccessRole),
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
              cornerHint={<HomeTileEntryAccessRolesHint entryAccessRoleKeys={tile.entryAccessRoleKeys} loginRequired={tile.loginRequired} />}
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
