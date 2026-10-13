import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router'
import { Icon } from '../../components/Icon.tsx'
import { Subheader, type SubheaderTab } from '../../components/Subheader.tsx'

/** Das Modul Infosysteme, neben AbasManager und Mapping (User 2026-10-11) */
export const INFOSYS_MANAGER_PATH = '/infosys'

/**
 * Das Modul Infosysteme: die hinterlegten Aufrufe bei ABAS, je Aufruf Gruppe, Name und der Request-Body als Vorlage
 * mit Platzhaltern. Tab „Aufrufe“ pflegt sie, Tab „Ausführen“ füllt die Platzhalter von Hand und zeigt die Antwort.
 * Die Subfetches des Mappings und später die Bezugsnummer (ow1/BELEG2ID wie in der Vorlage) nutzen dieselben Aufrufe.
 * Eintritt mit AR ADMIN.
 */
export function InfosysManager() {
  const { t } = useTranslation()
  const tabs: SubheaderTab[] = [
    { label: t('infosys.tabs.calls'), icon: 'infosys', to: `${INFOSYS_MANAGER_PATH}/calls` },
    { label: t('infosys.tabs.run'), icon: 'abasManager', to: `${INFOSYS_MANAGER_PATH}/run` },
  ]

  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="infosys" /> {t('infosys.title')}
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
