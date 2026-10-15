import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { ConfirmCancelButtons } from '../../components/ConfirmCancelButtons.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { useOrigin, type OriginState } from '../../components/Origin.ts'
import { updateMessageSubClass, type MessageSubClass } from '../messagesubclass/MessageSubClass.ts'
import { MessageSubClassForm } from '../messagesubclass/MessageSubClassForm.tsx'
import { MESSAGE_SUB_CLASSES_PATH } from './MessageSubClassesTab.tsx'

const FORM_ID = 'edit-message-sub-class-form'

/**
 * State beim Öffnen: der origin und der key der Nachrichtenart. Der key steht nicht in der URL, dort sind nur guid und
 * Long-ID erlaubt.
 */
export interface EditMessageSubClassState extends OriginState {
  messageSubClassKey: string
}

/** Eine Nachrichtenart bearbeiten. Abbrechen und Speichern springen zum origin. */
export function EditMessageSubClassTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? MESSAGE_SUB_CLASSES_PATH
  const messageSubClassKey = (useLocation().state as Partial<EditMessageSubClassState> | null)?.messageSubClassKey
  // Die Arten liegen in den Stammdaten: die gesuchte heraussuchen
  const { messageSubClasses, errorMessage: loadErrorMessage, reloadMasterData } = useMasterData()
  const messageSubClass = messageSubClasses?.find((candidate) => candidate.key === messageSubClassKey) ?? null
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)

  function saveMessageSubClass(changedMessageSubClassData: MessageSubClass) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateMessageSubClass(changedMessageSubClassData)
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
  if (errorMessage === null && messageSubClasses !== null && messageSubClass === null) {
    errorMessage = t('messagesubclass.notFound')
  }

  // dasselbe Paar im Kopf und im Footer
  const buttons = (
    <ConfirmCancelButtons
      onCancel={() => navigate(originPath)}
      confirmLabel={t('common.save')}
      confirmForm={FORM_ID}
      disabled={messageSubClass === null}
      busy={saving}
    />
  )
  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {messageSubClasses === null && errorMessage === null && <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />}
      {buttons}
    </>
  )

  return (
    <ContentBox
      title={messageSubClass ? t('messagesubclass.editTitle', { key: messageSubClass.key }) : t('messagesubclass.editTitleEmpty')}
      actions={buttons}
      footer={footer}
      language={{ value: language, onChange: setLanguage }}
    >
      {/* Erst mit den geladenen Daten anzeigen, damit das Formular mit ihnen startet */}
      {messageSubClass !== null && (
        <MessageSubClassForm formId={FORM_ID} initialMessageSubClass={messageSubClass} language={language} disabled={saving} onSubmit={saveMessageSubClass} />
      )}
    </ContentBox>
  )
}
