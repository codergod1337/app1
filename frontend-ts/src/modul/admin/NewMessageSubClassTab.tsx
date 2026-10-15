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
import { createMessageSubClass, type MessageSubClass } from '../messagesubclass/MessageSubClass.ts'
import { MessageSubClassForm } from '../messagesubclass/MessageSubClassForm.tsx'
import { MESSAGE_SUB_CLASSES_PATH } from './MessageSubClassesTab.tsx'

const FORM_ID = 'new-message-sub-class-form'

/** Neue Nachrichtenart anlegen. Abbrechen und Anlegen springen zum origin, ohne ihn zum Tab „Nachrichtenarten“. */
export function NewMessageSubClassTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? MESSAGE_SUB_CLASSES_PATH
  const [saving, setSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)
  const { reloadMasterData } = useMasterData()

  function saveNewMessageSubClass(newMessageSubClassData: MessageSubClass) {
    setSaving(true)
    setErrorMessage(null)
    createMessageSubClass(newMessageSubClassData)
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
    <ContentBox title="new MessageSubClass()" actions={buttons} footer={footer} language={{ value: language, onChange: setLanguage }}>
      <MessageSubClassForm formId={FORM_ID} language={language} disabled={saving} onSubmit={saveNewMessageSubClass} />
    </ContentBox>
  )
}
