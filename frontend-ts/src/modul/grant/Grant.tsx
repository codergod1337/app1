import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router'
import { Icon } from '../../components/Icon.tsx'
import { Subheader, type SubheaderTab } from '../../components/Subheader.tsx'

/**
 * Freigaben: Dateifreigaben und Feldfreigaben je Kern anlegen, ansehen, entziehen. Für den Benutzer der App eine
 * Sache, innen zwei: Eine Dateifreigabe schreibt die guid des Empfängers in die Dokumente, eine Feldfreigabe öffnet ein
 * erweitertes Feld eines Kerns. Vorerst nur für ADMIN, später eine eigene AR, z. B. für die Datenpflege.
 */
export function Grant() {
  const { t } = useTranslation()

  const tabs: SubheaderTab[] = [
    { label: t('grant.tabs.documents'), icon: 'documentGrants', to: '/grant/documents' },
    { label: t('grant.tabs.fields'), icon: 'fieldGrants', to: '/grant/fields' },
  ]

  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="grant" /> {t('grant.title')}
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
