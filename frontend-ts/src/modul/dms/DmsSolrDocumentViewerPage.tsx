import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { translate } from '../../components/multilingual.ts'
import { useOrigin } from '../../components/Origin.ts'
import { dmsFileDownloadUrl, getDmsSolrDocument, type DmsSolrDocumentForClient } from './Dms.ts'
import { DmsSolrDocumentActionButtons } from './DmsSolrDocumentActionButtons.tsx'
import { dmsViewerKindOfFileExtension } from './dmsViewerByFileExtension.ts'

/** Wohin Schließen führt, wenn niemand eine Herkunft mitgab */
const FALLBACK_PATH = '/dms/resolve'

/**
 * Ein Bild im Browser, die Bytes über den Download-Weg: der Browser kann das selbst. Zwei Größen, ein Klick, wie ein
 * einzeln geöffnetes Bild im Browser: eingepasst (ganz zu sehen) oder 1:1 in echten Pixeln, dann rollt die Fläche.
 */
function DmsImageViewer({ id, fileName }: { id: string; fileName: string }) {
  const { t } = useTranslation()
  const [nativeSize, setNativeSize] = useState(false)
  return (
    <div style={{ overflow: 'auto', maxHeight: '80vh' }}>
      <Hoverlay text={nativeSize ? t('dms.viewer.fitAgain') : t('dms.viewer.showNativeSize')}>
        <img
          src={dmsFileDownloadUrl(id)}
          alt={fileName}
          style={
            nativeSize
              ? { cursor: 'zoom-out', display: 'block' }
              : { cursor: 'zoom-in', display: 'block', maxWidth: '100%', maxHeight: '78vh', objectFit: 'contain' }
          }
          onClick={() => setNativeSize(!nativeSize)}
        />
      </Hoverlay>
    </div>
  )
}

/**
 * Die Seite, auf der eine Datei angezeigt wird: /dms/solr-document/<id>/viewer. In der URL steht nur die id, Name und
 * Beschreibung kommen aus dem Dokument, das mit der Leseprüfung des Users geladen wird. Welche Endung welchen Viewer
 * bekommt, sagt dmsViewerByFileExtension; ohne Viewer bleibt der Download.
 */
export function DmsSolrDocumentViewerPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const navigate = useNavigate()
  const origin = useOrigin()
  const { language } = useLanguage()
  const [loaded, setLoaded] = useState<DmsSolrDocumentForClient | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

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

  const closePath = origin?.path ?? FALLBACK_PATH
  const originState = origin !== null ? { origin } : undefined

  if (errorMessage !== null) {
    return (
      <ContentBox title={t('dms.viewer.title')}>
        <p className="text-danger mb-0">{errorMessage}</p>
      </ContentBox>
    )
  }
  if (loaded === null || id === undefined) {
    return (
      <ContentBox title={t('dms.viewer.title')}>
        <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
      </ContentBox>
    )
  }

  const document = loaded.document
  const fileName = typeof document.fileName === 'string' ? document.fileName : id
  const description = typeof document.fileDescription === 'string' ? translate(document.fileDescription, language) : ''
  const fileIndex = typeof document.fileIndex === 'string' ? document.fileIndex : null
  const viewerKind = dmsViewerKindOfFileExtension(document.fileExtension)

  const title = (
    <span className="d-inline-flex align-items-center gap-2 text-break">
      {description !== '' ? <Hoverlay text={description}>{fileName}</Hoverlay> : fileName}
      {fileIndex !== null && (
        <span className="fw-normal text-muted">
          {t('dms.document.fileIndex')}: {fileIndex}
        </span>
      )}
    </span>
  )

  const actions = (
    <span className="d-inline-flex align-items-center gap-2">
      <DmsSolrDocumentActionButtons document={document} origin={originState} />
      <button type="button" className="button-cancel" onClick={() => navigate(closePath)}>
        {t('common.close')}
      </button>
    </span>
  )

  return (
    <ContentBox title={title} actions={actions}>
      {viewerKind === 'image' ? (
        <DmsImageViewer id={id} fileName={fileName} />
      ) : (
        <p className="text-warning mb-0">{t('dms.viewer.noViewer')}</p>
      )}
    </ContentBox>
  )
}
