import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { ADMIN_ACCESS_ROLE_KEY } from '../accessrole/AccessRole.ts'
import { DOCUMENT_GRANTS_TAB_PATH, type DocumentGrantPrefillState } from '../grant/DocumentGrantPrefill.ts'
import { useSession } from '../login/sessionContext.ts'
import {
  DMS_CORE_KEY,
  DMS_STATE_ACTIVE,
  DMS_STATE_DELETED,
  dmsFileDownloadUrl,
  dmsSolrDocumentEditPath,
  dmsSolrDocumentPath,
  dmsSolrDocumentViewerPath,
} from './Dms.ts'
import { hasDmsViewer } from './dmsViewerByFileExtension.ts'
import { STATUS_FIELD_NAME } from './DmsFields.ts'
import type { DmsSolrDocument } from './DmsSolrDocumentFacts.tsx'

interface DmsSolrDocumentActionButtonsProps {
  document: DmsSolrDocument
  /** woher man kommt, damit Schließen dorthin zurückführt */
  origin?: OriginState
  /** auf der Detailseite selbst entfällt der Knopf „Details“ */
  onDetailsPage?: boolean
  /** ob writeAcl eine AR des Users enthält; ohne Angabe gibt es weder Bearbeiten noch Löschen */
  writeAclMatchForCurrentUser?: boolean
  /** das weiche Löschen, nur wo der Aufrufer es anbietet */
  onDelete?: () => void
}

/**
 * Die Wege zu einem DMS-Dokument, überall dieselben in derselben Reihenfolge (nach den DmsTrefferAktionen der Vorlage):
 * Auge ansehen (nur mit Viewer), Info Details, Pfeil herunterladen, Stift bearbeiten, Freigeben und Mülleimer löschen.
 * Stift und Mülleimer nur mit writeAcl-Match und nur an der geltenden Fassung. Freigeben ebenso nur mit
 * writeAcl-Match, denn wer schreiben darf, darf freigeben, und solange das Freigabemodul nur ADMIN offensteht, nur mit
 * ADMIN; es öffnet den Tab „Dateifreigaben“ mit Kern DMS und der id als Bedingung im Router-State, die Dokumentseite
 * als Herkunft. Das ist Anzeige, kein Schutz: Das Backend prüft jeden Schreibweg selbst.
 */
export function DmsSolrDocumentActionButtons({
  document,
  origin,
  onDetailsPage = false,
  writeAclMatchForCurrentUser = false,
  onDelete,
}: DmsSolrDocumentActionButtonsProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { hasAccessRole } = useSession()
  const id = typeof document.id === 'string' ? document.id : null
  if (id === null) {
    return null
  }
  const fileName = typeof document.fileName === 'string' ? document.fileName : id
  const status = document[STATUS_FIELD_NAME]
  const currentVersion = status === undefined || status === DMS_STATE_ACTIVE
  const canEdit = writeAclMatchForCurrentUser && currentVersion
  const canGrant = writeAclMatchForCurrentUser && currentVersion && hasAccessRole(ADMIN_ACCESS_ROLE_KEY)
  const canDelete = writeAclMatchForCurrentUser && status !== DMS_STATE_DELETED && onDelete !== undefined

  /** Den Tab „Dateifreigaben“ öffnen: Kern DMS, Bedingung id = dieses Dokument, zurück führt auf die Dokumentseite */
  function openDocumentGrant(documentId: string) {
    const state: DocumentGrantPrefillState & OriginState = {
      documentGrantPrefill: { coreKey: DMS_CORE_KEY, fieldValuesByFieldName: { id: [documentId] } },
      origin: { path: dmsSolrDocumentPath(documentId), label: 'dms.document.title' },
    }
    navigate(DOCUMENT_GRANTS_TAB_PATH, { state })
  }

  return (
    <span className="d-inline-flex align-items-center gap-2">
      {hasDmsViewer(document) && (
        <Hoverlay text={t('dms.document.view', { name: fileName })}>
          <button
            type="button"
            className="button-other"
            aria-label={t('dms.document.view', { name: fileName })}
            onClick={() => navigate(dmsSolrDocumentViewerPath(id), { state: origin })}
          >
            <Icon name="view" />
          </button>
        </Hoverlay>
      )}
      {!onDetailsPage && (
        <Hoverlay text={t('dms.document.details', { name: fileName })}>
          <button
            type="button"
            className="button-other"
            aria-label={t('dms.document.details', { name: fileName })}
            onClick={() => navigate(dmsSolrDocumentPath(id), { state: origin })}
          >
            <Icon name="details" />
          </button>
        </Hoverlay>
      )}
      <Hoverlay text={t('dms.document.download', { name: fileName })}>
        <a className="button-other" href={dmsFileDownloadUrl(id)} download aria-label={t('dms.document.download', { name: fileName })}>
          <Icon name="download" />
        </a>
      </Hoverlay>
      {canEdit && (
        <EditButton
          label={t('dms.document.edit', { name: fileName })}
          onClick={() => navigate(dmsSolrDocumentEditPath(id), { state: origin })}
        />
      )}
      {canGrant && (
        <Hoverlay text={t('dms.document.grant', { name: fileName })}>
          <button
            type="button"
            className="button-other"
            aria-label={t('dms.document.grant', { name: fileName })}
            onClick={() => openDocumentGrant(id)}
          >
            <Icon name="documentGrants" />
          </button>
        </Hoverlay>
      )}
      {canDelete && <DeleteButton label={t('dms.document.delete', { name: fileName })} onClick={onDelete} />}
    </span>
  )
}
