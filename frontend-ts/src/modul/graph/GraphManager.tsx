import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router'
import { Icon } from '../../components/Icon.tsx'
import { Subheader, type SubheaderTab } from '../../components/Subheader.tsx'

/**
 * Das Modul GraphManager: alles, was über Microsoft Graph läuft. Heute die Reiter „Mailvorlagen“ und „Test-Mail“,
 * später die Postfächer und ihre Zuweisung an User. Eintritt mit AR ADMIN, solange es nur Admin-Endpunkte gibt.
 */
export function GraphManager() {
  const { t } = useTranslation()
  const tabs: SubheaderTab[] = [
    { label: t('graph.tabs.mailTemplates'), icon: 'mailTemplates', to: '/graph/mailtemplates' },
    { label: t('graph.tabs.testMail'), icon: 'send', to: '/graph/testmail' },
  ]

  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="graph" /> {t('graph.title')}
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
