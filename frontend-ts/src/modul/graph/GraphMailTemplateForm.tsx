import { useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { KeyInput } from '../../components/KeyInput.tsx'
import { MultilingualInput } from '../../components/MultilingualInput.tsx'
import { showToast } from '../../components/toastStore.ts'
import {
  renderGraphMailTemplatePreview,
  type GraphMailTemplate,
  type GraphMailTemplatePreview,
  type GraphMailTemplateType,
  type GraphMailTemplateTypeForClient,
} from './GraphMailTemplate.ts'

/** Startwerte einer neuen Vorlage */
const NEW_GRAPH_MAIL_TEMPLATE: GraphMailTemplate = {
  key: '',
  type: 'LOGIN_2FA',
  displayName: '',
  description: '',
  subject: '',
  htmlBody: '',
  listingPosition: 0,
}

/** So lange nach dem letzten Tippen wartet das Muster, bevor es das Backend fragt */
const PREVIEW_DELAY_MS = 400

interface GraphMailTemplateFormProps {
  /** id des form-Elements, damit ein Knopf außerhalb (z. B. im contentbox-footer) es abschicken kann */
  formId: string
  /** Startwerte beim Bearbeiten. Fehlen sie, ist das Formular leer (neue Vorlage). */
  initialGraphMailTemplate?: GraphMailTemplate
  /** die Typen mit ihren Platzhaltergruppen, vom Backend */
  graphMailTemplateTypes: GraphMailTemplateTypeForClient[]
  /** Sprache der mehrsprachigen Felder, gewählt über die Flaggen der ContentBox; das Muster zeigt dieselbe Sprache */
  language: LanguageCode
  /** sperrt alle Felder, z. B. während gespeichert wird */
  disabled: boolean
  onSubmit: (graphMailTemplateData: GraphMailTemplate) => void
}

/**
 * Der Editor einer Mailvorlage, zum Anlegen und zum Bearbeiten. key und Typ stehen beim Bearbeiten fest. Rechts die
 * Platzhalter des Typs (Klick kopiert sie), darunter das Muster: Betreff und Text in der gewählten Sprache mit
 * Beispielwerten, vom Backend gerendert, kurz nach jedem Tippen neu. Speichert nichts selbst: die Daten gehen an onSubmit.
 */
export function GraphMailTemplateForm({
  formId,
  initialGraphMailTemplate,
  graphMailTemplateTypes,
  language,
  disabled,
  onSubmit,
}: GraphMailTemplateFormProps) {
  const { t } = useTranslation()
  const editing = initialGraphMailTemplate !== undefined
  const [graphMailTemplate, setGraphMailTemplate] = useState<GraphMailTemplate>(
    initialGraphMailTemplate ?? NEW_GRAPH_MAIL_TEMPLATE,
  )
  const [preview, setPreview] = useState<GraphMailTemplatePreview | null>(null)
  const [previewErrorMessage, setPreviewErrorMessage] = useState<string | null>(null)

  const chosenType = graphMailTemplateTypes.find((candidate) => candidate.type === graphMailTemplate.type) ?? null

  function change<K extends keyof GraphMailTemplate>(field: K, value: GraphMailTemplate[K]) {
    setGraphMailTemplate((current) => ({ ...current, [field]: value }))
  }

  function submitGraphMailTemplate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit(graphMailTemplate)
  }

  /** Den Platzhalter in die Zwischenablage legen, damit er sich an der richtigen Stelle einfügen lässt. */
  function copyPlaceholder(groupKey: string, placeholderName: string) {
    const placeholder = `{${groupKey}.${placeholderName}}`
    navigator.clipboard
      .writeText(placeholder)
      .then(() => showToast({ kind: 'info', title: t('graph.mailTemplates.form.placeholderCopied'), text: placeholder }))
      .catch(() => showToast({ kind: 'error', title: t('common.unknownError'), text: placeholder }))
  }

  // Das Muster: kurz nach dem letzten Tippen neu rendern lassen, eine späte Antwort wird verworfen
  const { type, subject, htmlBody } = graphMailTemplate
  useEffect(() => {
    let active = true
    const timeout = setTimeout(() => {
      renderGraphMailTemplatePreview({ type, language, subject, htmlBody })
        .then((result) => {
          if (active) {
            setPreview(result.data)
            setPreviewErrorMessage(null)
          }
        })
        .catch((error: unknown) => {
          if (active) {
            setPreviewErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
          }
        })
    }, PREVIEW_DELAY_MS)
    return () => {
      active = false
      clearTimeout(timeout)
    }
  }, [type, language, subject, htmlBody, t])

  return (
    <form id={formId} onSubmit={submitGraphMailTemplate}>
      <fieldset className="row g-3" disabled={disabled}>
        <div className="col-md-4">
          <label className="form-label" htmlFor={`${formId}-key`}>
            {t('common.key')}
          </label>
          <KeyInput
            id={`${formId}-key`}
            required
            // der key ändert sich nie
            disabled={disabled || editing}
            value={graphMailTemplate.key}
            onChange={(key) => change('key', key)}
          />
        </div>
        <div className="col-md-5">
          <label className="form-label" htmlFor={`${formId}-type`}>
            {t('graph.mailTemplates.form.type')}
          </label>
          <select
            id={`${formId}-type`}
            className="form-select"
            // der Typ wird beim Anlegen gewählt und ändert sich nie: Das Backend schickt die Mail über ihn
            disabled={disabled || editing}
            value={graphMailTemplate.type}
            onChange={(event) => change('type', event.target.value as GraphMailTemplateType)}
          >
            {graphMailTemplateTypes.map((graphMailTemplateType) => (
              <option key={graphMailTemplateType.type} value={graphMailTemplateType.type}>
                {t(`graph.mailTemplates.types.${graphMailTemplateType.type}`)}
              </option>
            ))}
          </select>
        </div>
        <div className="col-md-3">
          <label className="form-label" htmlFor={`${formId}-position`}>
            {t('common.position')}
          </label>
          <input
            id={`${formId}-position`}
            className="form-control"
            type="number"
            value={graphMailTemplate.listingPosition}
            onChange={(event) => change('listingPosition', Number(event.target.value))}
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-display-name`}>
            {t('common.displayName')}
          </label>
          <MultilingualInput
            id={`${formId}-display-name`}
            required
            disabled={disabled}
            language={language}
            value={graphMailTemplate.displayName || null}
            onChange={(displayName) => change('displayName', displayName ?? '')}
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-description`}>
            {t('common.description')}
          </label>
          <MultilingualInput
            id={`${formId}-description`}
            required
            disabled={disabled}
            language={language}
            value={graphMailTemplate.description || null}
            onChange={(description) => change('description', description ?? '')}
          />
        </div>
        <div className="col-md-8">
          <label className="form-label" htmlFor={`${formId}-subject`}>
            {t('graph.mailTemplates.form.subject')}
          </label>
          <MultilingualInput
            id={`${formId}-subject`}
            required
            disabled={disabled}
            language={language}
            value={graphMailTemplate.subject || null}
            onChange={(subject) => change('subject', subject ?? '')}
          />
          <label className="form-label mt-3" htmlFor={`${formId}-html-body`}>
            {t('graph.mailTemplates.form.htmlBody')}
          </label>
          <MultilingualInput
            id={`${formId}-html-body`}
            required
            multiline
            disabled={disabled}
            language={language}
            value={graphMailTemplate.htmlBody || null}
            onChange={(htmlBody) => change('htmlBody', htmlBody ?? '')}
          />
        </div>
        <div className="col-md-4">
          <span className="form-label d-block">{t('graph.mailTemplates.form.placeholders')}</span>
          <p className="small text-muted">{t('graph.mailTemplates.form.placeholdersHint')}</p>
          {chosenType?.placeholderGroups.map((placeholderGroup) => (
            <div key={placeholderGroup.groupKey} className="mb-2">
              <div className="small fw-bold">{placeholderGroup.groupKey}</div>
              <div className="d-flex flex-wrap gap-1">
                {placeholderGroup.placeholderNames.map((placeholderName) => (
                  <button
                    key={placeholderName}
                    type="button"
                    className="button-other"
                    onClick={() => copyPlaceholder(placeholderGroup.groupKey, placeholderName)}
                  >
                    {`{${placeholderGroup.groupKey}.${placeholderName}}`}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
        <div className="col-12">
          <span className="form-label d-block">{t('graph.mailTemplates.form.preview')}</span>
          <p className="small text-muted">{t('graph.mailTemplates.form.previewHint')}</p>
          {previewErrorMessage !== null && <p className="text-danger">{previewErrorMessage}</p>}
          {preview !== null && (
            <>
              <p className="mb-2">
                <strong>{t('graph.mailTemplates.form.previewSubject')}:</strong> {preview.subject}
              </p>
              {/* sandbox ohne Freigaben: das HTML der Vorlage läuft abgeschottet, keine Skripte, kein Zugriff auf die Seite */}
              <iframe
                title={t('graph.mailTemplates.form.preview')}
                sandbox=""
                srcDoc={preview.htmlBody}
                className="w-100 border rounded bg-white"
                style={{ height: '20rem' }}
              />
            </>
          )}
        </div>
      </fieldset>
    </form>
  )
}
