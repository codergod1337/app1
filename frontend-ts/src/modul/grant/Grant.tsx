import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router'
import { Icon } from '../../components/Icon.tsx'
import { Subheader, type SubheaderTab } from '../../components/Subheader.tsx'

/**
 * Freigaben: Dateifreigaben je Kern, Feldfreigaben je Kern und Hook-Freigaben serverweit anlegen, ansehen, entziehen.
 * Für den Benutzer der App eine Sache, innen drei: Eine Dateifreigabe schreibt die guid des Empfängers in die
 * Dokumente, eine Feldfreigabe öffnet ein Feld eines Kerns zum Auflösen, eine Hook-Freigabe einen Hook in jedem Kern.
 * Vorerst nur für ADMIN, später eine eigene AR, z. B. für die Datenpflege.
 */
export function Grant() {
  const { t } = useTranslation()

  const tabs: SubheaderTab[] = [
    { label: t('grant.tabs.documents'), icon: 'documentGrants', to: '/grant/documents' },
    { label: t('grant.tabs.fields'), icon: 'fieldGrants', to: '/grant/fields' },
    { label: t('grant.tabs.hooks'), icon: 'hookGrants', to: '/grant/hooks' },
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
