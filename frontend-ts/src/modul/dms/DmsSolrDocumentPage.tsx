import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useOrigin } from '../../components/Origin.ts'
import { dmsSolrDocumentPath, getDmsSolrDocument, type DmsSolrDocumentForClient } from './Dms.ts'
import { DmsSolrDocumentDetailsBox } from './DmsSolrDocumentDetailsBox.tsx'
import { DmsSolrDocumentEditBox } from './DmsSolrDocumentEditBox.tsx'

/** Wohin Schließen führt, wenn niemand eine Herkunft mitgab */
const FALLBACK_PATH = '/dms/resolve'

interface DmsSolrDocumentPageProps {
  mode: 'details' | 'edit'
}

/**
 * Der dünne Rahmen um ein DMS-Dokument unter /dms/solr-document/<id> und …/edit: lädt das Dokument mit der
 * Leseprüfung des Users und zeigt die Detailbox oder die Bearbeiten-Box. Die Boxen selbst kennen keinen Rahmen, damit
 * sie später genauso im Dokumente-Tab der Artikelmappe stehen. Schließen führt zur Herkunft, sonst zum Auflösen.
 */
export function DmsSolrDocumentPage({ mode }: DmsSolrDocumentPageProps) {
  const { t } = useTranslation()
  const { id } = useParams()
  const navigate = useNavigate()
  const origin = useOrigin()
  const [loaded, setLoaded] = useState<DmsSolrDocumentForClient | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  function load() {
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
  }

  useEffect(() => {
    load()
    // neu laden, wenn eine andere id in der Adresse steht
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const originState = origin !== null ? { origin } : undefined
  const closePath = origin?.path ?? FALLBACK_PATH

  if (errorMessage !== null) {
    return (
      <ContentBox title={t('dms.document.title')}>
        <p className="text-danger mb-0">{errorMessage}</p>
      </ContentBox>
    )
  }
  if (loaded === null || id === undefined) {
    return (
      <ContentBox title={t('dms.document.title')}>
        <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
      </ContentBox>
    )
  }
  if (mode === 'edit') {
    return (
      <DmsSolrDocumentEditBox
        key={id}
        loaded={loaded}
        onSaved={(saved) => {
          setLoaded(saved)
          navigate(dmsSolrDocumentPath(id), { state: originState })
        }}
        onCancel={() => navigate(dmsSolrDocumentPath(id), { state: originState })}
      />
    )
  }
  return (
    <DmsSolrDocumentDetailsBox
      loaded={loaded}
      origin={originState}
      onClose={() => navigate(closePath)}
      onDeleted={load}
    />
  )
}
