import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router'
import { Icon } from '../../components/Icon.tsx'
import { Subheader, type SubheaderTab } from '../../components/Subheader.tsx'
import { MAPPING_PATH } from './ResponseMapping.ts'

export { MAPPING_PATH }

/**
 * Das Modul Mapping: Vorschriften, die einem Key einen Wert aus einer geholten JSON-Antwort zuordnen, über Feldnamen,
 * nie über Positionen. Die Tabs „Mappings“ (die Vorschriften), „JSON-Ansicht“ (ein JSON aus Datei oder Text als Baum)
 * und „ABAS holen“ (Sternzeit oder Infosystem live über das Backend, um Adressen zu finden). Das Mapping selbst kennt
 * weder ABAS noch Solr noch Mail: Die liefern nur das JSON beziehungsweise nehmen die Werte ab.
 */
export function Mapping() {
  const { t } = useTranslation()
  const tabs: SubheaderTab[] = [
    { label: t('mapping.tabs.overview'), icon: 'mapping', to: `${MAPPING_PATH}/overview` },
    { label: t('mapping.tabs.jsonView'), icon: 'fullText', to: `${MAPPING_PATH}/json` },
    { label: t('mapping.tabs.abasFetch'), icon: 'abasManager', to: `${MAPPING_PATH}/abas` },
    { label: t('mapping.tabs.execute'), icon: 'send', to: `${MAPPING_PATH}/execute` },
  ]

  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="mapping" /> {t('mapping.title')}
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
