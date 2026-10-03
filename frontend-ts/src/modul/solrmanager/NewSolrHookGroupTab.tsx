import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { useOrigin } from '../../components/Origin.ts'
import { createSolrHookGroup, type SolrHookGroup } from './SolrHookGroup.ts'
import { SolrHookGroupForm } from './SolrHookGroupForm.tsx'

const FORM_ID = 'new-solr-hook-group-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/solrmanager/hookgroups'

/** Neue Hook-Gruppe anlegen. Abbrechen und Anlegen springen zum origin. */
export function NewSolrHookGroupTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const [saving, setSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)
  const { reloadMasterData } = useMasterData()

  function saveNewSolrHookGroup(newSolrHookGroupData: SolrHookGroup) {
    setSaving(true)
    setErrorMessage(null)
    createSolrHookGroup(newSolrHookGroupData)
      .then(() => {
        reloadMasterData()
        navigate(originPath)
      })
      .catch((error: unknown) => {
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setSaving(false)
      })
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={() => navigate(originPath)}>
          {t('common.cancel')}
        </button>
        <button type="submit" form={FORM_ID} className="button-save" disabled={saving}>
          {t('common.create')}
        </button>
      </span>
    </>
  )

  return (
    <ContentBox title="new SolrHookGroup()" footer={footer} language={{ value: language, onChange: setLanguage }}>
      <SolrHookGroupForm formId={FORM_ID} language={language} disabled={saving} onSubmit={saveNewSolrHookGroup} />
    </ContentBox>
  )
}
