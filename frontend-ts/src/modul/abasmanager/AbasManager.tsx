import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router'
import { Icon } from '../../components/Icon.tsx'
import { Subheader, type SubheaderTab } from '../../components/Subheader.tsx'
import { ABAS_MANAGER_PATH } from './AbasSternzeit.ts'

/**
 * Das Modul AbasManager: die Anbindung an das ABAS-ERP. Heute der Tab „Übersicht“ mit den Stammsätzen der Sternzeiten;
 * Queue, Sperrliste, Fetch-Steuerung und das Mapping kommen als weitere Tabs dazu. Eintritt mit AR ADMIN.
 */
export function AbasManager() {
  const { t } = useTranslation()
  // die hinterlegten Infosystem-Aufrufe sind ein eigenes Modul (modul/infosys/, User 2026-10-11)
  const tabs: SubheaderTab[] = [{ label: t('abasmanager.tabs.overview'), icon: 'abasManager', to: `${ABAS_MANAGER_PATH}/overview` }]

  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="abasManager" /> {t('abasmanager.title')}
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
