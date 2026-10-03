import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { useOrigin, type OriginState } from '../../components/Origin.ts'
import { updateSolrHook, type SolrHook } from './SolrHook.ts'
import { SolrHookForm } from './SolrHookForm.tsx'

const FORM_ID = 'edit-solr-hook-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/solrmanager/hooks'

/** State beim Öffnen: der origin und der key des Hooks. Der key steht nicht in der URL, dort sind nur guid und Long-ID erlaubt. */
export interface EditSolrHookState extends OriginState {
  solrHookKey: string
}

/** Einen Hook bearbeiten. Abbrechen und Speichern springen zum origin. */
export function EditSolrHookTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const solrHookKey = (useLocation().state as Partial<EditSolrHookState> | null)?.solrHookKey
  // Die Hooks liegen in den Stammdaten: den gesuchten heraussuchen
  const { solrHooks, errorMessage: loadErrorMessage, reloadMasterData } = useMasterData()
  const solrHook = solrHooks?.find((candidate) => candidate.key === solrHookKey) ?? null
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)

  function saveSolrHook(changedSolrHookData: SolrHook) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateSolrHook(changedSolrHookData)
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
  if (errorMessage === null && solrHooks !== null && solrHook === null) {
    errorMessage = t('solrmanager.hooks.notFound')
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {solrHooks === null && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
      )}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={() => navigate(originPath)}>
          {t('common.cancel')}
        </button>
        <button type="submit" form={FORM_ID} className="button-save" disabled={saving || solrHook === null}>
          {t('common.save')}
        </button>
      </span>
    </>
  )

  return (
    <ContentBox
      title={
        solrHook ? t('solrmanager.hooks.editTitle', { key: solrHook.key }) : t('solrmanager.hooks.editTitleNone')
      }
      footer={footer}
      language={{ value: language, onChange: setLanguage }}
    >
      {/* Erst mit den geladenen Daten anzeigen, damit das Formular mit ihnen startet */}
      {solrHook !== null && (
        <SolrHookForm
          formId={FORM_ID}
          initialSolrHook={solrHook}
          language={language}
          disabled={saving}
          onSubmit={saveSolrHook}
        />
      )}
    </ContentBox>
  )
}
