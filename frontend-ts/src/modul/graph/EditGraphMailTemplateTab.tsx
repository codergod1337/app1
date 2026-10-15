import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { ConfirmCancelButtons } from '../../components/ConfirmCancelButtons.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useOrigin, type OriginState } from '../../components/Origin.ts'
import { updateGraphMailTemplate, type GraphMailTemplate } from './GraphMailTemplate.ts'
import { GraphMailTemplateForm } from './GraphMailTemplateForm.tsx'
import { useAllGraphMailTemplates, useAllGraphMailTemplateTypes } from './useGraphMailTemplates.ts'

const FORM_ID = 'edit-graph-mail-template-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/graph/mailtemplates'

/** State beim Öffnen: der origin und der key der Vorlage. Der key steht nicht in der URL, dort sind nur guid und Long-ID erlaubt. */
export interface EditGraphMailTemplateState extends OriginState {
  graphMailTemplateKey: string
}

/** Eine Mailvorlage bearbeiten. Abbrechen und Speichern springen zum origin. */
export function EditGraphMailTemplateTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const graphMailTemplateKey = (useLocation().state as Partial<EditGraphMailTemplateState> | null)
    ?.graphMailTemplateKey
  const { graphMailTemplates, errorMessage: loadErrorMessage } = useAllGraphMailTemplates()
  const { graphMailTemplateTypes, errorMessage: typesErrorMessage } = useAllGraphMailTemplateTypes()
  const graphMailTemplate = graphMailTemplates?.find((candidate) => candidate.key === graphMailTemplateKey) ?? null
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)

  function saveGraphMailTemplate(changedGraphMailTemplateData: GraphMailTemplate) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateGraphMailTemplate(changedGraphMailTemplateData)
      .then(() => navigate(originPath))
      .catch((error: unknown) => {
        setSaveErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setSaving(false)
      })
  }

  let errorMessage = loadErrorMessage ?? typesErrorMessage ?? saveErrorMessage
  if (errorMessage === null && graphMailTemplates !== null && graphMailTemplate === null) {
    errorMessage = t('graph.mailTemplates.notFound')
  }
  const loaded = graphMailTemplate !== null && graphMailTemplateTypes !== null

  // dasselbe Paar im Kopf und im Footer
  const buttons = (
    <ConfirmCancelButtons
      onCancel={() => navigate(originPath)}
      confirmLabel={t('common.save')}
      confirmForm={FORM_ID}
      disabled={!loaded}
      busy={saving}
    />
  )
  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {!loaded && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
      )}
      {buttons}
    </>
  )

  return (
    <ContentBox
      title={
        graphMailTemplate
          ? t('graph.mailTemplates.editTitle', { key: graphMailTemplate.key })
          : t('graph.mailTemplates.editTitleNone')
      }
      actions={buttons}
      footer={footer}
      language={{ value: language, onChange: setLanguage }}
    >
      {/* Erst mit den geladenen Daten anzeigen, damit das Formular mit ihnen startet */}
      {loaded && (
        <GraphMailTemplateForm
          formId={FORM_ID}
          initialGraphMailTemplate={graphMailTemplate}
          graphMailTemplateTypes={graphMailTemplateTypes}
          language={language}
          disabled={saving}
          onSubmit={saveGraphMailTemplate}
        />
      )}
    </ContentBox>
  )
}
