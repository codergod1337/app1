import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { useOrigin, type OriginState } from '../../components/Origin.ts'
import { updateSolrHookGroup, type SolrHookGroup } from './SolrHookGroup.ts'
import { SolrHookGroupForm } from './SolrHookGroupForm.tsx'

const FORM_ID = 'edit-solr-hook-group-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/solrmanager/hookgroups'

/** State beim Öffnen: der origin und der key der Gruppe. Der key steht nicht in der URL, dort sind nur guid und Long-ID erlaubt. */
export interface EditSolrHookGroupState extends OriginState {
  solrHookGroupKey: string
}

/** Eine Hook-Gruppe bearbeiten. Abbrechen und Speichern springen zum origin. */
export function EditSolrHookGroupTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const solrHookGroupKey = (useLocation().state as Partial<EditSolrHookGroupState> | null)?.solrHookGroupKey
  // Die Gruppen liegen in den Stammdaten: die gesuchte heraussuchen
  const { solrHookGroups, errorMessage: loadErrorMessage, reloadMasterData } = useMasterData()
  const solrHookGroup = solrHookGroups?.find((candidate) => candidate.key === solrHookGroupKey) ?? null
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)

  function saveSolrHookGroup(changedSolrHookGroupData: SolrHookGroup) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateSolrHookGroup(changedSolrHookGroupData)
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
  if (errorMessage === null && solrHookGroups !== null && solrHookGroup === null) {
    errorMessage = t('solrmanager.hookGroups.notFound')
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {solrHookGroups === null && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
      )}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={() => navigate(originPath)}>
          {t('common.cancel')}
        </button>
        <button type="submit" form={FORM_ID} className="button-save" disabled={saving || solrHookGroup === null}>
          {t('common.save')}
        </button>
      </span>
    </>
  )

  return (
    <ContentBox
      title={
        solrHookGroup
          ? t('solrmanager.hookGroups.editTitle', { key: solrHookGroup.key })
          : t('solrmanager.hookGroups.editTitleNone')
      }
      footer={footer}
      language={{ value: language, onChange: setLanguage }}
    >
      {/* Erst mit den geladenen Daten anzeigen, damit das Formular mit ihnen startet */}
      {solrHookGroup !== null && (
        <SolrHookGroupForm
          formId={FORM_ID}
          initialSolrHookGroup={solrHookGroup}
          language={language}
          disabled={saving}
          onSubmit={saveSolrHookGroup}
        />
      )}
    </ContentBox>
  )
}
