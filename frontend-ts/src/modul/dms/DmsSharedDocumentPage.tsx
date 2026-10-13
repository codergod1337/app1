import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DmsSolrDocumentDetailsBox } from './DmsSolrDocumentDetailsBox.tsx'
import { dmsShareDownloadUrl, getDmsSharedDocumentByToken, type DmsSharedDocument } from './DmsShare.ts'

/**
 * Die Station „Detailansicht“ für einen Empfänger ohne Konto (User 2026-10-09): Der Link aus dem Abholschein öffnet
 * diese Seite, sie löst den Schein am öffentlichen DMS-Endpunkt ein und zeigt die freigegebenen Felder in derselben
 * Detailbox wie die Dokumentseite im DMS (User 2026-10-10), nur ohne die Wege ins DMS. Jeder Aufruf zählt als
 * Einlösung. Darf der Download dazu, läuft er über denselben Schein an der Station „nur Download“ und zählt noch
 * einmal. Ein ungültiger, abgelaufener oder verbrauchter Schein ist ein 404.
 */
export function DmsSharedDocumentPage() {
  const { t, i18n } = useTranslation()
  const { token } = useParams()
  const [shared, setShared] = useState<DmsSharedDocument | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (token === undefined) {
      return
    }
    let cancelled = false
    getDmsSharedDocumentByToken(token)
      .then((data) => {
        if (!cancelled) {
          setShared(data)
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setErrorMessage(
            error instanceof RequestError && error.status === 404
              ? t('dms.shared.notFound')
              : error instanceof RequestError
                ? error.message
                : t('common.unknownError'),
          )
        }
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token])

  if (token === undefined || errorMessage !== null) {
    return (
      <div className="main-content">
        <ContentBox title={t('dms.shared.title')}>
          <p className="text-danger mb-0">{errorMessage ?? t('dms.shared.notFound')}</p>
        </ContentBox>
      </div>
    )
  }
  if (shared === null) {
    return (
      <div className="main-content">
        <ContentBox title={t('dms.shared.title')}>
          <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
        </ContentBox>
      </div>
    )
  }

  return (
    <div className="main-content">
      <p className="small text-muted">
        {t('dms.shared.validity', {
          expiresAt: new Date(shared.expiresAt).toLocaleString(i18n.language),
          count: shared.usesLeft,
        })}
      </p>
      <DmsSolrDocumentDetailsBox
        loaded={{ document: shared.fieldValues, writeAclMatchForCurrentUser: false }}
        shared={{ downloadUrl: shared.binaryIncluded ? dmsShareDownloadUrl(token) : null }}
      />
    </div>
  )
}
