import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { MultilingualInput } from '../../components/MultilingualInput.tsx'
import { SolrFieldNameInput } from '../../components/SolrFieldNameInput.tsx'
import type { SolrHook } from './SolrHook.ts'
import { SolrHookChip } from './SolrHookChip.tsx'
import { SolrHookValues } from './SolrHookValues.tsx'

/** Startwerte eines neuen Hooks */
const NEW_SOLR_HOOK: SolrHook = {
  key: '',
  displayName: '',
  description: null,
  hookGroupKey: null,
  listingPosition: 0,
}

/** Beispielwerte, damit die Vorschau wie ein gesetzter Filter und wie ein Hook am Dokument aussieht */
const PREVIEW_VALUE = '4711'
const PREVIEW_VALUES = ['4711', '4712']

interface SolrHookFormProps {
  /** id des form-Elements, damit ein Knopf außerhalb (z. B. im contentbox-footer) es abschicken kann */
  formId: string
  /** Startwerte beim Bearbeiten. Fehlen sie, ist das Formular leer (neuer Hook). */
  initialSolrHook?: SolrHook
  /** Sprache der mehrsprachigen Felder, gewählt über die Flaggen der ContentBox */
  language: LanguageCode
  /** sperrt alle Felder, z. B. während gespeichert wird */
  disabled: boolean
  onSubmit: (solrHookData: SolrHook) => void
}

/**
 * Eingabefelder eines Hooks mit Live-Vorschau des Chips, zum Anlegen und zum Bearbeiten. Beim Bearbeiten ist der key
 * fest: Er ist der Feldname in Solr, und die Dokumente tragen ihn. Speichert nichts selbst: die Daten gehen an onSubmit.
 */
export function SolrHookForm({ formId, initialSolrHook, language, disabled, onSubmit }: SolrHookFormProps) {
  const { t } = useTranslation()
  const editing = initialSolrHook !== undefined
  const [solrHook, setSolrHook] = useState<SolrHook>(initialSolrHook ?? NEW_SOLR_HOOK)
  // die Gruppen aus den Stammdaten zur Auswahl, gepflegt im Tab „Hook-Gruppen“
  const { solrHookGroups } = useMasterData()

  function change<K extends keyof SolrHook>(field: K, value: SolrHook[K]) {
    setSolrHook((current) => ({ ...current, [field]: value }))
  }

  function submitSolrHook(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit(solrHook)
  }

  return (
    <form id={formId} onSubmit={submitSolrHook}>
      <fieldset className="row g-3" disabled={disabled}>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-key`}>
            {t('solrmanager.hooks.form.fieldName')}
          </label>
          <SolrFieldNameInput
            id={`${formId}-key`}
            required
            // der key ändert sich nie, sonst zeigten alle Dokumente ins Leere
            disabled={disabled || editing}
            value={solrHook.key}
            onChange={(key) => change('key', key)}
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
            value={solrHook.displayName || null}
            onChange={(displayName) => change('displayName', displayName ?? '')}
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-description`}>
            {t('common.description')}
          </label>
          <MultilingualInput
            id={`${formId}-description`}
            disabled={disabled}
            language={language}
            value={solrHook.description}
            onChange={(description) => change('description', description)}
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-group`}>
            {t('solrmanager.hooks.form.group')}
          </label>
          <select
            id={`${formId}-group`}
            className="form-select"
            disabled={disabled}
            value={solrHook.hookGroupKey ?? ''}
            onChange={(event) => change('hookGroupKey', event.target.value || null)}
          >
            <option value="">{t('solrmanager.hooks.form.noGroup')}</option>
            {(solrHookGroups ?? []).map((solrHookGroup) => (
              <option key={solrHookGroup.key} value={solrHookGroup.key}>
                {translate(solrHookGroup.displayName, language) || solrHookGroup.key}
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
            value={solrHook.listingPosition}
            onChange={(event) => change('listingPosition', Number(event.target.value))}
          />
        </div>

        {/* Live-Vorschau beider Darstellungen: als gesetzter Filter und als Hook am Dokument. Der Look ist fest, nur der Name ändert sich */}
        <div className="col-md-9">
          <span className="form-label d-block">{t('common.preview')}</span>
          <div className="d-flex flex-wrap align-items-center gap-4">
            <span className="d-inline-flex align-items-center gap-2">
              <span className="text-secondary small">{t('solrmanager.hooks.form.asFilter')}</span>
              <SolrHookChip
                solrHook={{ ...solrHook, key: solrHook.key || 'hook' }}
                value={PREVIEW_VALUE}
                language={language}
              />
            </span>
            <span className="d-inline-flex align-items-center gap-2">
              <span className="text-secondary small">{t('solrmanager.hooks.form.onDocument')}</span>
              <SolrHookValues
                solrHook={{ ...solrHook, key: solrHook.key || 'hook' }}
                values={PREVIEW_VALUES}
                language={language}
              />
            </span>
          </div>
        </div>
      </fieldset>
    </form>
  )
}
