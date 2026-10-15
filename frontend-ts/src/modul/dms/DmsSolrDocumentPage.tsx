import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useOrigin } from '../../components/Origin.ts'
import {
  dmsSolrDocumentPath,
  dmsVersionChainHead,
  getDmsSolrDocument,
  getDmsVersionChain,
  type DmsSolrDocumentForClient,
} from './Dms.ts'
import { DmsSolrDocumentDetailsBox } from './DmsSolrDocumentDetailsBox.tsx'
import { DmsSolrDocumentEditBox } from './DmsSolrDocumentEditBox.tsx'
import { DmsSolrDocumentRevisionsBox } from './DmsSolrDocumentRevisionsBox.tsx'
import { DmsVersionChainBox } from './DmsVersionChainBox.tsx'

/** Wohin es nach dem Löschen geht, wenn niemand eine Herkunft mitgab */
const FALLBACK_PATH = '/dms/resolve'

interface DmsSolrDocumentPageProps {
  mode: 'details' | 'edit'
}

/**
 * Der dünne Rahmen um ein DMS-Dokument unter /dms/solr-document/<id>, …/edit und /dms/version-chain/<versionChain>:
 * lädt das Dokument mit der Leseprüfung des Users und zeigt die Detailbox oder die Bearbeiten-Box, unter den Details
 * den Fahrplan der Versionskette und die Revisionen, wenn es welche gibt. Über die Kette landet man bei der geltenden
 * Fassung (ACTIVE, sonst die höchste): der feste Verweis auf eine Datei, der jede neue Fassung überlebt. Die Boxen
 * selbst kennen keinen Rahmen, damit sie später genauso im Dokumente-Tab der Artikelmappe stehen. Ein Schließen gibt es
 * nicht, raus geht es über das „zurück zu …“ im Header; nach dem Löschen zur Herkunft, sonst zum Auflösen.
 */
export function DmsSolrDocumentPage({ mode }: DmsSolrDocumentPageProps) {
  const { t } = useTranslation()
  const { id: idParam, versionChain } = useParams()
  const navigate = useNavigate()
  const origin = useOrigin()
  /** die id, die die Seite zeigt: aus der Adresse, oder über die Kette der Kopf */
  const [id, setId] = useState<string | null>(idParam ?? null)
  const [loaded, setLoaded] = useState<DmsSolrDocumentForClient | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  function fail(error: unknown) {
    setLoaded(null)
    setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
  }

  function load(documentId: string) {
    setErrorMessage(null)
    getDmsSolrDocument(documentId)
      .then(({ data }) => setLoaded(data))
      .catch(fail)
  }

  function reload() {
    if (id !== null) {
      load(id)
    }
  }

  useEffect(() => {
    // die Adresse nennt eine id: sie gilt; nennt sie eine Kette, löst die Seite erst den Kopf auf
    if (idParam !== undefined) {
      setId(idParam)
      return
    }
    if (versionChain === undefined) {
      return
    }
    setId(null)
    setLoaded(null)
    setErrorMessage(null)
    getDmsVersionChain(versionChain)
      .then(({ data }) => {
        const head = dmsVersionChainHead(data)
        if (head === null || typeof head.id !== 'string') {
          setErrorMessage(t('dms.batch.chainNotFound'))
          return
        }
        setId(head.id)
      })
      .catch(fail)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idParam, versionChain])

  useEffect(() => {
    if (id !== null) {
      load(id)
    }
    // neu laden, wenn eine andere id gilt
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
  if (loaded === null || id === null) {
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
  const chain = loaded.document.versionChain
  return (
    <>
      <DmsSolrDocumentDetailsBox
        loaded={loaded}
        origin={originState}
        // nach dem weichen Löschen gibt es das Dokument für den User nicht mehr: zurück zur Herkunft
        onDeleted={() => navigate(closePath)}
      />
      {typeof chain === 'string' && <DmsVersionChainBox versionChain={chain} currentId={id} origin={originState} />}
      <DmsSolrDocumentRevisionsBox id={id} document={loaded.document} onRestored={reload} />
    </>
  )
}
