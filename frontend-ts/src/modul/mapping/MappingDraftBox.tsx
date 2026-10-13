import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import { showToast } from '../../components/toastStore.ts'
import { customFieldNameOfPath, type MappingDraft, type MappingDraftFetch, responseMappingMatchesResponse, type useMappingDraft } from './MappingDraft.ts'
import { createResponseMapping, type ResponseMappingLine, updateResponseMapping } from './ResponseMapping.ts'
import { ResponseMappingEditor, type SubfetchInputPick } from './ResponseMappingEditor.tsx'

const FORM_ID = 'mapping-draft-form'

interface MappingDraftBoxProps {
  mappingDraft: ReturnType<typeof useMappingDraft>
  /** ein Klick auf einen Wert im Blatt: der Tab springt im Baum an die Quelle des Eintrags */
  onJumpToLine?: (line: ResponseMappingLine) => void
  /** der Tab holt einen Subfetch-Eintrag gegen die gezeigte Antwort und zeigt die Antwort darunter */
  onFetchSubfetch?: (line: ResponseMappingLine) => void
  /** der Tab merkt sich, welche Subfetch-Eingabe der nächste Klick im Baum setzt */
  onPickSubfetchInput?: (pick: SubfetchInputPick | null) => void
  subfetchInputPick?: SubfetchInputPick | null
}

/**
 * Die Box „Mapping-Entwurf“: das Gesamt-Mapping der gezeigten Antwort, immer komplett sichtbar, im Editor mit den
 * Werten aus der Antwort. Gibt es zum Typ schon Mappings, stehen sie zur Wahl, eines übernehmen oder neu. Mehrere
 * Entwürfe stehen als Stapel oben, die Hauptantwort und je Subfetch ein weiterer; Speichern legt den gezeigten Entwurf
 * an oder ändert das übernommene Mapping und geht zum Entwurf darunter zurück. Editor und Knopf hängen an einem
 * Formular, damit der Browser leere Pflichtfelder vor dem Absenden meldet.
 */
export function MappingDraftBox({ mappingDraft, onJumpToLine, onFetchSubfetch, onPickSubfetchInput, subfetchInputPick = null }: MappingDraftBoxProps) {
  const { t } = useTranslation()
  const [saving, setSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const current = mappingDraft.current
  const existing = mappingDraft.existing
  // die y-Felder (Custom-Felder der Firma) ohne Eintrag: jedes wird gespeichert, der Knopf nimmt alle auf einmal auf
  const customFieldPaths = mappingDraft.unmappedCustomFieldPathsOf()

  function recordCustomFields() {
    const count = mappingDraft.recordCustomFields()
    if (count > 0) {
      showToast({ kind: 'info', title: t('mapping.draft.customFieldsRecorded', { count }) })
    }
  }

  function fetchText(fetch: MappingDraftFetch): string {
    switch (fetch.kind) {
      case 'JSON':
        return t('mapping.draft.mainJson', { name: fetch.sourceName })
      case 'STERNZEIT':
        return t('mapping.draft.mainSternzeit', { sternzeit: fetch.sternzeit })
      case 'INFOSYS':
        return t('mapping.draft.mainInfosys', { key: fetch.abasInfosysKey })
    }
  }

  function draftLabel(draft: MappingDraft): string {
    const key = draft.value.responseMapping.key
    return key !== '' ? key : draft.value.responseMapping.responseObjectType || fetchText(draft.mainFetch)
  }

  function save(event: FormEvent) {
    event.preventDefault()
    if (current === null) {
      return
    }
    setSaving(true)
    setErrorMessage(null)
    const editing = current.value.responseMapping.id !== null
    const send = editing ? updateResponseMapping(current.value) : createResponseMapping(current.value)
    send
      .then((result) => {
        showToast({ kind: 'success', title: t(editing ? 'mapping.draft.updated' : 'mapping.draft.saved', { key: result.data.responseMapping.key }) })
        mappingDraft.removeCurrent()
      })
      .catch((error: unknown) => setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError')))
      .finally(() => setSaving(false))
  }

  const footer = (
    <>
      {errorMessage !== null ? <span className="text-danger">{errorMessage}</span> : <span>{current === null ? t('mapping.draft.empty') : t('mapping.draft.hint')}</span>}
      {current !== null && (
        <span className="ms-auto d-flex gap-2">
          <button type="button" className="button-cancel" disabled={saving} onClick={mappingDraft.reset}>
            {t('mapping.draft.discard')}
          </button>
          <button type="submit" form={FORM_ID} className="button-save" disabled={saving || current.value.lines.length === 0}>
            {current.value.responseMapping.id !== null ? t('mapping.draft.update') : t('mapping.draft.save')}
          </button>
        </span>
      )}
    </>
  )

  return (
    <ContentBox title={t('mapping.draft.title')} footer={footer}>
      {current !== null && (
        <form id={FORM_ID} className="d-flex flex-column gap-3" onSubmit={save}>
          <div className="d-flex align-items-center gap-2 flex-wrap">
            {mappingDraft.drafts.map((draft, index) => (
              <span key={index} className="d-inline-flex align-items-center gap-2">
                {index > 0 && <span>›</span>}
                <button
                  type="button"
                  className={index === mappingDraft.currentIndex ? 'button-other fw-bold' : 'button-other'}
                  onClick={() => mappingDraft.selectDraft(index)}
                >
                  {draftLabel(draft)}
                </button>
              </span>
            ))}
            <span className="text-body-secondary ms-2">{fetchText(current.mainFetch)}</span>
          </div>

          {existing !== null && (
            <div className="d-flex align-items-center gap-2 flex-wrap">
              <span className="fw-semibold">{t('mapping.draft.existingTitle', { type: existing.responseObjectType })}</span>
              {existing.errorMessage !== null && <span className="text-danger">{existing.errorMessage}</span>}
              {existing.errorMessage === null && existing.mappings.length === 0 && (
                <span className="text-body-secondary">{t('mapping.draft.existingNone')}</span>
              )}
              {existing.mappings.map((mapping) => {
                const adopted = current.value.responseMapping.id === mapping.responseMapping.id
                const matches = responseMappingMatchesResponse(mapping, current.response)
                return (
                  <button
                    key={mapping.responseMapping.id ?? mapping.responseMapping.key}
                    type="button"
                    className={adopted ? 'button-other fw-bold' : 'button-other'}
                    disabled={saving}
                    onClick={() => mappingDraft.adoptExisting(mapping)}
                  >
                    {mapping.responseMapping.key}
                    {matches && <span className="badge text-bg-success ms-1">{t('mapping.draft.existingMatches')}</span>}
                  </button>
                )
              })}
              {current.value.responseMapping.id !== null && (
                <button type="button" className="button-other" disabled={saving} onClick={mappingDraft.startFresh}>
                  {t('mapping.draft.startFresh')}
                </button>
              )}
            </div>
          )}

          {customFieldPaths.length > 0 && (
            <div className="d-flex align-items-center gap-2 flex-wrap">
              <Hoverlay text={customFieldPaths.map(customFieldNameOfPath).join(', ')}>
                <span>{t('mapping.draft.customFieldsUnmapped', { count: customFieldPaths.length })}</span>
              </Hoverlay>
              <NewEntityButton entity="ResponseMappingLine (y)" disabled={saving} onClick={recordCustomFields} />
            </div>
          )}

          <ResponseMappingEditor
            idPrefix={`mapping-draft-${mappingDraft.currentIndex}`}
            value={current.value}
            onChange={mappingDraft.changeCurrent}
            disabled={saving}
            keyLocked={current.value.responseMapping.id !== null}
            sampleJson={current.response}
            onMakeCondition={mappingDraft.makeCondition}
            onJumpToLine={onJumpToLine}
            onFetchSubfetch={onFetchSubfetch}
            onPickSubfetchInput={onPickSubfetchInput}
            subfetchInputPick={subfetchInputPick}
          />
        </form>
      )}
    </ContentBox>
  )
}
