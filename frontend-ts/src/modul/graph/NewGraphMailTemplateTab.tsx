import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
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

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {graphMailTemplateTypes === null && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
      )}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={() => navigate(originPath)}>
          {t('common.cancel')}
        </button>
        <button
          type="submit"
          form={FORM_ID}
          className="button-save"
          disabled={saving || graphMailTemplateTypes === null}
        >
          {t('common.create')}
        </button>
      </span>
    </>
  )

  return (
    <ContentBox title="new GraphMailTemplate()" footer={footer} language={{ value: language, onChange: setLanguage }}>
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
