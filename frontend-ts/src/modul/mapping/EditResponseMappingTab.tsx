import { type FormEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ConfirmCancelButtons } from '../../components/ConfirmCancelButtons.tsx'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useOrigin, type OriginState } from '../../components/Origin.ts'
import { getResponseMapping, MAPPING_PATH, type ResponseMappingWithLines, updateResponseMapping } from './ResponseMapping.ts'
import { ResponseMappingEditor } from './ResponseMappingEditor.tsx'

const FORM_ID = 'edit-response-mapping-form'

/** Ohne mitgegebenen origin geht es hierhin zurück */
const FALLBACK_PATH = `${MAPPING_PATH}/overview`

/** State beim Öffnen: der origin und die id des Mappings, als Router-State statt in der Adresse */
export interface EditResponseMappingState extends OriginState {
  responseMappingId: number
}

/** Ein Gesamt-Mapping bearbeiten, derselbe Editor wie im Entwurf. Abbrechen und Speichern springen zum origin. */
export function EditResponseMappingTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const originPath = useOrigin()?.path ?? FALLBACK_PATH
  const responseMappingId = (useLocation().state as Partial<EditResponseMappingState> | null)?.responseMappingId
  const [responseMapping, setResponseMapping] = useState<ResponseMappingWithLines | null>(null)
  const [loadErrorMessage, setLoadErrorMessage] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)

  // ohne id im State gibt es nichts zu laden: beim Rendern abgeleitet, nicht im Effekt gesetzt
  const missingIdErrorMessage = responseMappingId === undefined ? t('mapping.overview.notFound') : null

  useEffect(() => {
    const unknownError = t('common.unknownError')
    if (responseMappingId === undefined) {
      return
    }
    let current = true
    getResponseMapping(responseMappingId)
      .then((result) => {
        if (current) {
          setResponseMapping(result.data)
        }
      })
      .catch((error: unknown) => {
        if (current) {
          setLoadErrorMessage(error instanceof RequestError ? error.message : unknownError)
        }
      })
    return () => {
      current = false
    }
  }, [responseMappingId, t])

  function save(event: FormEvent) {
    event.preventDefault()
    if (responseMapping === null) {
      return
    }
    setSaving(true)
    setSaveErrorMessage(null)
    updateResponseMapping(responseMapping)
      .then(() => navigate(originPath))
      .catch((error: unknown) => {
        setSaveErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setSaving(false)
      })
  }

  const errorMessage = missingIdErrorMessage ?? loadErrorMessage ?? saveErrorMessage

  // dasselbe Paar im Kopf und im Footer
  const buttons = (
    <ConfirmCancelButtons
      onCancel={() => navigate(originPath)}
      confirmLabel={t('common.save')}
      confirmForm={FORM_ID}
      disabled={responseMapping === null}
      busy={saving}
    />
  )
  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {responseMapping === null && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
      )}
      {buttons}
    </>
  )

  return (
    <ContentBox
      title={responseMapping ? t('mapping.overview.editTitle', { key: responseMapping.responseMapping.key }) : t('mapping.overview.editTitleNone')}
      actions={buttons}
      footer={footer}
    >
      {responseMapping !== null && (
        <form id={FORM_ID} onSubmit={save}>
          <ResponseMappingEditor idPrefix="edit-response-mapping" value={responseMapping} onChange={setResponseMapping} disabled={saving} keyLocked />
        </form>
      )}
    </ContentBox>
  )
}
