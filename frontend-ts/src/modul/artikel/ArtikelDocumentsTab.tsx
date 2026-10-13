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
import { ARTIKEL_MASTER_NUMBER_HOOK_NAME, ARTIKEL_NUMBER_HOOK_NAME, ARTIKELMAPPE_DOKUMENTE_DESTINATION_KEY } from './Artikel.ts'

/** Die UsersSetting mit der Ansicht des Dokumente-Tabs der Artikelmappe (ALL, WITH_FILES, FAVORITES) */
const ARTIKELMAPPE_DOCUMENT_LIST_MODE_SETTING_KEY = 'ARTIKELMAPPE_DATENTYPEN'

interface ArtikelDocumentsTabProps {
  artikelNummer: string
  /** die Masternummer des Artikels, leer ohne Familie */
  masterNumber: string
  origin: OriginState
}

/**
 * Die Dokumente des Artikels aus dem Kern DMS, nach dem Dokumente-Tab der Artikelmappe der Vorlage: über beide Hooks,
 * artikelNummer und masterArtikelNummer, denn die Familie teilt ihre Dateien. Zwei Auflösungen, per id zusammengelegt,
 * weil verschiedene Felder in einer Auflösung mit UND verknüpft wären. Tür ist die AR DMS, ohne sie bleibt der Tab ein
 * Hinweis. Gezeigt je Dateiart, die Ansicht wählt der Umschalter im Kopf; Hochladen führt in die Upload-Maske mit der
 * Dateiart gesperrt und beiden Nummern als Hooks vorbelegt, die Maske selbst bleibt, wie sie ist.
 */
export function ArtikelDocumentsTab({ artikelNummer, masterNumber, origin }: ArtikelDocumentsTabProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { hasAccessRole } = useSession()
  const allowed = hasAccessRole(DMS_ACCESS_ROLE_KEY)
  const mode = useDmsDocumentListMode(ARTIKELMAPPE_DOCUMENT_LIST_MODE_SETTING_KEY)
  const [documents, setDocuments] = useState<DmsSolrDocument[] | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!allowed) {
      return
    }
    let cancelled = false
    const requests = [resolveDmsDocumentsPageByExactFieldValues({ fieldValuesByFieldName: { [ARTIKEL_NUMBER_HOOK_NAME]: [artikelNummer] }, page: 0, size: 0, sortFieldName: null, sortDescending: false })]
    if (masterNumber !== '') {
      requests.push(resolveDmsDocumentsPageByExactFieldValues({ fieldValuesByFieldName: { [ARTIKEL_MASTER_NUMBER_HOOK_NAME]: [masterNumber] }, page: 0, size: 0, sortFieldName: null, sortDescending: false }))
    }
    Promise.all(requests)
      .then((results) => {
        if (cancelled) {
          return
        }
        const byId = new Map<string, DmsSolrDocument>()
        for (const result of results) {
          for (const document of result.data.documents) {
            if (typeof document.id === 'string') {
              byId.set(document.id, document)
            }
          }
        }
        setDocuments([...byId.values()])
        setErrorMessage(null)
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        }
      })
    return () => {
      cancelled = true
    }
  }, [allowed, artikelNummer, masterNumber, t])

  /** In die Upload-Maske: die Dateiart fest, beide Nummern als Hooks vorbelegt, die Mappe als Herkunft */
  function openUpload(fileSubClassKey: string) {
    const hooks: Record<string, string[]> = { [ARTIKEL_NUMBER_HOOK_NAME]: [artikelNummer] }
    if (masterNumber !== '') {
      hooks[ARTIKEL_MASTER_NUMBER_HOOK_NAME] = [masterNumber]
    }
    const state: DmsUploadPrefillState & OriginState = { dmsUploadPrefill: { fileSubClassKey, lockFileSubClass: true, hooks }, origin: origin.origin }
    navigate(DMS_UPLOAD_PATH, { state })
  }

  let footer
  if (!allowed) {
    footer = <span className="text-muted">{t('artikel.documents.noAccess', { role: DMS_ACCESS_ROLE_KEY })}</span>
  } else if (errorMessage !== null) {
    footer = <span className="text-danger">{errorMessage}</span>
  } else if (documents === null) {
    footer = <span>{t('common.loading')}</span>
  } else {
    footer = <span>{t('artikel.documents.count', { count: documents.length })}</span>
  }

  return (
    <ContentBox title={t('artikel.documents.title')} actions={allowed ? <DmsDocumentListModeToggle settingKey={ARTIKELMAPPE_DOCUMENT_LIST_MODE_SETTING_KEY} /> : undefined} footer={footer}>
      {allowed && documents !== null && (
        <DmsFileSubClassDocumentsList documents={documents} displayContextKey={ARTIKELMAPPE_DOKUMENTE_DESTINATION_KEY} mode={mode} onNewFile={openUpload} origin={origin} />
      )}
    </ContentBox>
  )
}
