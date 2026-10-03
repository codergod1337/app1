import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router'
import { Icon } from '../../components/Icon.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { Subheader, type SubheaderTab } from '../../components/Subheader.tsx'

/**
 * SolrManager: eine stark abgespeckte Solr-Admin-Oberfläche. Feste Tabs „Hooks“, „Hook-Gruppen“ und „Kerne“, dahinter je Kern ein
 * eigener Tab aus den Stammdaten, mit seinem Symbol in seiner Hauptfarbe. Subheader mit den Tabs, darunter der gewählte
 * Tab.
 */
export function SolrManager() {
  const { t } = useTranslation()
  const { solrCores } = useMasterData()

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
      <div className="main-content">
        <Outlet />
      </div>
    </>
  )
}
