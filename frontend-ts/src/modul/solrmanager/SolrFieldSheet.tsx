import { useRef, useState, type KeyboardEvent } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { MultilingualInput } from '../../components/MultilingualInput.tsx'
import { positionsByKey } from '../../components/positions.ts'
import { isValidSolrFieldName } from '../../components/solrFieldNameRule.ts'
import { SolrFieldNameInput } from '../../components/SolrFieldNameInput.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import {
  SOLR_CORE_BUILT_IN_FIELD_NAMES,
  SOLR_CORE_ID_FIELD_NAME,
  SOLR_CORE_USER_GRANTED_ACCESS_FIELD_NAME,
} from './SolrCore.ts'
import {
  changeSolrFieldPositions,
  createSolrField,
  deleteSolrField,
  isSuggestable,
  SOLR_FIELD_CLIENT_WRITE_RULES,
  SOLR_FIELD_SETTINGS,
  SOLR_FIELD_TYPES,
  SOLR_ID_FIELD_FIXED_SETTINGS,
  SOLR_USER_GRANTED_ACCESS_FIELD_FIXED_SETTINGS,
  updateSolrField,
  type SolrField,
  type SolrFieldClientWriteRule,
  type SolrFieldData,
  type SolrFieldSetting,
  type SolrFieldType,
} from './SolrField.ts'
import {
  solrFieldAccessHint,
  solrFieldClientWriteRuleHint,
  solrFieldDescriptionHint,
  solrFieldIdHint,
  solrFieldSettingHint,
  solrFieldTypeHint,
} from './solrFieldHints.tsx'

/** So lange nach dem letzten Tippen in einem Textfeld wird gewartet, dann gespeichert */
const TEXT_SAVE_DELAY_MS = 600

type RowStatus = { kind: 'saving' } | { kind: 'saved' } | { kind: 'error'; message: string }

/** Eine Zeile der Tabelle: ein bestehendes Feld, oder ganz unten die leere Zeile für ein neues */
type SheetRow = { kind: 'field'; field: SolrField } | { kind: 'new' }

const NEW_ROW: SheetRow = { kind: 'new' }

/** Die Zeile des Feldes id, des Primärschlüssels: zusätzlich stehen die PK-Schalter fest, und es steht ganz oben */
function isIdRow(row: SheetRow): boolean {
  return row.kind === 'field' && row.field.name === SOLR_CORE_ID_FIELD_NAME
}

/** Die Zeile eines festen Feldes (id, cursorDate, userGrantedAccess): jeder Kern hat es, der Typ steht fest, löschen geht nicht */
function isBuiltInRow(row: SheetRow): boolean {
  return row.kind === 'field' && SOLR_CORE_BUILT_IN_FIELD_NAMES.includes(row.field.name)
}

/** Die Schalter, die bei einem festen Feld nicht umgelegt werden dürfen: bei id die PK-Schalter, bei userGrantedAccess die der Dokumentgrenze */
function fixedSettingsOf(row: SheetRow): readonly SolrFieldSetting[] {
  if (isIdRow(row)) {
    return SOLR_ID_FIELD_FIXED_SETTINGS
  }
  if (row.kind === 'field' && row.field.name === SOLR_CORE_USER_GRANTED_ACCESS_FIELD_NAME) {
    return SOLR_USER_GRANTED_ACCESS_FIELD_FIXED_SETTINGS
  }
  return []
}

/** Die leere Zeile unten: indexed und stored an, der Rest aus, Zugriff Erweitert, Schreibregel Backend */
function emptySolrFieldData(coreKey: string): SolrFieldData {
  return {
    coreKey,
    name: '',
    type: 'STRING',
    indexed: true,
    stored: true,
    multiValued: false,
    docValues: false,
    required: false,
    suggest: false,
    readAccessLevel: 'ADVANCED_FULL_SIGHT_OR_FIELD_GRANT',
    clientWriteRule: 'BACKEND_ONLY',
    description: null,
    listingPosition: 0,
  }
}

/** Die nächste Stufe der Schreibregel: Backend, nur beim Anlegen, Client, dann wieder Backend */
function nextClientWriteRule(current: SolrFieldClientWriteRule): SolrFieldClientWriteRule {
  const index = SOLR_FIELD_CLIENT_WRITE_RULES.indexOf(current)
  return SOLR_FIELD_CLIENT_WRITE_RULES[(index + 1) % SOLR_FIELD_CLIENT_WRITE_RULES.length]
}

/** suggest geht nur bei Typen mit Zwilling, sonst fällt es auf aus */
function withAllowedSettings<T extends SolrFieldData>(field: T): T {
  return { ...field, suggest: field.suggest && isSuggestable(field.type) }
}

interface SolrFieldSheetProps {
  coreKey: string
  /** die Felder dieses Kerns aus den Stammdaten, in Listenreihenfolge */
  solrFields: SolrField[]
  /** Sprache der Beschreibungen, gewählt über die Flaggen der ContentBox */
  language: LanguageCode
  /** nach jedem Speichern, damit die Stammdaten nachziehen */
  onSaved: () => void
  /** Das Plus im Kopf der Box wurde geklickt: unten steht die leere Zeile für ein neues Feld */
  newRowOpen: boolean
  /** die leere Zeile ist weg, nach dem Anlegen oder Abbrechen */
  onNewRowClose: () => void
}

/**
 * Die Felder eines Kerns wie in einer Tabellenkalkulation, in der zentralen Tabelle: eine Zeile je Feld, alles direkt
 * in der Zeile bearbeiten. Schalter und Typ speichern sofort, die Beschreibung kurz nach dem letzten Tippen. Die
 * Statusspalte zeigt, ob eine Zeile gerade gespeichert wird, gespeichert ist oder einen Fehler hat. Die Reihenfolge
 * ändert sich per Drag and Drop am Griff.
 *
 * Ein neues Feld: Das Plus steht im Kopf der Box (der Aufrufer schaltet newRowOpen). Erst nach dem Klick erscheint
 * unten die leere Zeile mit dem Cursor im Namen, Enter oder der Haken legt an, Escape oder das Kreuz bricht ab.
 *
 * Die Breite ist ausgerechnet, damit alles in die Box passt: 80rem Seite minus 2rem Innenabstand sind 78rem. ID 4,
 * Name 10, Typ 7, sechs Schalter je 3, Zugriff 6,5, Schreibregel 8,5, Status 3,5, Aktionen 5,5, die Beschreibung
 * bekommt den Rest, 15rem. Schalter, Status und Aktionen haben senkrechte Köpfe, so bleiben die Spalten schmal.
 *
 * Hier steht jedes normale Feld des Kerns, auch die festen, die jeder Kern hat: id, cursorDate und userGrantedAccess.
 * Bei allen steht der Typ fest und löschen geht nicht. id ist der Primärschlüssel, immer an Position 1 und nicht
 * verschiebbar, die Spalte ID zeigt dort den Schlüssel, und die PK-Schalter (indexed, required, multiValued) stehen
 * fest. Alles andere ist frei. Was in id hineinkommt, steht in seiner Beschreibung. Die Beschreibung jedes Feldes liegt
 * zusätzlich als Hoverlay am Namen.
 *
 * Die Spalte Zugriff ist ein Umschaltknopf je Zeile: Minimum (die Grundansicht, jeder, der das Dokument sehen darf)
 * oder Erweitert (nur mit <KERN>_FULL oder Feld-Freigabe). id ist immer Minimum, das Backend erzwingt es. Die Spalte
 * Schreibregel ist der zweite Umschaltknopf mit drei Stufen: Backend, nur beim Anlegen, Client. Die eingebauten Felder
 * sind immer Backend, und ein Backend-Feld hat keinen Löschknopf, denn das Backend braucht es.
 *
 * Der Aufrufer gibt key={coreKey} mit: Beim Wechsel des Kerns wird die Tabelle neu aufgebaut und vergisst Entwürfe.
 */
export function SolrFieldSheet({ coreKey, solrFields, language, onSaved, newRowOpen, onNewRowClose }: SolrFieldSheetProps) {
  const { t } = useTranslation()
  // Was in den Zeilen steht, sobald jemand tippt: der Entwurf vor dem Stand der Stammdaten, nach dem Speichern die Antwort
  const [drafts, setDrafts] = useState<Map<number, SolrField>>(() => new Map())
  const [statusById, setStatusById] = useState<Map<number, RowStatus>>(() => new Map())
  const timersRef = useRef(new Map<number, ReturnType<typeof setTimeout>>())
  // Nach Drag and Drop sofort die neue Reihenfolge zeigen, ohne auf das Neuladen zu warten
  const [reorderedFields, setReorderedFields] = useState<SolrField[] | null>(null)
  const [positionErrorMessage, setPositionErrorMessage] = useState<string | null>(null)
  const [newField, setNewField] = useState<SolrFieldData>(() => emptySolrFieldData(coreKey))
  const [creating, setCreating] = useState(false)
  const [createErrorMessage, setCreateErrorMessage] = useState<string | null>(null)
  // Sicherheitsabfrage vor dem Löschen: null, solange keine offen ist
  const [solrFieldToDelete, setSolrFieldToDelete] = useState<SolrField | null>(null)

  const shownFields = reorderedFields ?? solrFields
  // id steht immer an Position 1, egal welche Position die Stammdaten kennen, der Rest in Listenreihenfolge
  const orderedFields = [
    ...shownFields.filter((field) => field.name === SOLR_CORE_ID_FIELD_NAME),
    ...shownFields.filter((field) => field.name !== SOLR_CORE_ID_FIELD_NAME),
  ]
  const rows: SheetRow[] = [
    ...orderedFields.map((field): SheetRow => ({ kind: 'field', field })),
    ...(newRowOpen ? [NEW_ROW] : []),
  ]

  function setStatus(id: number, status: RowStatus | null) {
    setStatusById((current) => {
      const next = new Map(current)
      if (status === null) {
        next.delete(id)
      } else {
        next.set(id, status)
      }
      return next
    })
  }

  /** Speichert eine Zeile und übernimmt die Antwort als neuen Stand */
  function saveRow(row: SolrField) {
    setStatus(row.id, { kind: 'saving' })
    updateSolrField(row)
      .then(({ data }) => {
        setDrafts((current) => new Map(current).set(row.id, data))
        setStatus(row.id, { kind: 'saved' })
        onSaved()
      })
      .catch((error: unknown) => {
        setStatus(row.id, {
          kind: 'error',
          message: error instanceof RequestError ? error.message : t('common.unknownError'),
        })
      })
  }

  /**
   * Eine Änderung in einer Zeile: sofort speichern, bei Texten erst nach einer kurzen Pause. Jeder Tastendruck rendert
   * neu, deshalb ist der Entwurf aus dem Render hier immer der aktuelle.
   */
  function changeRow(solrField: SolrField, changes: Partial<SolrField>, delayed = false) {
    const next = withAllowedSettings({ ...(drafts.get(solrField.id) ?? solrField), ...changes })
    setDrafts((current) => new Map(current).set(solrField.id, next))
    clearTimeout(timersRef.current.get(solrField.id))
    if (delayed) {
      timersRef.current.set(
        solrField.id,
        setTimeout(() => saveRow(next), TEXT_SAVE_DELAY_MS),
      )
    } else {
      saveRow(next)
    }
  }

  /**
   * Positionen neu durchzählen (1, 2, 3 …) und alle auf einmal speichern, danach die Stammdaten neu laden. id steht
   * fest ganz oben und bekommt so immer die 1. Die leere Zeile unten zählt nicht mit, sie steht nach dem Neuladen
   * wieder unten.
   */
  function reorderRows(newOrder: SheetRow[]) {
    const fields = newOrder.flatMap((row) => (row.kind === 'field' ? [row.field] : []))
    const positions = positionsByKey(fields, (field) => String(field.id))
    setReorderedFields(fields.map((field) => ({ ...field, listingPosition: positions[String(field.id)] })))
    setPositionErrorMessage(null)
    changeSolrFieldPositions(positions)
      .then(({ data }) => setReorderedFields(data.filter((field) => field.coreKey === coreKey)))
      .catch((error: unknown) => {
        setPositionErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setReorderedFields(null)
      })
      .finally(onSaved)
  }

  function changeNewField(changes: Partial<SolrFieldData>) {
    setNewField((current) => withAllowedSettings({ ...current, ...changes }))
  }

  const canCreate = !creating && isValidSolrFieldName(newField.name)

  function createNewField() {
    if (!canCreate) {
      return
    }
    setCreating(true)
    setCreateErrorMessage(null)
    createSolrField({ ...newField, coreKey })
      .then(() => {
        setNewField(emptySolrFieldData(coreKey))
        setReorderedFields(null)
        onNewRowClose()
        onSaved()
      })
      .catch((error: unknown) => {
        setCreateErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
      })
      .finally(() => setCreating(false))
  }

  /** Die leere Zeile verwerfen, nichts wird angelegt */
  function cancelNewField() {
    setNewField(emptySolrFieldData(coreKey))
    setCreateErrorMessage(null)
    onNewRowClose()
  }

  /** In der neuen Zeile: Enter legt an, Escape bricht ab */
  function newRowKeys(event: KeyboardEvent<HTMLElement>) {
    if (event.key === 'Enter') {
      event.preventDefault()
      createNewField()
    } else if (event.key === 'Escape') {
      event.preventDefault()
      cancelNewField()
    }
  }

  /** Die Werte einer Zeile zum Zeichnen: der Entwurf, sonst der Stand der Stammdaten, unten die neue Zeile */
  function dataOf(row: SheetRow): SolrFieldData {
    return row.kind === 'field' ? (drafts.get(row.field.id) ?? row.field) : newField
  }

  /** Eine Änderung, egal ob an einem bestehenden Feld oder in der leeren Zeile */
  function change(row: SheetRow, changes: Partial<SolrFieldData>, delayed = false) {
    if (row.kind === 'field') {
      changeRow(row.field, changes, delayed)
    } else {
      changeNewField(changes)
    }
  }

  function idOf(row: SheetRow): string {
    return row.kind === 'field' ? `solr-field-${row.field.id}` : 'solr-field-new'
  }

  /** Der Name einer Zeile für Beschriftungen, bei der leeren Zeile „neues Feld“ */
  function nameOf(row: SheetRow): string {
    return dataOf(row).name || t('solrmanager.sheet.newField')
  }

  function statusCell(row: SheetRow) {
    if (row.kind === 'new') {
      if (creating) {
        return <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.creating')} />
      }
      return createErrorMessage !== null ? (
        <Hoverlay text={createErrorMessage}>
          <span className="text-danger">
            <Icon name="error" />
          </span>
        </Hoverlay>
      ) : null
    }
    const status = statusById.get(row.field.id)
    if (status === undefined) {
      return null
    }
    if (status.kind === 'saving') {
      return <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.saving')} />
    }
    if (status.kind === 'saved') {
      return (
        <Hoverlay text={t('common.saved')}>
          <span className="text-success">
            <Icon name="check" />
          </span>
        </Hoverlay>
      )
    }
    return (
      <Hoverlay text={status.message}>
        <span className="text-danger">
          <Icon name="error" />
        </span>
      </Hoverlay>
    )
  }

  function settingColumn(setting: SolrFieldSetting, header: string): TableColumn<SheetRow> {
    return {
      header,
      headerHint: solrFieldSettingHint(t, setting),
      unimportant: true,
      width: '3rem',
      verticalHeader: true,
      cell: (row) => {
        const data = dataOf(row)
        const unavailable = setting === 'suggest' && !isSuggestable(data.type)
        return (
          <input
            id={`${idOf(row)}-${setting}`}
            className="form-check-input"
            type="checkbox"
            aria-label={t('solrmanager.sheet.settingFor', { setting: header, name: nameOf(row) })}
            disabled={creating || unavailable || fixedSettingsOf(row).includes(setting)}
            checked={data[setting]}
            onChange={(event) => change(row, { [setting]: event.target.checked })}
          />
        )
      },
    }
  }

  const columns: TableColumn<SheetRow>[] = [
    {
      header: t('solrmanager.sheet.id'),
      headerHint: solrFieldIdHint(t),
      width: '4rem',
      cell: (row) =>
        isIdRow(row) ? (
          <Hoverlay text={t('solrmanager.sheet.idHoverlay')}>
            <Icon name="documentId" />
          </Hoverlay>
        ) : null,
    },
    {
      header: t('solrmanager.sheet.fieldName'),
      width: '10rem',
      cell: (row) =>
        row.kind === 'field' ? (
          <Hoverlay text={solrFieldDescriptionHint(row.field.name, dataOf(row).description, language)}>
            <strong>{row.field.name}</strong>
          </Hoverlay>
        ) : (
          <span onKeyDown={newRowKeys}>
            <SolrFieldNameInput
              id="solr-field-new-name"
              disabled={creating}
              autoFocus
              value={newField.name}
              onChange={(name) => changeNewField({ name })}
            />
          </span>
        ),
    },
    {
      header: t('solrmanager.sheet.type'),
      headerHint: solrFieldTypeHint(t),
      width: '7rem',
      cell: (row) => {
        const data = dataOf(row)
        return (
          <select
            className="form-select form-select-sm"
            aria-label={t('solrmanager.sheet.typeOf', { name: nameOf(row) })}
            disabled={creating || isBuiltInRow(row)}
            value={data.type}
            onChange={(event) => change(row, { type: event.target.value as SolrFieldType })}
          >
            {SOLR_FIELD_TYPES.map(({ type, label }) => (
              <option key={type} value={type}>
                {label}
              </option>
            ))}
          </select>
        )
      },
    },
    ...SOLR_FIELD_SETTINGS.map(({ setting, label }) => settingColumn(setting, label)),
    {
      header: t('solrmanager.sheet.fieldAccess'),
      headerHint: solrFieldAccessHint(t),
      unimportant: true,
      width: '6.5rem',
      cell: (row) => {
        const minimum = dataOf(row).readAccessLevel === 'MINIMUM_SET_EVERY_READER'
        return (
          <button
            type="button"
            id={`${idOf(row)}-readAccessLevel`}
            className="button-other"
            aria-label={t('solrmanager.sheet.fieldAccessOf', { name: nameOf(row) })}
            // id ist immer Minimum, das Backend erzwingt es
            disabled={creating || isIdRow(row)}
            onClick={() =>
              change(row, {
                readAccessLevel: minimum ? 'ADVANCED_FULL_SIGHT_OR_FIELD_GRANT' : 'MINIMUM_SET_EVERY_READER',
              })
            }
          >
            {minimum ? t('solrmanager.sheet.fieldAccessMinimum') : t('solrmanager.sheet.fieldAccessAdvanced')}
          </button>
        )
      },
    },
    {
      header: t('solrmanager.sheet.clientWriteRule'),
      headerHint: solrFieldClientWriteRuleHint(t),
      unimportant: true,
      width: '8.5rem',
      cell: (row) => {
        const rule = dataOf(row).clientWriteRule
        return (
          <button
            type="button"
            id={`${idOf(row)}-clientWriteRule`}
            className="button-other"
            aria-label={t('solrmanager.sheet.clientWriteRuleOf', { name: nameOf(row) })}
            // die eingebauten Felder sind immer Backend, das Backend erzwingt es
            disabled={creating || isBuiltInRow(row)}
            onClick={() => change(row, { clientWriteRule: nextClientWriteRule(rule) })}
          >
            {t(`solrmanager.sheet.clientWriteRules.${rule}`)}
          </button>
        )
      },
    },
    {
      header: t('solrmanager.sheet.description'),
      unimportant: true,
      cell: (row) => (
        <MultilingualInput
          id={`${idOf(row)}-description`}
          language={language}
          disabled={creating && row.kind === 'new'}
          value={dataOf(row).description}
          onChange={(description) => change(row, { description }, true)}
        />
      ),
    },
    { header: t('solrmanager.sheet.status'), width: '3.5rem', verticalHeader: true, cell: statusCell },
  ]

  return (
    <>
      <Table
        columns={columns}
        rows={rows}
        rowKey={(row) => (row.kind === 'field' ? String(row.field.id) : 'new')}
        onReorder={reorderRows}
        // id bleibt ganz oben, die leere Zeile ganz unten, nur die Felder dazwischen lassen sich verschieben
        fixedRow={(row) => row.kind === 'new' || isIdRow(row)}
        detailTitle={(row) => (row.kind === 'field' ? row.field.name : t('solrmanager.sheet.newField'))}
        emptyText={t('solrmanager.sheet.empty')}
        actionsWidth="5.5rem"
        actionsHeaderVertical
        actions={(row) => {
          if (row.kind === 'new') {
            // Haken legt an, Kreuz verwirft die Zeile
            return (
              <>
                <Hoverlay text={t('common.save')}>
                  <button
                    type="button"
                    className="button-save"
                    aria-label={t('common.save')}
                    disabled={!canCreate}
                    onClick={createNewField}
                  >
                    <Icon name="check" />
                  </button>
                </Hoverlay>
                <Hoverlay text={t('common.cancel')}>
                  <button
                    type="button"
                    className="button-cancel"
                    aria-label={t('common.cancel')}
                    disabled={creating}
                    onClick={cancelNewField}
                  >
                    <Icon name="close" />
                  </button>
                </Hoverlay>
              </>
            )
          }
          if (isBuiltInRow(row) || dataOf(row).clientWriteRule === 'BACKEND_ONLY') {
            // die eingebauten Felder hat jeder Kern, und ein Backend-Feld braucht das Backend: kein Löschknopf
            return null
          }
          return <DeleteButton label={t('common.deleteAction')} onClick={() => setSolrFieldToDelete(row.field)} />
        }}
      />
      {positionErrorMessage !== null && <p className="text-danger mb-0 mt-2">{positionErrorMessage}</p>}
      {createErrorMessage !== null && <p className="text-danger mb-0 mt-2">{createErrorMessage}</p>}
      <DeletePopup
        open={solrFieldToDelete !== null}
        title={t('solrmanager.sheet.deleteTitle')}
        onConfirm={() => deleteSolrField(solrFieldToDelete?.id ?? 0)}
        onDeleted={() => {
          const deletedId = solrFieldToDelete?.id
          setDrafts((current) => {
            const next = new Map(current)
            if (deletedId !== undefined) {
              next.delete(deletedId)
            }
            return next
          })
          setReorderedFields(null)
          onSaved()
        }}
        onClose={() => setSolrFieldToDelete(null)}
      >
        <Trans
          i18nKey="solrmanager.sheet.deleteQuestion"
          values={{ name: solrFieldToDelete?.name ?? '', core: coreKey }}
          components={{ strong: <strong /> }}
        />
      </DeletePopup>
    </>
  )
}
