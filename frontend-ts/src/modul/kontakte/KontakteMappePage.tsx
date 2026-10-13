import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { Subheader, type SubheaderTab } from '../../components/Subheader.tsx'
import {
  KONTAKTE_CONTACT_KIND_COMPANY,
  KONTAKTE_CONTACT_KIND_FIELD_NAME,
  KONTAKTE_CONTACT_KIND_PERSON,
  KONTAKTE_MAPPE_TABS,
  KONTAKTE_PARTNER_ROLES,
  type KontakteMappeTab,
  type KontaktePartnerRole,
  isKontakteMappeTab,
  kontakteMappePath,
  resolveAllKontakteDocuments,
  resolveKontakteDocuments,
} from './Kontakte.ts'
import { KontakteMappeContactPersonsTab } from './KontakteMappeContactPersonsTab.tsx'
import { KontakteMappeDocumentsTab } from './KontakteMappeDocumentsTab.tsx'
import { KontakteMappeOverviewTab } from './KontakteMappeOverviewTab.tsx'

interface KontakteMappePageProps {
  partnerRole: KontaktePartnerRole
}

/** Was die Seite zu einer Nummer geladen hat; eine andere Rolle oder Nummer in der Adresse heißt „lädt“ */
type Loaded = { requestKey: string } & (
  | { kind: 'error'; message: string }
  | { kind: 'notFound' }
  | { kind: 'ok'; company: Record<string, unknown>; contactPersons: Record<string, unknown>[] }
)

/**
 * Die Kunden- oder Lieferantenmappe (User 2026-10-13), nach der Kundenmappe der Vorlage, aber je Rolle eine Mappe:
 * Route /kunde/:nummer/:tab? und /lieferant/:nummer/:tab?, keine Kachel, der Weg hinein sind die Badges der Nummern
 * und die Suche. Geladen über das Auflösen im Kern KONTAKTE (Tür KONTAKTE): die Firma über die Nummer und kontaktorig
 * „Adresse“, ihre Ansprechpartner über dieselbe Nummer und „Kontakt“. Tabs: Übersicht, Ansprechpartner nur mit
 * Ansprechpartnern, Dokumente aus dem DMS. Die Herkunft für jeden Weg aus der Mappe ist der aktive Tab.
 */
export function KontakteMappePage({ partnerRole }: KontakteMappePageProps) {
  const { t } = useTranslation()
  const { nummer = '', tab } = useParams()
  const [lastLoaded, setLastLoaded] = useState<Loaded | null>(null)
  const role = KONTAKTE_PARTNER_ROLES[partnerRole]
  const requestKey = `${partnerRole}:${nummer}`

  useEffect(() => {
    let cancelled = false
    const numberCondition = { [role.numberHookName]: nummer }
    Promise.all([
      resolveKontakteDocuments({ ...numberCondition, [KONTAKTE_CONTACT_KIND_FIELD_NAME]: KONTAKTE_CONTACT_KIND_COMPANY }),
      resolveAllKontakteDocuments({ ...numberCondition, [KONTAKTE_CONTACT_KIND_FIELD_NAME]: KONTAKTE_CONTACT_KIND_PERSON }),
    ])
      .then(([companies, contactPersons]) => {
        if (cancelled) {
          return
        }
        const company = companies.data.documents[0]
        setLastLoaded(company === undefined ? { requestKey, kind: 'notFound' } : { requestKey, kind: 'ok', company, contactPersons })
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setLastLoaded({ requestKey, kind: 'error', message: error instanceof RequestError ? error.message : t('common.unknownError') })
        }
      })
    return () => {
      cancelled = true
    }
  }, [nummer, requestKey, role.numberHookName, t])

  const loaded = lastLoaded !== null && lastLoaded.requestKey === requestKey ? lastLoaded : null

  /** Ein Tab nur mit Daten: Ansprechpartner nur, wenn es welche gibt */
  function tabHasData(tabKey: KontakteMappeTab): boolean {
    return tabKey !== 'contactPersons' || (loaded?.kind === 'ok' && loaded.contactPersons.length > 0)
  }

  const activeTab: KontakteMappeTab = isKontakteMappeTab(tab) && tabHasData(tab) ? tab : 'overview'
  const tabs: SubheaderTab[] =
    loaded?.kind === 'ok'
      ? KONTAKTE_MAPPE_TABS.filter(tabHasData).map((tabKey) => ({ label: t(`kontakte.mappe.tabs.${tabKey}`), to: kontakteMappePath(partnerRole, nummer, tabKey) }))
      : []
  const origin: OriginState = { origin: { path: kontakteMappePath(partnerRole, nummer, activeTab), label: t(`kontakte.mappe.status.${partnerRole}`, { nummer }) } }
  const boxTitle = t(`kontakte.mappe.title.${partnerRole}`)

  let content
  if (loaded === null) {
    content = (
      <ContentBox title={boxTitle}>
        <p className="mb-0">{t('common.loading')}</p>
      </ContentBox>
    )
  } else if (loaded.kind === 'error') {
    content = (
      <ContentBox title={boxTitle}>
        <p className="mb-0 text-danger">{loaded.message}</p>
      </ContentBox>
    )
  } else if (loaded.kind === 'notFound') {
    content = (
      <ContentBox title={boxTitle}>
        <p className="mb-0 text-muted">{t(`kontakte.mappe.notFound.${partnerRole}`, { nummer })}</p>
      </ContentBox>
    )
  } else if (activeTab === 'contactPersons') {
    content = <KontakteMappeContactPersonsTab contactPersons={loaded.contactPersons} />
  } else if (activeTab === 'documents') {
    content = <KontakteMappeDocumentsTab partnerRole={partnerRole} nummer={nummer} origin={origin} />
  } else {
    content = <KontakteMappeOverviewTab partnerRole={partnerRole} nummer={nummer} company={loaded.company} origin={origin} />
  }

  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="kontaktFirma" /> {t(`kontakte.mappe.status.${partnerRole}`, { nummer })}
          </>
        }
        tabs={tabs}
        sticky
      />
      <div className="main-content">{content}</div>
    </>
  )
}
