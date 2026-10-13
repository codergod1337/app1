import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useOrigin } from '../../components/Origin.ts'
import { type AbasInfosys, createAbasInfosys } from './AbasInfosys.ts'
import { AbasInfosysForm } from './AbasInfosysForm.tsx'
import { ABAS_INFOSYS_PATH } from './AbasInfosysCallsTab.tsx'

const FORM_ID = 'new-abas-infosys-form'

/** Neuen Infosystem-Aufruf hinterlegen. Abbrechen und Anlegen springen zum origin. */
export function NewAbasInfosysTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? ABAS_INFOSYS_PATH
  const [saving, setSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)

  function save(newAbasInfosysData: AbasInfosys) {
    setSaving(true)
    setErrorMessage(null)
    createAbasInfosys(newAbasInfosysData)
      .then(() => navigate(originPath))
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
    <ContentBox title="new AbasInfosys()" footer={footer} language={{ value: language, onChange: setLanguage }}>
      <AbasInfosysForm formId={FORM_ID} language={language} disabled={saving} onSubmit={save} />
    </ContentBox>
  )
}
