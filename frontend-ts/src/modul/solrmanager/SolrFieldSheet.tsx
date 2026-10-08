import { useState, type KeyboardEvent } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import type { LanguageCode } from '../../branding/languages.ts'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { MultilingualInput } from '../../components/MultilingualInput.tsx'
import { Popup } from '../../components/Popup.tsx'
import { positionsByKey } from '../../components/positions.ts'
import { isValidSolrFieldName } from '../../components/solrFieldNameRule.ts'
import { SolrFieldNameInput } from '../../components/SolrFieldNameInput.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { AccessRoleBadge } from '../accessrole/AccessRoleBadge.tsx'
import { BadgeToggleList } from '../accessrole/BadgeToggleList.tsx'
import {
  changeSolrFieldPositions,
  createSolrField,
  deleteSolrField,
  fieldReadAccessRoleCandidates,
  isClientAllowedToSetOnCreate,
  isSuggestable,
  SOLR_FIELD_CLIENT_WRITE_RULES,
  SOLR_FIELD_SETTINGS,
  SOLR_FIELD_TYPES,
  updateSolrField,
  type SolrField,
  type SolrFieldClientWriteRule,
  type SolrFieldData,
  type SolrFieldSetting,
  type SolrFieldType,
} from './SolrField.ts'
import {
  solrFieldAccessHint,
  solrFieldBackendOnlyHint,
  solrFieldClientWriteRuleHint,
  solrFieldNameHint,
  solrFieldSettingHint,
  solrFieldTypeHint,
} from './solrFieldHints.tsx'

type RowStatus = { kind: 'saving' } | { kind: 'saved' } | { kind: 'error'; message: string }

/** Eine Zeile der Tabelle: ein bestehendes Feld, oder ganz unten die leere Zeile für ein neues */
type SheetRow = { kind: 'field'; field: SolrField } | { kind: 'new' }

const NEW_ROW: SheetRow = { kind: 'new' }

/** Die leere Zeile unten: indexed und stored an, der Rest aus, niemand liest, Schreibregel Backend */
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
    readAccessRoleKeys: [],
    backendOnly: false,
    clientWriteRule: 'BACKEND_ONLY',
    displayName: null,
    description: null,
    listingPosition: 0,
  }
}

/** Die Kurzform des Solr-Namens für die Spalte: die ersten vier Zeichen und zwei Punkte, kurze Namen ganz */
function shortSolrFieldName(name: string): string {
  return name.length > 4 ? `${name.slice(0, 4)}..` : name
}

/** Die nächste Stufe der Schreibregel: Backend, nur beim Anlegen, Client, dann wieder Backend */
function nextClientWriteRule(current: SolrFieldClientWriteRule): SolrFieldClientWriteRule {
  const index = SOLR_FIELD_CLIENT_WRITE_RULES.indexOf(current)
  return SOLR_FIELD_CLIENT_WRITE_RULES[(index + 1) % SOLR_FIELD_CLIENT_WRITE_RULES.length]
}

/**
 * suggest geht nur bei Typen mit Zwilling, sonst fällt es auf aus; nur Backend heißt, keine AR liest; required heißt,
 * der Client muss es beim Anlegen mitschicken, bei der Schreibregel Backend fällt es auf aus (User 2026-10-09)
 */
function withAllowedSettings<T extends SolrFieldData>(field: T): T {
  return {
    ...field,
    suggest: field.suggest && isSuggestable(field.type),
    required: field.required && isClientAllowedToSetOnCreate(field.clientWriteRule),
    readAccessRoleKeys: field.backendOnly ? [] : field.readAccessRoleKeys,
  }
}

interface SolrFieldSheetProps {
  coreKey: string
  /** die Felder dieses Kerns aus den Stammdaten, in Listenreihenfolge, die Felder jedes Kerns zuerst */
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
 * in der Zeile bearbeiten. Schalter und Typ speichern sofort; Anzeigename und Beschreibung erst beim Verlassen des
 * Feldes oder mit Enter, nicht bei jedem Tastendruck (User 2026-10-07). Die Statusspalte zeigt, ob eine Zeile gerade
 * gespeichert wird, gespeichert ist oder einen Fehler hat. Die Reihenfolge ändert sich per Drag and Drop am Griff.
 *
 * Ein neues Feld: Das Plus steht im Kopf der Box (der Aufrufer schaltet newRowOpen). Erst nach dem Klick erscheint
 * unten die leere Zeile mit dem Cursor im Namen, Enter oder der Haken legt an, Escape oder das Kreuz bricht ab.
 *
 * Die Breite ist ausgerechnet, damit alles in die Box passt: 80rem Seite minus 2rem Innenabstand sind 78rem. Name 4,5,
 * Anzeigename 10, Typ 6,5, sechs Schalter je 3, nur Backend 3, Lesen 9, Schreibregel 8,5, Status 3,5, Aktionen 5,5,
 * die Beschreibung bekommt den Rest. Schalter, Status und Aktionen haben senkrechte Köpfe, so bleiben die Spalten
 * schmal.
 *
 * Die Spalte Name zeigt nur die ersten vier Zeichen des Solr-Namens und zwei Punkte (User 2026-10-09): Namen wie
 * bildVorschauBase64Png brauchen in der Spalte zu viel Platz, der ganze Name steht mit der Beschreibung im Hoverlay.
 * Sichtbar daneben ist der Anzeigename. In der leeren Zeile steht an dessen Stelle die Eingabe des Solr-Namens, der
 * Anzeigename kommt nach dem Anlegen.
 *
 * Ein Feld, das in jedem Kern liegt (id, cursorDate, userGrantedAccess), trägt ein zweites Symbol in der Spalte Name
 * und erscheint in jedem Kern-Tab, bearbeitet wird dieselbe Zeile.
 *
 * Die Spalte Lesen (2026-10-09) zeigt die ARs, deren Inhaber das Feld bekommen, als kompakte Badges; Klick öffnet das
 * Popup mit USER und den Feld-ARs (SOLR_FIELD_*) zum Ankreuzen, jeder Haken speichert sofort. Ohne AR liest niemand
 * außer SOLR_USERS_VIEW_ALL, USER heißt „immer“. Nur Backend heißt: nie zum Client, auch nicht mit ALL. Die Spalte
 * Schreibregel ist der Umschaltknopf mit drei Stufen: Backend, nur beim Anlegen, Client. Ein Backend-Feld hat keinen
 * Löschknopf, denn das Backend braucht es, und sein required ist aus und gesperrt: Pflicht beim Anlegen gibt es nur
 * bei Feldern, die der Client mitschickt (User 2026-10-09).
 *
 * Der Aufrufer gibt key={coreKey} mit: Beim Wechsel des Kerns wird die Tabelle neu aufgebaut und vergisst Entwürfe.
 */
export function SolrFieldSheet({ coreKey, solrFields, language, onSaved, newRowOpen, onNewRowClose }: SolrFieldSheetProps) {
  const { t } = useTranslation()
  const { accessRoles } = useMasterData()
  // Was in den Zeilen steht, sobald jemand tippt: der Entwurf vor dem Stand der Stammdaten, nach dem Speichern die Antwort
  const [drafts, setDrafts] = useState<Map<number, SolrField>>(() => new Map())
  const [statusById, setStatusById] = useState<Map<number, RowStatus>>(() => new Map())
  // Zeilen, deren Texte getippt, aber noch nicht gespeichert sind: gespeichert wird beim Verlassen des Feldes
  const [dirtyIds, setDirtyIds] = useState<Set<number>>(() => new Set())
  // Nach Drag and Drop sofort die neue Reihenfolge zeigen, ohne auf das Neuladen zu warten
  const [reorderedFields, setReorderedFields] = useState<SolrField[] | null>(null)
  const [positionErrorMessage, setPositionErrorMessage] = useState<string | null>(null)
  const [newField, setNewField] = useState<SolrFieldData>(() => emptySolrFieldData(coreKey))
  const [creating, setCreating] = useState(false)
  const [createErrorMessage, setCreateErrorMessage] = useState<string | null>(null)
  // Sicherheitsabfrage vor dem Löschen: null, solange keine offen ist
  const [solrFieldToDelete, setSolrFieldToDelete] = useState<SolrField | null>(null)
  // Das Popup mit den ARs zum Ankreuzen: die Zeile, deren Leseliste gerade bearbeitet wird
  const [accessRow, setAccessRow] = useState<SheetRow | null>(null)

  const shownFields = reorderedFields ?? solrFields
  const rows: SheetRow[] = [
    ...shownFields.map((field): SheetRow => ({ kind: 'field', field })),
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
   * Eine Änderung in einer Zeile: Schalter und Typ sofort speichern, ein Text bleibt Entwurf, bis das Feld verlassen
   * wird (saveDraft). Jeder Tastendruck rendert neu, deshalb ist der Entwurf aus dem Render hier immer der aktuelle.
   */
  function changeRow(solrField: SolrField, changes: Partial<SolrField>, textOnly = false) {
    const next = withAllowedSettings({ ...(drafts.get(solrField.id) ?? solrField), ...changes })
    setDrafts((current) => new Map(current).set(solrField.id, next))
    if (textOnly) {
      setDirtyIds((current) => new Set(current).add(solrField.id))
    } else {
      saveRow(next)
    }
  }

  /** Das Textfeld wurde verlassen oder Enter gedrückt: den Entwurf speichern, wenn seit dem letzten Speichern getippt wurde */
  function saveDraft(row: SheetRow) {
    if (row.kind !== 'field' || !dirtyIds.has(row.field.id)) {
      return
    }
    setDirtyIds((current) => {
      const next = new Set(current)
      next.delete(row.field.id)
      return next
    })
    saveRow(drafts.get(row.field.id) ?? row.field)
  }

  /**
   * Positionen neu durchzählen (1, 2, 3 …) und alle auf einmal speichern, danach die Stammdaten neu laden. Die leere
   * Zeile unten zählt nicht mit, sie steht nach dem Neuladen wieder unten.
   */
  function reorderRows(newOrder: SheetRow[]) {
    const fields = newOrder.flatMap((row) => (row.kind === 'field' ? [row.field] : []))
    const positions = positionsByKey(fields, (field) => String(field.id))
    setReorderedFields(fields.map((field) => ({ ...field, listingPosition: positions[String(field.id)] })))
    setPositionErrorMessage(null)
    changeSolrFieldPositions(positions)
      .then(({ data }) => setReorderedFields(data.filter((field) => field.coreKey === null || field.coreKey === coreKey)))
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

  /** Eine Änderung, egal ob an einem bestehenden Feld oder in der leeren Zeile; textOnly: nur Entwurf, speichern beim Verlassen */
  function change(row: SheetRow, changes: Partial<SolrFieldData>, textOnly = false) {
    if (row.kind === 'field') {
      changeRow(row.field, changes, textOnly)
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
        const unavailable =
          (setting === 'suggest' && !isSuggestable(data.type)) ||
          (setting === 'required' && !isClientAllowedToSetOnCreate(data.clientWriteRule))
        return (
          <input
            id={`${idOf(row)}-${setting}`}
            className="form-check-input"
            type="checkbox"
            aria-label={t('solrmanager.sheet.settingFor', { setting: header, name: nameOf(row) })}
            disabled={creating || unavailable}
            checked={data[setting]}
            onChange={(event) => change(row, { [setting]: event.target.checked })}
          />
        )
      },
    }
  }

  /** Die Leseliste einer Zeile als kompakte Badges, bei nur Backend der Hinweis, ohne AR „niemand“ */
  function accessCell(row: SheetRow) {
    const data = dataOf(row)
    if (data.backendOnly) {
      return <span className="text-body-secondary small">{t('solrmanager.sheet.backendOnly')}</span>
    }
    const badges = (accessRoles ?? []).filter((accessRole) => data.readAccessRoleKeys.includes(accessRole.key))
    return (
      <button
        type="button"
        id={`${idOf(row)}-readAccessRoleKeys`}
        className="button-other"
        aria-label={t('solrmanager.sheet.fieldAccessOf', { name: nameOf(row) })}
        disabled={creating}
        onClick={() => setAccessRow(row)}
      >
        {badges.length === 0 ? (
          <span className="small">{t('solrmanager.sheet.fieldAccessNone')}</span>
        ) : (
          <span className="d-inline-flex flex-wrap gap-1">
            {badges.map((accessRole) => (
              <AccessRoleBadge key={accessRole.key} badge={accessRole} size="compact" withHoverlay={false} />
            ))}
          </span>
        )}
      </button>
    )
  }

  const columns: TableColumn<SheetRow>[] = [
    {
      header: t('solrmanager.sheet.name'),
      width: '4.5rem',
      cell: (row) =>
        row.kind === 'field' ? (
          <span className="d-inline-flex align-items-center gap-1">
            <Hoverlay text={solrFieldNameHint(row.field.name, dataOf(row).description, language)}>
              <strong>{shortSolrFieldName(row.field.name)}</strong>
            </Hoverlay>
            {row.field.coreKey === null && (
              <Hoverlay text={t('solrmanager.sheet.everyCore')}>
                <span className="text-body-secondary">
                  <Icon name="solrCores" />
                </span>
              </Hoverlay>
            )}
          </span>
        ) : null,
    },
    {
      header: t('solrmanager.sheet.displayName'),
      width: '10rem',
      cell: (row) =>
        row.kind === 'field' ? (
          <MultilingualInput
            id={`${idOf(row)}-displayName`}
            language={language}
            value={dataOf(row).displayName}
            onChange={(displayName) => change(row, { displayName }, true)}
            onBlur={() => saveDraft(row)}
            onEnter={() => saveDraft(row)}
          />
        ) : (
          // die neue Zeile: hier der Solr-Name, der Anzeigename kommt nach dem Anlegen
          <Hoverlay text={t('solrmanager.sheet.fieldName')}>
            <span onKeyDown={newRowKeys}>
              <SolrFieldNameInput
                id="solr-field-new-name"
                disabled={creating}
                autoFocus
                value={newField.name}
                onChange={(name) => changeNewField({ name })}
              />
            </span>
          </Hoverlay>
        ),
    },
    {
      header: t('solrmanager.sheet.type'),
      headerHint: solrFieldTypeHint(t),
      width: '6.5rem',
      cell: (row) => {
        const data = dataOf(row)
        return (
          <select
            className="form-select form-select-sm"
            aria-label={t('solrmanager.sheet.typeOf', { name: nameOf(row) })}
            disabled={creating}
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
      header: t('solrmanager.sheet.backendOnly'),
      headerHint: solrFieldBackendOnlyHint(t),
      unimportant: true,
      width: '3rem',
      verticalHeader: true,
      cell: (row) => (
        <input
          id={`${idOf(row)}-backendOnly`}
          className="form-check-input"
          type="checkbox"
          aria-label={t('solrmanager.sheet.backendOnlyOf', { name: nameOf(row) })}
          disabled={creating}
          checked={dataOf(row).backendOnly}
          onChange={(event) => change(row, { backendOnly: event.target.checked })}
        />
      ),
    },
    {
      header: t('solrmanager.sheet.fieldAccess'),
      headerHint: solrFieldAccessHint(t),
      unimportant: true,
      width: '9rem',
      cell: accessCell,
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
            disabled={creating}
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
          onBlur={() => saveDraft(row)}
          onEnter={() => saveDraft(row)}
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
        // die leere Zeile bleibt ganz unten, nur die Felder lassen sich verschieben
        fixedRow={(row) => row.kind === 'new'}
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
          if (dataOf(row).clientWriteRule === 'BACKEND_ONLY') {
            // ein Backend-Feld braucht das Backend: kein Löschknopf
            return null
          }
          return <DeleteButton label={t('common.deleteAction')} onClick={() => setSolrFieldToDelete(row.field)} />
        }}
      />
      {positionErrorMessage !== null && <p className="text-danger mb-0 mt-2">{positionErrorMessage}</p>}
      {createErrorMessage !== null && <p className="text-danger mb-0 mt-2">{createErrorMessage}</p>}
      <Popup
        open={accessRow !== null}
        title={t('solrmanager.sheet.fieldAccessEdit', { name: accessRow ? nameOf(accessRow) : '' })}
        onClose={() => setAccessRow(null)}
      >
        {accessRow !== null && (
          <>
            <p>{t('solrmanager.sheet.fieldAccessIntro')}</p>
            <BadgeToggleList
              badges={fieldReadAccessRoleCandidates(accessRoles ?? [], dataOf(accessRow).readAccessRoleKeys)}
              selectedKeys={dataOf(accessRow).readAccessRoleKeys}
              onChange={(readAccessRoleKeys) => change(accessRow, { readAccessRoleKeys })}
              shape="rect"
              disabled={creating}
            />
          </>
        )}
      </Popup>
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
