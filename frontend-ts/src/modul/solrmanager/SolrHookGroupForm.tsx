import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { KeyInput } from '../../components/KeyInput.tsx'
import { MultilingualInput } from '../../components/MultilingualInput.tsx'
import type { SolrHookGroup } from './SolrHookGroup.ts'

/** Startwerte einer neuen Gruppe */
const NEW_SOLR_HOOK_GROUP: SolrHookGroup = {
  key: '',
  displayName: '',
  listingPosition: 0,
}

interface SolrHookGroupFormProps {
  /** id des form-Elements, damit ein Knopf außerhalb (z. B. im contentbox-footer) es abschicken kann */
  formId: string
  /** Startwerte beim Bearbeiten. Fehlen sie, ist das Formular leer (neue Gruppe). */
  initialSolrHookGroup?: SolrHookGroup
  /** Sprache der mehrsprachigen Felder, gewählt über die Flaggen der ContentBox */
  language: LanguageCode
  /** sperrt alle Felder, z. B. während gespeichert wird */
  disabled: boolean
  onSubmit: (solrHookGroupData: SolrHookGroup) => void
}

/**
 * Eingabefelder einer Hook-Gruppe, zum Anlegen und zum Bearbeiten. Beim Bearbeiten ist der key fest, die Hooks zeigen
 * darauf. Speichert nichts selbst: die Daten gehen an onSubmit.
 */
export function SolrHookGroupForm({ formId, initialSolrHookGroup, language, disabled, onSubmit }: SolrHookGroupFormProps) {
  const { t } = useTranslation()
  const editing = initialSolrHookGroup !== undefined
  const [solrHookGroup, setSolrHookGroup] = useState<SolrHookGroup>(initialSolrHookGroup ?? NEW_SOLR_HOOK_GROUP)

  function change<K extends keyof SolrHookGroup>(field: K, value: SolrHookGroup[K]) {
    setSolrHookGroup((current) => ({ ...current, [field]: value }))
  }

  function submitSolrHookGroup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit(solrHookGroup)
  }

  return (
    <form id={formId} onSubmit={submitSolrHookGroup}>
      <fieldset className="row g-3" disabled={disabled}>
        <div className="col-md-4">
          <label className="form-label" htmlFor={`${formId}-key`}>
            {t('common.key')}
          </label>
          <KeyInput
            id={`${formId}-key`}
            required
            // der key ändert sich nie, die Hooks zeigen darauf
            disabled={disabled || editing}
            value={solrHookGroup.key}
            onChange={(key) => change('key', key)}
          />
        </div>
        <div className="col-md-5">
          <label className="form-label" htmlFor={`${formId}-display-name`}>
            {t('common.displayName')}
          </label>
          <MultilingualInput
            id={`${formId}-display-name`}
            required
            disabled={disabled}
            language={language}
            value={solrHookGroup.displayName || null}
            onChange={(displayName) => change('displayName', displayName ?? '')}
          />
        </div>
        <div className="col-md-3">
          <label className="form-label" htmlFor={`${formId}-position`}>
            {t('common.position')}
          </label>
          <input
            id={`${formId}-position`}
            className="form-control"
            type="number"
            value={solrHookGroup.listingPosition}
            onChange={(event) => change('listingPosition', Number(event.target.value))}
          />
        </div>
      </fieldset>
    </form>
  )
}
