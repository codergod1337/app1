import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { ConfirmCancelButtons } from '../../components/ConfirmCancelButtons.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { useOrigin } from '../../components/Origin.ts'
import { createSolrCore, type SolrCore } from './SolrCore.ts'
import { SolrCoreForm } from './SolrCoreForm.tsx'

const FORM_ID = 'new-solr-core-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/solrmanager/cores'

/** Neuen Kern anlegen. Abbrechen und Anlegen springen zum origin. */
export function NewSolrCoreTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const [saving, setSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)
  const { reloadMasterData } = useMasterData()

  function saveNewSolrCore(newSolrCoreData: SolrCore) {
    setSaving(true)
    setErrorMessage(null)
    createSolrCore(newSolrCoreData)
      .then(() => {
        reloadMasterData()
        navigate(originPath)
      })
      .catch((error: unknown) => {
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setSaving(false)
      })
  }

  // dasselbe Paar im Kopf und im Footer
  const buttons = (
    <ConfirmCancelButtons
      onCancel={() => navigate(originPath)}
      confirmLabel={t('common.create')}
      confirmForm={FORM_ID}
      busy={saving}
    />
  )
  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {buttons}
    </>
  )

  return (
    <ContentBox title="new SolrCore()" actions={buttons} footer={footer} language={{ value: language, onChange: setLanguage }}>
      <SolrCoreForm formId={FORM_ID} language={language} disabled={saving} onSubmit={saveNewSolrCore} />
    </ContentBox>
  )
}
