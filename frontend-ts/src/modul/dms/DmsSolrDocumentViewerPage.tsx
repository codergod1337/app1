import { lazy, Suspense, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { useOrigin } from '../../components/Origin.ts'
import { Popup } from '../../components/Popup.tsx'
import { Subheader } from '../../components/Subheader.tsx'
import { solrHookValuesOf } from '../solrmanager/SolrHook.ts'
import { SolrHookFields } from '../solrmanager/SolrHookFields.tsx'
import { dmsFieldLabel, dmsFileDownloadUrl, getDmsSolrDocument, type DmsSolrDocumentForClient } from './Dms.ts'
import { DmsPdfViewer } from './DmsPdfViewer.tsx'
import { DmsSolrDocumentActionButtons } from './DmsSolrDocumentActionButtons.tsx'
import { DmsTextViewer } from './DmsTextViewer.tsx'
import { dmsViewerKindOf } from './dmsViewerByFileExtension.ts'

// Word und Excel bringen große Bibliotheken mit (docx-preview, SheetJS): Wie in der Vorlage lädt der Browser sie erst,
// wenn wirklich so eine Datei geöffnet wird, nicht mit jeder Seite
const DmsWordViewer = lazy(() => import('./DmsWordViewer.tsx').then((module) => ({ default: module.DmsWordViewer })))
const DmsSpreadsheetViewer = lazy(() => import('./DmsSpreadsheetViewer.tsx').then((module) => ({ default: module.DmsSpreadsheetViewer })))

/**
 * Ein Bild, die Bytes über den Download-Weg: der Browser kann das selbst. Zwei Größen, ein Klick, wie ein einzeln
 * geöffnetes Bild im Browser (nach dem BildViewer der Vorlage): eingepasst, ganz zu sehen, oder 1:1 in echten Pixeln,
 * dann scrollt die Fläche. Kein Zoomregler: ganz sehen oder genau sehen, für alles dazwischen gibt es den Download.
 */
function DmsImageViewer({ id, fileName }: { id: string; fileName: string }) {
  const { t } = useTranslation()
  const [nativeSize, setNativeSize] = useState(false)
  return (
    <Hoverlay text={nativeSize ? t('dms.viewer.fitAgain') : t('dms.viewer.showNativeSize')}>
      <img
        className={nativeSize ? 'viewer-image viewer-image-native' : 'viewer-image'}
        src={dmsFileDownloadUrl(id)}
        alt={fileName}
        onClick={() => setNativeSize(!nativeSize)}
      />
    </Hoverlay>
  )
}

/**
 * Die Seite, auf der eine Datei angezeigt wird: /dms/viewer/<id>, nach der ViewerSeite der Vorlage. In der URL steht
 * nur die id, alles andere kommt aus dem Dokument, das mit der Leseprüfung des Users geladen wird.
 *
 * Ohne ContentBox: Der eigene subheader trägt, was man beim Ansehen braucht, Dateiname mit der Beschreibung im
 * Hoverlay, Index, die Hooks als Knopf mit Anzahl und Popup, rechts die Aktionen. Darunter bekommt die Datei die ganze
 * Fläche, die in sich scrollt. Welche Endung welchen Viewer bekommt, sagt dmsViewerByFileExtension; ohne Viewer bleibt
 * der Download. Eine Lese-Seite: kein Schließen, raus geht es über das „zurück zu …“ im Header (User 2026-10-14).
 */
export function DmsSolrDocumentViewerPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const origin = useOrigin()
  const { language } = useLanguage()
  const { solrHooks, solrFields } = useMasterData()
  const [loaded, setLoaded] = useState<DmsSolrDocumentForClient | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [hooksOpen, setHooksOpen] = useState(false)

  useEffect(() => {
    if (id === undefined) {
      return
    }
    setErrorMessage(null)
    getDmsSolrDocument(id)
      .then(({ data }) => setLoaded(data))
      .catch((error: unknown) => {
        setLoaded(null)
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
      })
    // neu laden, wenn eine andere id in der Adresse steht
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const originState = origin !== null ? { origin } : undefined
  const document = loaded?.document ?? null
  const fileName = typeof document?.fileName === 'string' ? document.fileName : (id ?? '')
  const description = typeof document?.fileDescription === 'string' ? translate(document.fileDescription, language) : ''
  const fileIndex = typeof document?.fileIndex === 'string' ? document.fileIndex : null
  const hooks = document ? solrHookValuesOf(document, solrHooks ?? []) : {}
  const hookCount = Object.keys(hooks).length
  const viewerKind = document ? dmsViewerKindOf(document) : null

  const status = (
    <span className="d-inline-flex align-items-center gap-2">
      <Icon name="view" /> {t('dms.viewer.title')}
      {document && (
        <>
          <Hoverlay text={description !== '' ? description : null}>
            <span className="subheader-note fw-semibold">{fileName}</span>
          </Hoverlay>
          {fileIndex !== null && (
            <span className="subheader-note">
              {dmsFieldLabel(solrFields, 'fileIndex', t('dms.document.fileIndex'), language)}: {fileIndex}
            </span>
          )}
          <Hoverlay text={t('dms.viewer.showHooks')}>
            <button
              type="button"
              className="button-other"
              aria-label={t('dms.viewer.showHooks')}
              onClick={() => setHooksOpen(true)}
            >
              <Icon name="solrHooks" /> {hookCount}
            </button>
          </Hoverlay>
        </>
      )}
    </span>
  )

  const actions = document && <DmsSolrDocumentActionButtons document={document} origin={originState} onViewerPage />

  let area
  if (errorMessage !== null) {
    area = <p className="text-danger mb-0">{errorMessage}</p>
  } else if (document === null || id === undefined) {
    area = (
      <span className="d-inline-flex align-items-center gap-2">
        <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
        {t('common.loading')}
      </span>
    )
  } else if (viewerKind === 'image') {
    area = <DmsImageViewer id={id} fileName={fileName} />
  } else if (viewerKind === 'pdf') {
    area = <DmsPdfViewer id={id} fileName={fileName} />
  } else if (viewerKind === 'text') {
    area = <DmsTextViewer id={id} fileName={fileName} />
  } else if (viewerKind === 'word' || viewerKind === 'spreadsheet') {
    const loadingViewer = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
    area = (
      <Suspense fallback={loadingViewer}>
        {viewerKind === 'word' ? <DmsWordViewer id={id} fileName={fileName} /> : <DmsSpreadsheetViewer id={id} fileName={fileName} />}
      </Suspense>
    )
  } else {
    area = <p className="text-warning mb-0">{t('dms.viewer.noViewer')}</p>
  }

  return (
    <div className="viewer-page">
      <Subheader status={status} actions={actions} sticky />
      <div className="viewer-area">{area}</div>
      {document && (
        <Popup open={hooksOpen} title={t('dms.viewer.hooksTitle', { name: fileName })} onClose={() => setHooksOpen(false)}>
          <SolrHookFields hooks={hooks} language={language} />
        </Popup>
      )}
    </div>
  )
}
