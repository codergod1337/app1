import { useTranslation } from 'react-i18next'
import { Outlet, useLocation } from 'react-router'
import { Icon } from '../../components/Icon.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { Subheader, type SubheaderTab } from '../../components/Subheader.tsx'

/** Die festen Tabs; alles andere unter /solrmanager/ ist ein Kern-Tab */
const FIXED_TAB_PATHS = ['/solrmanager/hooks', '/solrmanager/hookgroups', '/solrmanager/cores']

/**
 * SolrManager: eine stark abgespeckte Solr-Admin-Oberfläche. Feste Tabs „Hooks“, „Hook-Gruppen“ und „Kerne“, dahinter je
 * Kern ein eigener Tab aus den Stammdaten, mit seinem Symbol in seiner Hauptfarbe. Subheader mit den Tabs, darunter der
 * gewählte Tab. Ein Kern-Tab ist eine Matrix-Darstellung (ohne ContentBox, über die ganze Breite): Die Feldtabelle
 * braucht die ganze Breite (User 2026-10-09), die festen Tabs bleiben in der normalen Spalte.
 */
export function SolrManager() {
  const { t } = useTranslation()
  const { solrCores } = useMasterData()
  const { pathname } = useLocation()
  const isCoreTab = !FIXED_TAB_PATHS.some((path) => pathname.startsWith(path))

  const tabs: SubheaderTab[] = [
    { label: t('solrmanager.tabs.hooks'), icon: 'solrHooks', to: '/solrmanager/hooks' },
    { label: t('solrmanager.tabs.hookGroups'), icon: 'solrHookGroups', to: '/solrmanager/hookgroups' },
    { label: t('solrmanager.tabs.cores'), icon: 'solrCores', to: '/solrmanager/cores' },
    ...(solrCores ?? []).map((solrCore) => ({
      label: translate(solrCore.displayName) || solrCore.key,
      symbol: solrCore.look.symbol ?? undefined,
      color: solrCore.look.mainColor ?? undefined,
      to: `/solrmanager/${solrCore.key}`,
    })),
  ]

  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="solrManager" /> SolrManager
          </>
        }
        tabs={tabs}
        sticky
      />
      {/* ein Kern-Tab baut seine Matrix über die ganze Breite selbst, ohne main-content */}
      {isCoreTab ? (
        <Outlet />
      ) : (
        <div className="main-content">
          <Outlet />
        </div>
      )}
    </>
  )
}
