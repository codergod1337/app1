import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { useOrigin, type OriginState } from '../../components/Origin.ts'
import { updateSolrCore, type SolrCore } from './SolrCore.ts'
import { SolrCoreForm } from './SolrCoreForm.tsx'

const FORM_ID = 'edit-solr-core-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/solrmanager/cores'

/** State beim Öffnen: der origin und der key des Kerns. */
export interface EditSolrCoreState extends OriginState {
  solrCoreKey: string
}

/** Einen Kern bearbeiten. Abbrechen und Speichern springen zum origin. */
export function EditSolrCoreTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const solrCoreKey = (useLocation().state as Partial<EditSolrCoreState> | null)?.solrCoreKey
  // Die Kerne liegen in den Stammdaten: den gesuchten heraussuchen
  const { solrCores, errorMessage: loadErrorMessage, reloadMasterData } = useMasterData()
  const solrCore = solrCores?.find((candidate) => candidate.key === solrCoreKey) ?? null
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)

  function saveSolrCore(changedSolrCoreData: SolrCore) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateSolrCore(changedSolrCoreData)
      .then(() => {
        reloadMasterData()
        navigate(originPath)
      })
      .catch((error: unknown) => {
        setSaveErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setSaving(false)
      })
  }

  let errorMessage = loadErrorMessage ?? saveErrorMessage
  if (errorMessage === null && solrCores !== null && solrCore === null) {
    errorMessage = t('solrmanager.cores.notFound')
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {solrCores === null && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
      )}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={() => navigate(originPath)}>
          {t('common.cancel')}
        </button>
        <button type="submit" form={FORM_ID} className="button-save" disabled={saving || solrCore === null}>
          {t('common.save')}
        </button>
      </span>
    </>
  )

  return (
    <ContentBox
      title={
        solrCore ? t('solrmanager.cores.editTitle', { key: solrCore.key }) : t('solrmanager.cores.editTitleNone')
      }
      footer={footer}
      language={{ value: language, onChange: setLanguage }}
    >
      {/* Erst mit den geladenen Daten anzeigen, damit das Formular mit ihnen startet */}
      {solrCore !== null && (
        <SolrCoreForm
          formId={FORM_ID}
          initialSolrCore={solrCore}
          language={language}
          disabled={saving}
          onSubmit={saveSolrCore}
        />
      )}
    </ContentBox>
  )
}
