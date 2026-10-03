import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { KeyInput } from '../../components/KeyInput.tsx'
import { MultilingualInput } from '../../components/MultilingualInput.tsx'
import type { SolrCore } from './SolrCore.ts'
import { SolrCoreEmblem } from './SolrCoreEmblem.tsx'
import { EMPTY_SOLR_LOOK, type SolrLook } from './SolrLook.ts'
import { SolrLookFields } from './SolrLookFields.tsx'

/** Startwerte eines neuen Kerns: alles Standard. Das Feld id legt das Backend dazu. */
const NEW_SOLR_CORE: SolrCore = {
  key: '',
  displayName: '',
  look: EMPTY_SOLR_LOOK,
  listingPosition: 0,
}

interface SolrCoreFormProps {
  /** id des form-Elements, damit ein Knopf außerhalb (z. B. im contentbox-footer) es abschicken kann */
  formId: string
  /** Startwerte beim Bearbeiten. Fehlen sie, ist das Formular leer (neuer Kern). */
  initialSolrCore?: SolrCore
  /** Sprache der mehrsprachigen Felder, gewählt über die Flaggen der ContentBox */
  language: LanguageCode
  /** sperrt alle Felder, z. B. während gespeichert wird */
  disabled: boolean
  onSubmit: (solrCoreData: SolrCore) => void
}

/**
 * Eingabefelder eines Kerns mit Live-Vorschau des Emblems, zum Anlegen und zum Bearbeiten. Der key folgt der normalen
 * Key-Regel und ist zugleich der Name des Kerns in Solr. Beim Bearbeiten ist er fest. Speichert nichts selbst: die
 * Daten gehen an onSubmit.
 */
export function SolrCoreForm({ formId, initialSolrCore, language, disabled, onSubmit }: SolrCoreFormProps) {
  const { t } = useTranslation()
  const editing = initialSolrCore !== undefined
  const [solrCore, setSolrCore] = useState<SolrCore>(initialSolrCore ?? NEW_SOLR_CORE)

  function change<K extends keyof SolrCore>(field: K, value: SolrCore[K]) {
    setSolrCore((current) => ({ ...current, [field]: value }))
  }

  function changeLook(changes: Partial<SolrLook>) {
    setSolrCore((current) => ({ ...current, look: { ...current.look, ...changes } }))
  }

  function submitSolrCore(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit(solrCore)
  }

  return (
    <form id={formId} onSubmit={submitSolrCore}>
      <fieldset className="row g-3" disabled={disabled}>
        <div className="col-md-6">
          <label className="form-label" htmlFor={`${formId}-key`}>
            {t('solrmanager.cores.form.key')}
          </label>
          <KeyInput
            id={`${formId}-key`}
            required
            // der key ändert sich nie, er ist der Name des Kerns in Solr
            disabled={disabled || editing}
            value={solrCore.key}
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
            value={solrCore.displayName || null}
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
            value={solrCore.listingPosition}
            onChange={(event) => change('listingPosition', Number(event.target.value))}
          />
        </div>

        {/* Live-Vorschau: ändert sich mit jeder Eingabe */}
        <div className="col-md-9">
          <span className="form-label d-block">{t('common.preview')}</span>
          <SolrCoreEmblem solrCore={{ ...solrCore, key: solrCore.key || 'KEY' }} language={language} />
        </div>

        <SolrLookFields idPrefix={formId} look={solrCore.look} disabled={disabled} onChange={changeLook} />
      </fieldset>
    </form>
  )
}
