import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { ConfirmCancelButtons } from '../../components/ConfirmCancelButtons.tsx'
import { highlight } from '../../components/highlight.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { Popup } from '../../components/Popup.tsx'
import { ValuesInput } from '../../components/ValuesInput.tsx'
import type { SolrCore } from '../solrmanager/SolrCore.ts'
import { SolrCoreEmblem } from '../solrmanager/SolrCoreEmblem.tsx'
import { solrFieldLabel, type SolrField } from '../solrmanager/SolrField.ts'
import { groupSolrHooks, type SolrHook } from '../solrmanager/SolrHook.ts'
import { SolrHookCatalog } from '../solrmanager/SolrHookCatalog.tsx'
import { SolrHookChip } from '../solrmanager/SolrHookChip.tsx'
import { createUsersDebuffs, USERS_DEBUFF_TYPES, type UsersDebuffType } from '../users/UsersDebuffs.ts'

/** Das gewählte Feld: ein Feld eines Kerns oder ein Hook */
type ChosenField = { kind: 'field'; solrField: SolrField } | { kind: 'hook'; solrHook: SolrHook }

interface UsersDebuffsPopupProps {
  open: boolean
  usersGuid: string
  /** der Debuff ist angelegt, der Aufrufer lädt die Liste neu */
  onCreated: () => void
  onClose: () => void
}

/**
 * Neuer Debuff für einen User, aus der Maske User bearbeiten. Nichts wird getippt außer den Werten: Das Feld kommt aus
 * den Stammdaten, Solr-Felder je Kern mit Emblem, Hooks je Gruppe wie im Kern-Tab, eine Eingabe filtert beide Listen.
 * Texttypen und Felder nur für das Backend fehlen, nach denen lässt sich nicht exakt filtern. Die Werte gehen über
 * die ValuesInput, jeder als Badge mit Mülleimer.
 */
export function UsersDebuffsPopup({ open, usersGuid, onCreated, onClose }: UsersDebuffsPopupProps) {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const { solrCores, solrFields, solrHooks, solrHookGroups } = useMasterData()
  const [type, setType] = useState<UsersDebuffType>('RESOLVE')
  const [needle, setNeedle] = useState('')
  const [chosen, setChosen] = useState<ChosenField | null>(null)
  const [values, setValues] = useState<string[]>([])
  const [saving, setSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const lowerNeedle = needle.trim().toLowerCase()

  function matchesNeedle(name: string, label: string): boolean {
    return lowerNeedle === '' || name.toLowerCase().includes(lowerNeedle) || label.toLowerCase().includes(lowerNeedle)
  }

  // nur normale Felder, nach denen sich exakt filtern lässt: kein Text; die Hooks kommen aus dem Katalog darunter
  const filterableFields = (solrFields ?? []).filter(
    (solrField) =>
      solrField.hookKey === null &&
      solrField.type !== 'TEXT_EDGE' &&
      solrField.type !== 'TEXT_NGRAM' &&
      solrField.type !== 'TEXT_SUCHE' &&
      matchesNeedle(solrField.name, solrFieldLabel(solrField, language)),
  )
  // die Felder in jedem Kern zuerst, dann je Kern in der Reihenfolge der Kerne
  const fieldGroups: { solrCore: SolrCore | null; solrFields: SolrField[] }[] = [
    { solrCore: null, solrFields: filterableFields.filter((solrField) => solrField.coreKey === null) },
    ...(solrCores ?? []).map((solrCore) => ({
      solrCore,
      solrFields: filterableFields.filter((solrField) => solrField.coreKey === solrCore.key),
    })),
  ].filter((group) => group.solrFields.length > 0)
  const hookGroups = groupSolrHooks(
    (solrHooks ?? []).filter((solrHook) => matchesNeedle(solrHook.key, translate(solrHook.displayName || null, language))),
    solrHookGroups ?? [],
  )

  function reset() {
    setType('RESOLVE')
    setNeedle('')
    setChosen(null)
    setValues([])
    setErrorMessage(null)
  }

  function close() {
    reset()
    onClose()
  }

  const solrFieldName = chosen === null ? null : chosen.kind === 'field' ? chosen.solrField.name : chosen.solrHook.key
  const canSave = !saving && solrFieldName !== null && values.length > 0

  function save() {
    if (!canSave || solrFieldName === null) {
      return
    }
    setSaving(true)
    setErrorMessage(null)
    createUsersDebuffs({ usersGuid, type, solrFieldName, values })
      .then(() => {
        reset()
        onCreated()
      })
      .catch((error: unknown) => {
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
      })
      .finally(() => setSaving(false))
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      <ConfirmCancelButtons
        onCancel={close}
        confirmLabel={t('common.save')}
        onConfirm={save}
        disabled={!canSave}
        busy={saving}
      />
    </>
  )

  return (
    <Popup open={open} title={t('admin.debuffs.popupTitle')} footer={footer} onClose={close}>
      <div className="mb-3">
        <label className="form-label" htmlFor="users-debuffs-type">
          {t('admin.debuffs.type')}
        </label>
        <select
          id="users-debuffs-type"
          className="form-select"
          value={type}
          disabled={saving}
          onChange={(event) => setType(event.target.value as UsersDebuffType)}
        >
          {USERS_DEBUFF_TYPES.map((candidate) => (
            <option key={candidate} value={candidate}>
              {t(`admin.debuffs.types.${candidate}`)}
            </option>
          ))}
        </select>
      </div>

      <div className="mb-3">
        <label className="form-label">{t('admin.debuffs.field')}</label>
        {chosen !== null ? (
          // das gewählte Feld, daneben der Weg zurück in die Auswahl
          <div className="d-flex align-items-center gap-2">
            {chosen.kind === 'hook' ? (
              <SolrHookChip solrHook={chosen.solrHook} language={language} />
            ) : (
              <span className="d-inline-flex align-items-center gap-2">
                {(() => {
                  const solrCore = (solrCores ?? []).find((candidate) => candidate.key === chosen.solrField.coreKey)
                  return solrCore ? <SolrCoreEmblem solrCore={solrCore} language={language} /> : null
                })()}
                <strong>{solrFieldLabel(chosen.solrField, language)}</strong>
                <span className="text-body-secondary font-monospace">{chosen.solrField.name}</span>
              </span>
            )}
            <button type="button" className="button-other" disabled={saving} onClick={() => setChosen(null)}>
              {t('admin.debuffs.change')}
            </button>
          </div>
        ) : (
          <>
            <input
              className="form-control mb-2"
              placeholder={t('admin.debuffs.fieldSearchPlaceholder')}
              value={needle}
              autoFocus
              onChange={(event) => setNeedle(event.target.value)}
            />
            {fieldGroups.length === 0 && hookGroups.length === 0 && (
              <span className="text-body-secondary">{t('admin.debuffs.noFields')}</span>
            )}
            {fieldGroups.map((group) => (
              <div key={group.solrCore?.key ?? ''} className="mb-2">
                <div className="mb-1">
                  {group.solrCore ? (
                    <SolrCoreEmblem solrCore={group.solrCore} language={language} />
                  ) : (
                    <span className="text-body-secondary">{t('admin.debuffs.everyCore')}</span>
                  )}
                </div>
                <div className="d-flex flex-wrap gap-1">
                  {group.solrFields.map((solrField) => (
                    <button
                      key={solrField.name}
                      type="button"
                      className="button-other"
                      onClick={() => setChosen({ kind: 'field', solrField })}
                    >
                      {highlight(solrFieldLabel(solrField, language), lowerNeedle)}
                      <span className="text-body-secondary font-monospace ms-1">{highlight(solrField.name, lowerNeedle)}</span>
                    </button>
                  ))}
                </div>
              </div>
            ))}
            {hookGroups.length > 0 && (
              <>
                <div className="mb-1 text-body-secondary">{t('admin.debuffs.hooks')}</div>
                <SolrHookCatalog
                  groups={hookGroups}
                  language={language}
                  needle={lowerNeedle}
                  onPick={(solrHook) => setChosen({ kind: 'hook', solrHook })}
                  emptyText={t('admin.debuffs.noFields')}
                />
              </>
            )}
          </>
        )}
      </div>

      <div className="mb-2">
        <label className="form-label">{t('admin.debuffs.values')}</label>
        <ValuesInput
          values={values}
          onChange={setValues}
          placeholder={t('admin.debuffs.valuesPlaceholder')}
          disabled={saving}
        />
      </div>
      <p className="text-body-secondary small mb-0">{t('admin.debuffs.sessionsEnd')}</p>
    </Popup>
  )
}
