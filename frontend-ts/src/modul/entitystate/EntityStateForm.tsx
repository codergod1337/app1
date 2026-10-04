import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { KeyInput } from '../../components/KeyInput.tsx'
import { MultilingualInput } from '../../components/MultilingualInput.tsx'
import type { EntityState } from './EntityState.ts'

/** Startwerte eines neuen Status */
const NEW_ENTITY_STATE: EntityState = {
  key: '',
  displayName: '',
  scope: null,
  listingPosition: 0,
}

interface EntityStateFormProps {
  /** id des form-Elements, damit ein Knopf außerhalb (z. B. im contentbox-footer) es abschicken kann */
  formId: string
  /** Startwerte beim Bearbeiten. Fehlen sie, ist das Formular leer (neuer Status). */
  initialEntityState?: EntityState
  /** Sprache der mehrsprachigen Felder, gewählt über die Flaggen der ContentBox */
  language: LanguageCode
  /** sperrt alle Felder, z. B. während gespeichert wird */
  disabled: boolean
  onSubmit: (entityStateData: EntityState) => void
}

/**
 * Eingabefelder eines Status, zum Anlegen und zum Bearbeiten. Beim Bearbeiten ist der key fest, die Dokumente tragen
 * ihn. Speichert nichts selbst: die Daten gehen an onSubmit.
 */
export function EntityStateForm({ formId, initialEntityState, language, disabled, onSubmit }: EntityStateFormProps) {
  const { t } = useTranslation()
  const editing = initialEntityState !== undefined
  const [entityState, setEntityState] = useState<EntityState>(initialEntityState ?? NEW_ENTITY_STATE)

  function change<K extends keyof EntityState>(field: K, value: EntityState[K]) {
    setEntityState((current) => ({ ...current, [field]: value }))
  }

  function submitEntityState(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit(entityState)
  }

  return (
    <form id={formId} onSubmit={submitEntityState}>
      <fieldset className="row g-3" disabled={disabled}>
        <div className="col-md-3">
          <label className="form-label" htmlFor={`${formId}-key`}>
            {t('common.key')}
          </label>
          <KeyInput
            id={`${formId}-key`}
            required
            // der key ändert sich nie, die Dokumente tragen ihn
            disabled={disabled || editing}
            value={entityState.key}
            onChange={(key) => change('key', key)}
          />
        </div>
        <div className="col-md-4">
          <label className="form-label" htmlFor={`${formId}-display-name`}>
            {t('common.displayName')}
          </label>
          <MultilingualInput
            id={`${formId}-display-name`}
            required
            disabled={disabled}
            language={language}
            value={entityState.displayName || null}
            onChange={(displayName) => change('displayName', displayName ?? '')}
          />
        </div>
        <div className="col-md-3">
          <label className="form-label" htmlFor={`${formId}-scope`}>
            {t('entitystate.scope')}
          </label>
          <KeyInput
            id={`${formId}-scope`}
            disabled={disabled}
            value={entityState.scope ?? ''}
            onChange={(scope) => change('scope', scope === '' ? null : scope)}
          />
          <div className="form-text">{t('entitystate.scopeHint')}</div>
        </div>
        <div className="col-md-2">
          <label className="form-label" htmlFor={`${formId}-position`}>
            {t('common.position')}
          </label>
          <input
            id={`${formId}-position`}
            className="form-control"
            type="number"
            value={entityState.listingPosition}
            onChange={(event) => change('listingPosition', Number(event.target.value))}
          />
        </div>
      </fieldset>
    </form>
  )
}
