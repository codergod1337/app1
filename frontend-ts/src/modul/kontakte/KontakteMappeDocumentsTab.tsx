import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { DMS_ACCESS_ROLE_KEY, resolveDmsDocumentsPageByExactFieldValues, useDmsDocumentListMode } from '../dms/Dms.ts'
import { DmsDocumentListModeToggle } from '../dms/DmsDocumentListModeToggle.tsx'
import { DmsFileSubClassDocumentsList } from '../dms/DmsFileSubClassDocumentsList.tsx'
import type { DmsSolrDocument } from '../dms/DmsSolrDocumentFacts.tsx'
import { DMS_UPLOAD_PATH, type DmsUploadPrefillState } from '../dms/DmsUploadPrefill.ts'
import { useSession } from '../login/sessionContext.ts'
import { KONTAKTE_PARTNER_ROLES, type KontaktePartnerRole } from './Kontakte.ts'

interface KontakteMappeDocumentsTabProps {
  partnerRole: KontaktePartnerRole
  nummer: string
  origin: OriginState
}

/**
 * Die Dokumente des Kunden oder Lieferanten aus dem Kern DMS, nach dem Dokumente-Tab der Artikelmappe: aufgelöst über
 * den Hook der Rolle (kundenNummer oder lieferantenNummer), gezeigt je Dateiart mit dem Anzeigeort KUNDENMAPPE oder
 * LIEFERANTENMAPPE, die Ansicht wählt der Umschalter im Kopf und merkt sich je Mappe. Tür ist die AR DMS, ohne sie bleibt
 * der Tab ein Hinweis. Hochladen führt in die Upload-Maske mit der Dateiart gesperrt und der Nummer als Hook vorbelegt.
 */
export function KontakteMappeDocumentsTab({ partnerRole, nummer, origin }: KontakteMappeDocumentsTabProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { hasAccessRole } = useSession()
  const allowed = hasAccessRole(DMS_ACCESS_ROLE_KEY)
  const role = KONTAKTE_PARTNER_ROLES[partnerRole]
  // je Mappe eine eigene UsersSetting für die Ansicht, wie ARTIKELMAPPE_DATENTYPEN
  const modeSettingKey = `${role.documentsDestinationKey}_DATENTYPEN`
  const mode = useDmsDocumentListMode(modeSettingKey)
  const [loaded, setLoaded] = useState<{ key: string; documents: DmsSolrDocument[] } | { key: string; errorMessage: string } | null>(null)
  const requestKey = `${partnerRole}:${nummer}`

  useEffect(() => {
    if (!allowed) {
      return
    }
    let cancelled = false
    resolveDmsDocumentsPageByExactFieldValues({ fieldValuesByFieldName: { [role.numberHookName]: [nummer] }, page: 0, size: 0, sortFieldName: null, sortDescending: false })
      .then((result) => {
        if (!cancelled) {
          setLoaded({ key: requestKey, documents: result.data.documents })
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setLoaded({ key: requestKey, errorMessage: error instanceof RequestError ? error.message : t('common.unknownError') })
        }
      })
    return () => {
      cancelled = true
    }
  }, [allowed, nummer, requestKey, role.numberHookName, t])

  /** In die Upload-Maske: die Dateiart fest, die Nummer als Hook vorbelegt, die Mappe als Herkunft */
  function openUpload(fileSubClassKey: string) {
    const state: DmsUploadPrefillState & OriginState = {
      dmsUploadPrefill: { fileSubClassKey, lockFileSubClass: true, hooks: { [role.numberHookName]: [nummer] } },
      origin: origin.origin,
    }
    navigate(DMS_UPLOAD_PATH, { state })
  }

  // geladen ist nur, was zu dieser Mappe gehört; sonst lädt der Tab gerade
  const current = loaded !== null && loaded.key === requestKey ? loaded : null
  let footer
  if (!allowed) {
    footer = <span className="text-muted">{t('kontakte.mappe.documents.noAccess', { role: DMS_ACCESS_ROLE_KEY })}</span>
  } else if (current === null) {
    footer = <span>{t('common.loading')}</span>
  } else if ('errorMessage' in current) {
    footer = <span className="text-danger">{current.errorMessage}</span>
  } else {
    footer = <span>{t('kontakte.mappe.documents.count', { count: current.documents.length })}</span>
  }

  return (
    <ContentBox title={t('kontakte.mappe.tabs.documents')} actions={allowed ? <DmsDocumentListModeToggle settingKey={modeSettingKey} /> : undefined} footer={footer}>
      {allowed && current !== null && 'documents' in current && (
        <DmsFileSubClassDocumentsList documents={current.documents} displayContextKey={role.documentsDestinationKey} mode={mode} onNewFile={openUpload} origin={origin} />
      )}
    </ContentBox>
  )
}
