import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { ConfirmCancelButtons } from '../../components/ConfirmCancelButtons.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useOrigin } from '../../components/Origin.ts'
import { createGraphMailTemplate, type GraphMailTemplate } from './GraphMailTemplate.ts'
import { GraphMailTemplateForm } from './GraphMailTemplateForm.tsx'
import { useAllGraphMailTemplateTypes } from './useGraphMailTemplates.ts'

const FORM_ID = 'new-graph-mail-template-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/graph/mailtemplates'

/** Neue Mailvorlage anlegen. Abbrechen und Anlegen springen zum origin. */
export function NewGraphMailTemplateTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const { graphMailTemplateTypes, errorMessage: loadErrorMessage } = useAllGraphMailTemplateTypes()
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)

  function saveNewGraphMailTemplate(newGraphMailTemplateData: GraphMailTemplate) {
    setSaving(true)
    setSaveErrorMessage(null)
    createGraphMailTemplate(newGraphMailTemplateData)
      .then(() => navigate(originPath))
      .catch((error: unknown) => {
        setSaveErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setSaving(false)
      })
  }

  const errorMessage = loadErrorMessage ?? saveErrorMessage

  // dasselbe Paar im Kopf und im Footer
  const buttons = (
    <ConfirmCancelButtons
      onCancel={() => navigate(originPath)}
      confirmLabel={t('common.create')}
      confirmForm={FORM_ID}
      disabled={graphMailTemplateTypes === null}
      busy={saving}
    />
  )
  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {graphMailTemplateTypes === null && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
      )}
      {buttons}
    </>
  )

  return (
    <ContentBox
      title="new GraphMailTemplate()"
      actions={buttons}
      footer={footer}
      language={{ value: language, onChange: setLanguage }}
    >
      {/* Erst mit den Typen anzeigen, damit die Auswahl und die Platzhalter von Anfang an da sind */}
      {graphMailTemplateTypes !== null && (
        <GraphMailTemplateForm
          formId={FORM_ID}
          graphMailTemplateTypes={graphMailTemplateTypes}
          language={language}
          disabled={saving}
          onSubmit={saveNewGraphMailTemplate}
        />
      )}
    </ContentBox>
  )
}
