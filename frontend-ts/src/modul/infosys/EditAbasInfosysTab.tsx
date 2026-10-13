import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useOrigin, type OriginState } from '../../components/Origin.ts'
import { type AbasInfosys, updateAbasInfosys, useAbasInfosysList } from './AbasInfosys.ts'
import { AbasInfosysForm } from './AbasInfosysForm.tsx'
import { ABAS_INFOSYS_PATH } from './AbasInfosysCallsTab.tsx'

const FORM_ID = 'edit-abas-infosys-form'

/** State beim Öffnen: der origin und der key des Aufrufs. Der key steht nicht in der URL, dort sind nur guid und Long-ID erlaubt. */
export interface EditAbasInfosysState extends OriginState {
  abasInfosysKey: string
}

/** Einen hinterlegten Infosystem-Aufruf bearbeiten. Abbrechen und Speichern springen zum origin. */
export function EditAbasInfosysTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? ABAS_INFOSYS_PATH
  const abasInfosysKey = (useLocation().state as Partial<EditAbasInfosysState> | null)?.abasInfosysKey
  const { abasInfosysList, errorMessage: loadErrorMessage } = useAbasInfosysList()
  const abasInfosys = abasInfosysList?.find((candidate) => candidate.key === abasInfosysKey) ?? null
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)

  function save(changedAbasInfosysData: AbasInfosys) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateAbasInfosys(changedAbasInfosysData)
      .then(() => navigate(originPath))
      .catch((error: unknown) => {
        setSaveErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setSaving(false)
      })
  }

  let errorMessage = loadErrorMessage ?? saveErrorMessage
  if (errorMessage === null && abasInfosysList !== null && abasInfosys === null) {
    errorMessage = t('infosys.calls.notFound')
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {abasInfosysList === null && errorMessage === null && <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={saving} onClick={() => navigate(originPath)}>
          {t('common.cancel')}
        </button>
        <button type="submit" form={FORM_ID} className="button-save" disabled={saving || abasInfosys === null}>
          {t('common.save')}
        </button>
      </span>
    </>
  )

  return (
    <ContentBox
      title={abasInfosys ? t('infosys.calls.editTitle', { key: abasInfosys.key }) : t('infosys.calls.editTitleNone')}
      footer={footer}
      language={{ value: language, onChange: setLanguage }}
    >
      {abasInfosys !== null && <AbasInfosysForm formId={FORM_ID} initialAbasInfosys={abasInfosys} language={language} disabled={saving} onSubmit={save} />}
    </ContentBox>
  )
}
