import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router'
import { Icon } from '../../components/Icon.tsx'
import { Subheader, type SubheaderTab } from '../../components/Subheader.tsx'

/**
 * DMS: die Dokumente des Kerns DMS. Für alle mit der AR DMS. Tab „Upload“ ist die Stapelmaske, Tab „Probe“ der
 * Wegwerf-Upload, der prüft, liest und nichts speichert, Tab „Auflösen“ der exakte Leseweg mit der Grenze des
 * current users. Suche und Dokumentseite kommen danach.
 */
export function Dms() {
  const { t } = useTranslation()

  const tabs: SubheaderTab[] = [
    { label: t('dms.tabs.upload'), icon: 'upload', to: '/dms/upload' },
    { label: t('dms.tabs.probe'), icon: 'search', to: '/dms/probe' },
    { label: t('dms.tabs.resolve'), icon: 'resolve', to: '/dms/resolve' },
    { label: t('dms.tabs.myGrants'), icon: 'share', to: '/dms/my-grants' },
  ]

  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="dms" /> DMS
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
