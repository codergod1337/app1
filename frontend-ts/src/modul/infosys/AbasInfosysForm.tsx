import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { KeyInput } from '../../components/KeyInput.tsx'
import { isValidKey } from '../../components/keyRule.ts'
import { MultilingualInput } from '../../components/MultilingualInput.tsx'
import { ValuesInput } from '../../components/ValuesInput.tsx'
import {
  type AbasInfosysEntry,
  type AbasInfosysHeadField,
  abasInfosysEntriesOf,
  abasInfosysHeadFieldsOf,
  fetchAbasInfosysMeta,
  getAbasInfosysList,
} from '../abasmanager/AbasInfosys.ts'
import { type AbasInfosys, abasInfosysPlaceholderNamesOf, abasInfosysTemplateOf, KNOWN_ABAS_INFOSYS_DISPLAY_CONTEXT_KEYS } from './AbasInfosys.ts'

/** Startwerte eines neuen Aufrufs: die Vorlage mit dem Startknopf, der bei fast jedem Infosystem dabei ist */
const NEW_ABAS_INFOSYS: AbasInfosys = {
  key: '',
  displayName: '',
  infosysGroup: '',
  infosysName: '',
  requestBodyTemplate: abasInfosysTemplateOf([], ['bstart']),
  listingPosition: 0,
  displayContextKeys: [],
}

const GROUPS_LIST_ID = 'abas-infosys-form-groups'
const NAMES_LIST_ID = 'abas-infosys-form-names'

interface AbasInfosysFormProps {
  /** id des form-Elements, damit ein Knopf außerhalb (z. B. im contentbox-footer) es abschicken kann */
  formId: string
  /** Startwerte beim Bearbeiten. Fehlen sie, ist das Formular leer (neuer Aufruf). */
  initialAbasInfosys?: AbasInfosys
  /** Sprache der mehrsprachigen Felder, gewählt über die Flaggen der ContentBox */
  language: LanguageCode
  /** sperrt alle Felder, z. B. während gespeichert wird */
  disabled: boolean
  onSubmit: (abasInfosysData: AbasInfosys) => void
}

/**
 * Eingabefelder eines hinterlegten Infosystem-Aufrufs, zum Anlegen und zum Bearbeiten. Beim Bearbeiten ist der key
 * fest, die Mapping-Einträge zeigen darauf. Gruppe und Name kommen als Vorschläge aus der Liste, die ABAS liefert; die
 * Felder des gewählten Infosystems aus seinem Meta: Ein Klick hängt ein Feld als Platzhalter an die Vorlage, ein Knopf
 * ohne Wert. Die Vorlage bleibt frei editierbar, die Platzhalter darunter sind das, was ein Aufruf eingeben muss.
 * Speichert nichts selbst: die Daten gehen an onSubmit.
 */
export function AbasInfosysForm({ formId, initialAbasInfosys, language, disabled, onSubmit }: AbasInfosysFormProps) {
  const { t } = useTranslation()
  const editing = initialAbasInfosys !== undefined
  const [abasInfosys, setAbasInfosys] = useState<AbasInfosys>(initialAbasInfosys ?? NEW_ABAS_INFOSYS)
  const [entries, setEntries] = useState<AbasInfosysEntry[]>([])
  const [meta, setMeta] = useState<{ infosys: string; headFields: AbasInfosysHeadField[] } | null>(null)
  const [metaError, setMetaError] = useState<{ infosys: string; message: string } | null>(null)

  function change<K extends keyof AbasInfosys>(field: K, value: AbasInfosys[K]) {
    setAbasInfosys((current) => ({ ...current, [field]: value }))
  }

  /** Die Anzeigeorte nach der Key-Regel, wie das Backend sie prüft; ein Wert daneben fällt weg, Kleines wird groß */
  function changeDisplayContextKeys(values: string[]) {
    change(
      'displayContextKeys',
      values.map((value) => value.trim().toUpperCase()).filter((value, index, all) => isValidKey(value) && all.indexOf(value) === index),
    )
  }

  // die Liste aller Infosysteme bei ABAS, einmal beim Öffnen; scheitert sie, bleibt alles frei eintippbar
  useEffect(() => {
    let active = true
    getAbasInfosysList()
      .then((result) => {
        if (active) {
          setEntries(abasInfosysEntriesOf(result.data))
        }
      })
      .catch(() => {
        if (active) {
          setEntries([])
        }
      })
    return () => {
      active = false
    }
  }, [])

  const groupTrimmed = abasInfosys.infosysGroup.trim()
  const nameTrimmed = abasInfosys.infosysName.trim()
  const infosysKey = `${groupTrimmed}/${nameTrimmed}`
  const groups = useMemo(() => [...new Set(entries.map((entry) => entry.group))].sort(), [entries])
  const namesOfGroup = entries
    .filter((entry) => entry.group === groupTrimmed)
    .map((entry) => entry.name)
    .sort()
  const known = entries.some((entry) => entry.group === groupTrimmed && entry.name === nameTrimmed)
  const headFields = meta !== null && meta.infosys === infosysKey ? meta.headFields : []

  // die Felder des gewählten Infosystems, sobald Gruppe und Name in der Liste stehen
  useEffect(() => {
    if (!known || (meta !== null && meta.infosys === infosysKey) || (metaError !== null && metaError.infosys === infosysKey)) {
      return
    }
    let active = true
    const unknownError = t('common.unknownError')
    fetchAbasInfosysMeta(groupTrimmed, nameTrimmed)
      .then((result) => {
        if (active) {
          setMeta({ infosys: infosysKey, headFields: abasInfosysHeadFieldsOf(result.data) })
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setMetaError({ infosys: infosysKey, message: error instanceof RequestError ? error.message : unknownError })
        }
      })
    return () => {
      active = false
    }
  }, [known, infosysKey, groupTrimmed, nameTrimmed, meta, metaError, t])

  /** Ein Feld an die Vorlage hängen: als Platzhalter, ein Knopf ohne Wert; eine unlesbare Vorlage wird neu begonnen */
  function appendField(headField: AbasInfosysHeadField) {
    const isButton = headField.baseType === 'BUTTON'
    let actions: unknown[] = []
    try {
      const parsed: unknown = JSON.parse(abasInfosys.requestBodyTemplate)
      if (typeof parsed === 'object' && parsed !== null && Array.isArray((parsed as { actions?: unknown }).actions)) {
        actions = (parsed as { actions: unknown[] }).actions
      }
    } catch {
      actions = []
    }
    const action = isButton ? { _type: 'SetFieldValue', fieldName: headField.name } : { _type: 'SetFieldValue', fieldName: headField.name, value: `{{${headField.name}}}` }
    change('requestBodyTemplate', JSON.stringify({ actions: [...actions, action] }, null, 2))
  }

  const placeholders = abasInfosysPlaceholderNamesOf(abasInfosys.requestBodyTemplate)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit(abasInfosys)
  }

  return (
    <form id={formId} onSubmit={submit}>
      <fieldset className="row g-3" disabled={disabled}>
        <div className="col-md-4">
          <label className="form-label" htmlFor={`${formId}-key`}>
            {t('common.key')}
          </label>
          <KeyInput id={`${formId}-key`} required disabled={disabled || editing} value={abasInfosys.key} onChange={(key) => change('key', key)} />
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
            value={abasInfosys.displayName || null}
            onChange={(displayName) => change('displayName', displayName ?? '')}
          />
        </div>
        <div className="col-md-3">
          <label className="form-label" htmlFor={`${formId}-position`}>
            {t('common.position')}
          </label>
          <input id={`${formId}-position`} className="form-control" type="number" value={abasInfosys.listingPosition} onChange={(event) => change('listingPosition', Number(event.target.value))} />
        </div>
        <div className="col-md-4">
          <label className="form-label" htmlFor={`${formId}-group`}>
            {t('infosys.calls.form.group')}
          </label>
          <input
            id={`${formId}-group`}
            className="form-control font-monospace"
            required
            list={GROUPS_LIST_ID}
            value={abasInfosys.infosysGroup}
            placeholder={t('mapping.abasFetch.groupPlaceholder')}
            onChange={(event) => change('infosysGroup', event.target.value)}
          />
          <datalist id={GROUPS_LIST_ID}>
            {groups.map((group) => (
              <option key={group} value={group} />
            ))}
          </datalist>
        </div>
        <div className="col-md-8">
          <label className="form-label" htmlFor={`${formId}-name`}>
            {t('infosys.calls.form.name')}
          </label>
          <input
            id={`${formId}-name`}
            className="form-control font-monospace"
            required
            list={NAMES_LIST_ID}
            value={abasInfosys.infosysName}
            placeholder={t('mapping.abasFetch.namePlaceholder')}
            onChange={(event) => change('infosysName', event.target.value)}
          />
          <datalist id={NAMES_LIST_ID}>
            {namesOfGroup.map((name) => (
              <option key={name} value={name} />
            ))}
          </datalist>
          <div className="form-text">
            {t('infosys.calls.form.urlPreview')} <code>POST …/infosys/data/{groupTrimmed || '…'}/{nameTrimmed || '…'}</code>
          </div>
        </div>
        <div className="col-12">
          <span className="form-label d-block">{t('infosys.calls.form.fields')}</span>
          {metaError !== null && metaError.infosys === infosysKey && <span className="text-danger small">{metaError.message}</span>}
          {headFields.length === 0 && (metaError === null || metaError.infosys !== infosysKey) && (
            <span className="text-body-secondary small">{known ? t('common.loading') : t('infosys.calls.form.fieldsHint')}</span>
          )}
          {headFields.length > 0 && (
            <div className="d-flex flex-wrap gap-1">
              {headFields.map((headField) => (
                <Hoverlay key={headField.name} text={`${headField.description || headField.name} (${headField.baseType || '?'})`}>
                  <button type="button" className="badge text-bg-light fw-normal font-monospace border-0" disabled={disabled} onClick={() => appendField(headField)}>
                    {headField.baseType === 'BUTTON' ? '▶ ' : ''}
                    {headField.name}
                  </button>
                </Hoverlay>
              ))}
            </div>
          )}
        </div>
        <div className="col-12">
          <label className="form-label" htmlFor={`${formId}-template`}>
            {t('infosys.calls.form.template')}
          </label>
          <textarea
            id={`${formId}-template`}
            className="form-control font-monospace small"
            rows={8}
            required
            value={abasInfosys.requestBodyTemplate}
            onChange={(event) => change('requestBodyTemplate', event.target.value)}
          />
          <div className="form-text">
            {t('infosys.calls.form.templateHint')}{' '}
            {placeholders.length === 0 ? (
              t('infosys.calls.form.noPlaceholders')
            ) : (
              <>
                {t('infosys.calls.form.placeholders')}{' '}
                {placeholders.map((name) => (
                  <code key={name} className="me-1">
                    {name}
                  </code>
                ))}
              </>
            )}
          </div>
        </div>
        <div className="col-12">
          <span className="form-label d-block">{t('infosys.calls.form.displayContextKeys')}</span>
          <ValuesInput
            values={abasInfosys.displayContextKeys ?? []}
            placeholder={KNOWN_ABAS_INFOSYS_DISPLAY_CONTEXT_KEYS[0]}
            separator=";"
            ariaLabel={t('infosys.calls.form.displayContextKeys')}
            disabled={disabled}
            onChange={changeDisplayContextKeys}
          />
          <div className="form-text">{t('infosys.calls.form.displayContextKeysHint', { known: KNOWN_ABAS_INFOSYS_DISPLAY_CONTEXT_KEYS.join(', ') })}</div>
        </div>
      </fieldset>
    </form>
  )
}
