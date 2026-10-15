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
import { updateEntityState, type EntityState } from '../entitystate/EntityState.ts'
import { EntityStateForm } from '../entitystate/EntityStateForm.tsx'

const FORM_ID = 'edit-entity-state-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = '/admin/entitystates'

/** State beim Öffnen: der origin und der key des Status. Der key steht nicht in der URL, dort sind nur guid und Long-ID erlaubt. */
export interface EditEntityStateState extends OriginState {
  entityStateKey: string
}

/** Einen Status bearbeiten. Abbrechen und Speichern springen zum origin. */
export function EditEntityStateTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const entityStateKey = (useLocation().state as Partial<EditEntityStateState> | null)?.entityStateKey
  // Die Status liegen in den Stammdaten: den gesuchten heraussuchen
  const { entityStates, errorMessage: loadErrorMessage, reloadMasterData } = useMasterData()
  const entityState = entityStates?.find((candidate) => candidate.key === entityStateKey) ?? null
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  const [language, setLanguage] = useState<LanguageCode>(useLanguage().language)

  function saveEntityState(changedEntityStateData: EntityState) {
    setSaving(true)
    setSaveErrorMessage(null)
    updateEntityState(changedEntityStateData)
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
  if (errorMessage === null && entityStates !== null && entityState === null) {
    errorMessage = t('entitystate.notFound')
  }

  // dasselbe Paar im Kopf und im Footer
  const buttons = (
    <ConfirmCancelButtons
      onCancel={() => navigate(originPath)}
      confirmLabel={t('common.save')}
      confirmForm={FORM_ID}
      disabled={entityState === null}
      busy={saving}
    />
  )
  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {entityStates === null && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
      )}
      {buttons}
    </>
  )

  return (
    <ContentBox
      title={entityState ? t('entitystate.editTitle', { key: entityState.key }) : t('entitystate.editTitleNone')}
      actions={buttons}
      footer={footer}
      language={{ value: language, onChange: setLanguage }}
    >
      {/* Erst mit den geladenen Daten anzeigen, damit das Formular mit ihnen startet */}
      {entityState !== null && (
        <EntityStateForm
          formId={FORM_ID}
          initialEntityState={entityState}
          language={language}
          disabled={saving}
          onSubmit={saveEntityState}
        />
      )}
    </ContentBox>
  )
}
