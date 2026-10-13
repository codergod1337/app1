import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { KeyInput } from '../../components/KeyInput.tsx'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import { isValidKey } from '../../components/keyRule.ts'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { positionsByKey } from '../../components/positions.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { showToast } from '../../components/toastStore.ts'
import { type AbasInfosys, abasInfosysPlaceholderNamesOf, useAbasInfosysList } from '../infosys/AbasInfosys.ts'
import { type SolrField, updateSolrField } from '../solrmanager/SolrField.ts'
import { SolrFieldPicker } from '../solrmanager/SolrFieldPicker.tsx'
import { evaluateLine, evaluatePath, evaluateTableRows, valueText } from './jsonPath.ts'
import {
  changeResponseMappingLinePositions,
  ignoredPathsOf,
  isSubfetchKind,
  matchConditionsOf,
  newResponseMappingLine,
  type ResponseMappingLine,
  type ResponseMappingLineIdRole,
  type ResponseMappingLineKind,
  type ResponseMappingMatchCondition,
  type ResponseMappingSubfetchInputParameter,
  type ResponseMappingSubfetchKind,
  type ResponseMappingTableColumn,
  type ResponseMappingWithLines,
  STERNZEIT_PARAMETER_NAME,
  subfetchInputParametersOf,
  tableColumnNameOf,
  tableColumnsOf,
  tableLineOf,
} from './ResponseMapping.ts'

/** Höchstens so viele Werte einer Liste stehen im Blatt, der Rest als Zahl */
const SHOWN_LIST_VALUES = 5

/** Das Präfix im Ziel, hinter dem der Key des Solr-Kerns steht: SOLR_ARTIKEL füllt den Kern ARTIKEL */
const SOLR_TARGET_PREFIX = 'SOLR_'

/** Welche Eingabe gerade im Baum gewählt wird: der Eintrag und der Name der Eingabe */
export interface SubfetchInputPick {
  lineId: number | null
  parameterName: string
}

interface ResponseMappingEditorProps {
  /** id-Präfix der Felder, eindeutig je Einbau */
  idPrefix: string
  value: ResponseMappingWithLines
  onChange: (value: ResponseMappingWithLines) => void
  disabled: boolean
  /** beim Bearbeiten eines gespeicherten Mappings: der Name ändert sich nie */
  keyLocked: boolean
  /** im Entwurf: die geholte Antwort, aus der die Spalte „Wert(e)“ je Eintrag live gerechnet wird */
  sampleJson?: unknown
  /** im Entwurf: einen Eintrag zum kennzeichnenden Feld machen */
  onMakeCondition?: (line: ResponseMappingLine) => void
  /** im Entwurf: ein Klick auf den Wert springt im Baum an seine Quelle */
  onJumpToLine?: (line: ResponseMappingLine) => void
  /** im Entwurf: einen Subfetch-Eintrag jetzt gegen die gezeigte Antwort holen (User 2026-10-10) */
  onFetchSubfetch?: (line: ResponseMappingLine) => void
  /** im Entwurf: der nächste Klick im Baum setzt die Adresse dieser Eingabe; null hebt die Wahl auf */
  onPickSubfetchInput?: (pick: SubfetchInputPick | null) => void
  /** im Entwurf: welche Eingabe gerade im Baum gewählt wird */
  subfetchInputPick?: SubfetchInputPick | null
}

/**
 * Der Editor eines Gesamt-Mappings, im Entwurf und beim Bearbeiten derselbe: Name, Ziel, Typ, die kennzeichnenden
 * Felder, die Einträge als Tabellenblatt (Key, Id, Wert(e), Solr-Felder, JSON-Pfad, Art, Bedingung) mit allen Feldern
 * direkt in der Zeile, darunter die Subfetches als eigene Tabelle (User 2026-10-10): erst die Art, Sternzeit oder
 * Infosystem, dann je Eingabe aus dem JSON oder statisch, bei einem Infosystem der hinterlegte Aufruf und die
 * Platzhalter seiner Vorlage; zuletzt die Ignorierliste. Speichert nichts selbst, jede Änderung geht an onChange; die
 * Pflichtfelder prüft das umgebende Formular beim Abschicken.
 */
export function ResponseMappingEditor({
  idPrefix,
  value,
  onChange,
  disabled,
  keyLocked,
  sampleJson,
  onMakeCondition,
  onJumpToLine,
  onFetchSubfetch,
  onPickSubfetchInput,
  subfetchInputPick = null,
}: ResponseMappingEditorProps) {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const { solrFields, reloadMasterData } = useMasterData()
  const { abasInfosysList } = useAbasInfosysList()
  const mapping = value.responseMapping
  const conditions = matchConditionsOf(mapping)
  const ignoredPaths = ignoredPathsOf(mapping)
  const valueLines = value.lines.filter((line) => !isSubfetchKind(line.kind))
  const subfetchLines = value.lines.filter((line) => isSubfetchKind(line.kind))

  /** Der Kern des Ziels, SOLR_ARTIKEL heißt ARTIKEL; ohne Ziel null, dann sind alle Felder Kandidaten */
  const targetCoreKey = mapping.targetKey !== null && mapping.targetKey.startsWith(SOLR_TARGET_PREFIX) ? mapping.targetKey.slice(SOLR_TARGET_PREFIX.length) : null

  /** Die Felder, die der Key dieses Eintrags füllt: der lose Fremdschlüssel solr_field.command, aus den Stammdaten */
  function linkedSolrFieldsOf(line: ResponseMappingLine): SolrField[] {
    return (solrFields ?? []).filter((solrField) => solrField.command !== null && solrField.command === line.key)
  }

  /** Die Felder, die sich verknüpfen lassen: alle; was schon ein Mapping füllt, zeigt die Auswahl mit dessen Key und wird beim Wählen umgehängt */
  const linkCandidates = solrFields ?? []

  /** Das Feld an den Key hängen oder lösen: schreibt solr_field.command über den Feld-Endpunkt, dann ziehen die Stammdaten nach */
  function linkSolrField(solrField: SolrField, command: string | null) {
    updateSolrField({ ...solrField, command })
      .then(() => {
        showToast({
          kind: 'success',
          title: command === null ? t('mapping.editor.unlinked', { field: solrField.name }) : t('mapping.editor.linked', { field: solrField.name, key: command }),
        })
        reloadMasterData()
      })
      .catch((error: unknown) => showToast({ kind: 'error', title: error instanceof RequestError ? error.message : t('common.unknownError') }))
  }

  function changeMapping(change: Partial<ResponseMappingWithLines['responseMapping']>) {
    onChange({ ...value, responseMapping: { ...mapping, ...change } })
  }

  function changeConditions(changed: ResponseMappingMatchCondition[]) {
    changeMapping({ matchConditionsJson: JSON.stringify(changed) })
  }

  /** Alle Einträge neu setzen, Werte zuerst, Subfetches dahinter, Positionen durchgezählt */
  function changeLines(changedValueLines: ResponseMappingLine[], changedSubfetchLines: ResponseMappingLine[] = subfetchLines) {
    onChange({ ...value, lines: [...changedValueLines, ...changedSubfetchLines].map((line, index) => ({ ...line, position: index + 1 })) })
  }

  function changeLine(target: ResponseMappingLine, change: Partial<ResponseMappingLine>) {
    const changed = (line: ResponseMappingLine) => (line === target ? { ...line, ...change } : line)
    changeLines(valueLines.map(changed), subfetchLines.map(changed))
  }

  /**
   * Nach Drag and Drop: neu durchzählen, und bei einem gespeicherten Mapping die Positionen sofort sichern, wie bei den
   * AR (User 2026-10-10). Nur wenn jeder Eintrag schon eine id hat; ein neuer Eintrag ohne id nimmt die Reihenfolge erst
   * beim Speichern mit. Scheitert das Sichern, bleibt die Reihenfolge im Blatt, der Toast sagt warum.
   */
  function reorderLines(rows: ResponseMappingLine[]) {
    changeLines(rows)
    const all = [...rows, ...subfetchLines]
    if (mapping.id === null || !all.every((line) => line.id !== null && line.id > 0)) {
      return
    }
    changeResponseMappingLinePositions(positionsByKey(all, (line) => String(line.id))).catch((error: unknown) =>
      showToast({ kind: 'error', title: t('mapping.editor.positionsNotSaved'), text: error instanceof RequestError ? error.message : t('common.unknownError') }),
    )
  }

  function addValueLine() {
    changeLines([...valueLines, newResponseMappingLine('VALUE', value.lines.length + 1)])
  }

  function addSubfetchLine() {
    changeLines(valueLines, [...subfetchLines, newResponseMappingLine('SUBFETCH_STERNZEIT', value.lines.length + 1)])
  }

  function removeButton(label: string, onClick: () => void) {
    return (
      <Hoverlay text={label}>
        <button type="button" className="button-other" aria-label={label} disabled={disabled} onClick={onClick}>
          <Icon name="close" />
        </button>
      </Hoverlay>
    )
  }

  function textInput(current: string | null, placeholder: string, onChangeText: (text: string) => void, required = false) {
    return (
      <input
        className="form-control form-control-sm font-monospace"
        required={required}
        disabled={disabled}
        value={current ?? ''}
        placeholder={placeholder}
        aria-label={placeholder}
        onChange={(event) => onChangeText(event.target.value)}
      />
    )
  }

  function jumpWrapped(line: ResponseMappingLine, shown: React.ReactNode) {
    if (onJumpToLine === undefined) {
      return shown
    }
    return (
      <Hoverlay text={t('mapping.editor.jumpToValue')}>
        <button type="button" className="btn btn-link btn-sm p-0 border-0 text-start text-reset" onClick={() => onJumpToLine(line)}>
          {shown}
        </button>
      </Hoverlay>
    )
  }

  /** Die Werte eines Eintrags aus der Antwort, bei einer Liste die ersten mit Zahl, bei einer Tabelle die Zeilen; ein Klick springt im Baum an die Quelle */
  function valuesCell(line: ResponseMappingLine) {
    if (sampleJson === undefined || line.sourcePath.trim() === '') {
      return null
    }
    if (line.kind === 'TABLE') {
      const columns = tableColumnsOf(line)
      const rows = evaluateTableRows(sampleJson, line.sourcePath, columns)
      const shown =
        columns.length === 0 ? (
          <span className="text-danger">{t('mapping.editor.tableNoColumns')}</span>
        ) : (
          <span className="d-inline-flex flex-wrap gap-1 align-items-center">
            {rows.slice(0, 2).map((row, index) => (
              <code key={index} className="small text-break">
                {JSON.stringify(row)}
              </code>
            ))}
            <span className="badge text-bg-secondary">{t('mapping.editor.tableRowCount', { count: rows.length })}</span>
          </span>
        )
      return jumpWrapped(line, shown)
    }
    const { list, values } = evaluateLine(sampleJson, line.sourcePath, line.elementPath)
    let shown
    if (!list) {
      shown = values.length === 0 ? <span className="text-danger">{t('mapping.editor.noValue')}</span> : <span className="text-break">{valueText(values[0])}</span>
    } else {
      shown = (
        <span className="d-inline-flex flex-wrap gap-1 align-items-center">
          {values.slice(0, SHOWN_LIST_VALUES).map((element, index) => (
            <span key={index} className="badge text-bg-light fw-normal text-break">
              {valueText(element)}
            </span>
          ))}
          <span className="badge text-bg-secondary">{t('mapping.editor.listCount', { count: values.length })}</span>
        </span>
      )
    }
    return jumpWrapped(line, shown)
  }

  /** Die Spalten eines Tabellen-Eintrags: je Spalte Name und Adresse je Element, ein Klick im Baum legt eine an */
  function tableColumnsEditor(line: ResponseMappingLine) {
    const columns = tableColumnsOf(line)
    const setColumns = (changed: ResponseMappingTableColumn[]) => changeLine(line, { tableColumnsJson: JSON.stringify(changed) })
    return (
      <div className="d-flex flex-column gap-1">
        {columns.map((column, index) => (
          <div key={index} className="d-flex gap-1 align-items-center">
            {textInput(column.name, t('mapping.editor.tableColumnName'), (name) => setColumns(columns.map((candidate, candidateIndex) => (candidateIndex === index ? { ...candidate, name } : candidate))), true)}
            {textInput(
              column.elementPath,
              t('mapping.editor.elementPlaceholder'),
              (elementPath) =>
                setColumns(
                  columns.map((candidate, candidateIndex) =>
                    candidateIndex === index ? { elementPath, name: candidate.name === '' || candidate.name === tableColumnNameOf(candidate.elementPath) ? tableColumnNameOf(elementPath) : candidate.name } : candidate,
                  ),
                ),
              true,
            )}
            {removeButton(t('mapping.editor.removeTableColumn'), () => setColumns(columns.filter((_, candidateIndex) => candidateIndex !== index)))}
          </div>
        ))}
        <div>
          <NewEntityButton entity="ResponseMappingTableColumn" disabled={disabled} onClick={() => setColumns([...columns, { name: '', elementPath: '' }])} />
        </div>
      </div>
    )
  }

  const kindLabels: Record<'VALUE' | 'TABLE', string> = {
    VALUE: t('mapping.editor.kindValue'),
    TABLE: t('mapping.editor.kindTable'),
  }

  const columns: TableColumn<ResponseMappingLine>[] = [
    // die Position wie bei den AR: Nummer in der ersten Spalte, dort sitzt auch der Griff zum Verschieben; im festen
    // Layout braucht die Spalte eine Breite für Griff und Nummer, sonst läuft sie in die Key-Spalte
    { header: '#', cell: (line) => line.position, unimportant: true, width: '4.5rem' },
    {
      header: t('mapping.editor.columns.key'),
      headerHint: t('mapping.editor.keyHint'),
      cell: (line) => (
        <KeyInput id={`${idPrefix}-line-${line.id ?? line.position}-key`} required disabled={disabled} value={line.key} onChange={(key) => changeLine(line, { key })} />
      ),
      width: '13rem',
    },
    {
      header: t('mapping.editor.columns.id'),
      headerHint: t('mapping.editor.idHint'),
      cell: (line) => (
        <select
          className="form-select form-select-sm"
          disabled={disabled}
          value={line.idRole ?? ''}
          aria-label={t('mapping.editor.columns.id')}
          onChange={(event) =>
            changeLine(line, { idRole: event.target.value === '' ? null : (event.target.value as ResponseMappingLineIdRole) })
          }
        >
          <option value="">{t('mapping.editor.idNone')}</option>
          <option value="PRIMARY">{t('mapping.editor.idPrimary')}</option>
          <option value="COMPOSITE">{t('mapping.editor.idComposite')}</option>
          <option value="ALTERNATIVE">{t('mapping.editor.idAlternative')}</option>
        </select>
      ),
      width: '7rem',
    },
  ]
  if (sampleJson !== undefined) {
    columns.push({ header: t('mapping.editor.columns.values'), cell: valuesCell })
  }
  columns.push({
    header: t('mapping.editor.columns.solrFields'),
    headerHint: t('mapping.editor.solrFieldsHint'),
    cell: (line) => solrFieldsCell(line),
    width: '14rem',
  })
  columns.push(
    {
      header: t('mapping.editor.columns.path'),
      headerHint: t('mapping.editor.sourceHint'),
      cell: (line) => (
        <div className="d-flex flex-column gap-1">
          {textInput(line.sourcePath, t('mapping.editor.sourcePlaceholder'), (sourcePath) => changeLine(line, { sourcePath }), true)}
          {line.kind === 'VALUE' &&
            textInput(line.elementPath, t('mapping.editor.elementPlaceholder'), (elementPath) => changeLine(line, { elementPath: elementPath === '' ? null : elementPath }))}
          {line.kind === 'TABLE' && tableColumnsEditor(line)}
        </div>
      ),
    },
    {
      header: t('mapping.editor.columns.kind'),
      cell: (line) => (
        <select
          className="form-select form-select-sm"
          disabled={disabled}
          value={line.kind}
          aria-label={t('mapping.editor.columns.kind')}
          onChange={(event) => {
            // von Wert zu Tabelle: die Adresse je Element wird die erste Spalte; zurück: die erste Spalte wird die Adresse
            const kind = event.target.value as ResponseMappingLineKind
            if (kind === 'TABLE') {
              const first = line.elementPath === null || line.elementPath === '' ? [] : [{ name: tableColumnNameOf(line.elementPath), elementPath: line.elementPath }]
              changeLine(line, { kind, elementPath: null, tableColumnsJson: JSON.stringify(first) })
            } else {
              const firstColumn = tableColumnsOf(line)[0]
              changeLine(line, { kind, elementPath: firstColumn?.elementPath ?? null, tableColumnsJson: null })
            }
          }}
        >
          {(Object.keys(kindLabels) as ('VALUE' | 'TABLE')[]).map((kind) => (
            <option key={kind} value={kind}>
              {kindLabels[kind]}
            </option>
          ))}
        </select>
      ),
      unimportant: true,
      width: '9rem',
    },
    {
      header: t('mapping.editor.columns.onlyIf'),
      headerHint: t('mapping.editor.onlyIfHint'),
      cell: (line) => (
        <div className="d-flex flex-column gap-1">
          {textInput(line.onlyIfPath, t('mapping.editor.onlyIfPathPlaceholder'), (onlyIfPath) =>
            changeLine(line, { onlyIfPath: onlyIfPath === '' ? null : onlyIfPath, onlyIfEquals: onlyIfPath === '' ? null : (line.onlyIfEquals ?? '') }),
          )}
          {line.onlyIfPath !== null &&
            textInput(line.onlyIfEquals, t('mapping.editor.onlyIfEqualsPlaceholder'), (onlyIfEquals) => changeLine(line, { onlyIfEquals }))}
        </div>
      ),
      unimportant: true,
      width: '11rem',
    },
  )

  function solrFieldsCell(line: ResponseMappingLine) {
    const linked = linkedSolrFieldsOf(line)
    const keyReady = isValidKey(line.key)
    return (
      <div className="d-flex flex-column gap-1">
        {linked.length > 0 && (
          <div className="d-flex flex-wrap gap-1">
            {linked.map((solrField) => (
              <span key={solrField.id} className="badge text-bg-secondary d-inline-flex align-items-center gap-1 font-monospace fw-normal">
                {solrField.coreKey ?? '*'}/{solrField.name}
                <button
                  type="button"
                  className="btn btn-link btn-sm p-0 border-0 text-reset lh-1"
                  aria-label={t('mapping.editor.unlinkSolrField')}
                  disabled={disabled}
                  onClick={() => linkSolrField(solrField, null)}
                >
                  <Icon name="close" />
                </button>
              </span>
            ))}
          </div>
        )}
        {keyReady ? (
          <SolrFieldPicker
            label={t('mapping.editor.linkSolrField')}
            language={language}
            solrFields={linkCandidates.filter((solrField) => solrField.command !== line.key)}
            initialCoreKey={targetCoreKey}
            disabled={disabled}
            onPick={(solrField) => linkSolrField(solrField, line.key)}
          />
        ) : (
          <span className="text-body-secondary small">{t('mapping.editor.linkNeedsKey')}</span>
        )}
      </div>
    )
  }

  // ===== Subfetches =====

  /** Die Eingaben eines Subfetch-Eintrags, wie das Blatt sie zeigt: beim Sternzeit-Fetch sternzeit, beim Infosystem die Platzhalter des Aufrufs */
  function shownInputParametersOf(line: ResponseMappingLine, abasInfosys: AbasInfosys | null): ResponseMappingSubfetchInputParameter[] {
    const stored = subfetchInputParametersOf(line)
    const names = line.kind === 'SUBFETCH_STERNZEIT' ? [STERNZEIT_PARAMETER_NAME] : abasInfosys === null ? [] : abasInfosysPlaceholderNamesOf(abasInfosys.requestBodyTemplate)
    return names.map((name) => stored.find((parameter) => parameter.name === name) ?? { name, path: null, value: null })
  }

  function changeInputParameter(line: ResponseMappingLine, abasInfosys: AbasInfosys | null, name: string, change: Partial<ResponseMappingSubfetchInputParameter>) {
    const parameters = shownInputParametersOf(line, abasInfosys).map((parameter) => (parameter.name === name ? { ...parameter, ...change } : parameter))
    changeLine(line, { subfetchInputParametersJson: JSON.stringify(parameters) })
  }

  /** Eine Eingabe im Blatt: Name, Schalter aus JSON oder statisch, Adresse oder Wert, „im Baum wählen“ */
  function inputParameterRow(line: ResponseMappingLine, abasInfosys: AbasInfosys | null, parameter: ResponseMappingSubfetchInputParameter) {
    const fromJson = parameter.value === null
    const picking = subfetchInputPick !== null && subfetchInputPick.lineId === line.id && subfetchInputPick.parameterName === parameter.name
    let sampleValue: string | null = null
    if (sampleJson !== undefined && fromJson && parameter.path !== null) {
      // beim Subfetch je Zeile zeigt die Probe den Wert der ersten Zeile der Tabelle
      const tableLine = tableLineOf(line, value.lines)
      const scope = tableLine === null ? sampleJson : evaluateLine(sampleJson, tableLine.sourcePath, null).values[0]
      const found = scope === undefined || scope === null ? undefined : evaluatePath(scope, parameter.path)[0]
      sampleValue = found === undefined || found === null ? '' : valueText(found)
    }
    return (
      <div key={parameter.name} className="d-flex flex-wrap gap-1 align-items-center">
        <code className="small">{parameter.name}</code>
        <div className="btn-group btn-group-sm" role="group" aria-label={t('mapping.editor.subfetch.sourceOfInput')}>
          <button
            type="button"
            className={fromJson ? 'button-other fw-bold' : 'button-other'}
            disabled={disabled}
            onClick={() => changeInputParameter(line, abasInfosys, parameter.name, { value: null })}
          >
            {t('mapping.editor.subfetch.fromJson')}
          </button>
          <button
            type="button"
            className={fromJson ? 'button-other' : 'button-other fw-bold'}
            disabled={disabled}
            onClick={() => changeInputParameter(line, abasInfosys, parameter.name, { path: null, value: parameter.value ?? '' })}
          >
            {t('mapping.editor.subfetch.static')}
          </button>
        </div>
        {fromJson ? (
          <>
            <input
              className="form-control form-control-sm font-monospace"
              style={{ minWidth: '18rem' }}
              disabled={disabled}
              value={parameter.path ?? ''}
              placeholder={t('mapping.editor.sourcePlaceholder')}
              aria-label={t('mapping.editor.subfetch.inputPath', { name: parameter.name })}
              onChange={(event) => changeInputParameter(line, abasInfosys, parameter.name, { path: event.target.value === '' ? null : event.target.value, value: null })}
            />
            {onPickSubfetchInput !== undefined && (
              <Hoverlay text={picking ? t('mapping.editor.subfetch.pickingHint') : t('mapping.editor.subfetch.pickInTree')}>
                <button
                  type="button"
                  className={picking ? 'button-other fw-bold' : 'button-other'}
                  aria-pressed={picking}
                  disabled={disabled}
                  onClick={() => onPickSubfetchInput(picking ? null : { lineId: line.id, parameterName: parameter.name })}
                >
                  <Icon name="resolve" />
                </button>
              </Hoverlay>
            )}
            {sampleValue !== null && (sampleValue === '' ? <span className="text-danger small">{t('mapping.editor.noValue')}</span> : <span className="badge text-bg-light fw-normal text-break">{sampleValue}</span>)}
          </>
        ) : (
          <input
            className="form-control form-control-sm font-monospace"
            style={{ minWidth: '12rem' }}
            disabled={disabled}
            value={parameter.value ?? ''}
            placeholder={t('mapping.editor.subfetch.staticPlaceholder')}
            aria-label={t('mapping.editor.subfetch.inputValue', { name: parameter.name })}
            onChange={(event) => changeInputParameter(line, abasInfosys, parameter.name, { path: null, value: event.target.value })}
          />
        )}
      </div>
    )
  }

  const subfetchKindLabels: Record<ResponseMappingSubfetchKind, string> = {
    SUBFETCH_STERNZEIT: t('mapping.editor.kindSubfetchSternzeit'),
    SUBFETCH_INFOSYS: t('mapping.editor.kindSubfetchInfosys'),
  }

  const subfetchColumns: TableColumn<ResponseMappingLine>[] = [
    {
      header: t('mapping.editor.columns.key'),
      headerHint: t('mapping.editor.subfetch.keyHint'),
      cell: (line) => (
        <KeyInput id={`${idPrefix}-subfetch-${line.id ?? line.position}-key`} required disabled={disabled} value={line.key} onChange={(key) => changeLine(line, { key })} />
      ),
      width: '13rem',
    },
    {
      header: t('mapping.editor.columns.kind'),
      cell: (line) => (
        <select
          className="form-select form-select-sm"
          disabled={disabled}
          value={line.kind}
          aria-label={t('mapping.editor.columns.kind')}
          onChange={(event) => {
            const kind = event.target.value as ResponseMappingSubfetchKind
            changeLine(line, {
              kind,
              abasInfosysKey: null,
              subfetchInputParametersJson: kind === 'SUBFETCH_STERNZEIT' ? JSON.stringify([{ name: STERNZEIT_PARAMETER_NAME, path: null, value: null }]) : '[]',
            })
          }}
        >
          {(Object.keys(subfetchKindLabels) as ResponseMappingSubfetchKind[]).map((kind) => (
            <option key={kind} value={kind}>
              {subfetchKindLabels[kind]}
            </option>
          ))}
        </select>
      ),
      width: '11rem',
    },
    {
      header: t('mapping.editor.subfetch.perRow'),
      headerHint: t('mapping.editor.subfetch.perRowHint'),
      cell: (line) => (
        <select
          className="form-select form-select-sm"
          disabled={disabled}
          value={line.tableLineKey ?? ''}
          aria-label={t('mapping.editor.subfetch.perRow')}
          onChange={(event) => changeLine(line, { tableLineKey: event.target.value === '' ? null : event.target.value })}
        >
          <option value="">{t('mapping.editor.subfetch.perRowHead')}</option>
          {valueLines
            .filter((candidate) => candidate.kind === 'TABLE' && isValidKey(candidate.key))
            .map((candidate) => (
              <option key={candidate.key} value={candidate.key}>
                {candidate.key}
              </option>
            ))}
        </select>
      ),
      width: '12rem',
    },
    {
      header: t('mapping.editor.subfetch.target'),
      headerHint: t('mapping.editor.subfetch.targetHint'),
      cell: (line) => {
        if (line.kind === 'SUBFETCH_STERNZEIT') {
          return <span className="text-body-secondary small">{t('mapping.editor.subfetch.sternzeitTarget')}</span>
        }
        const abasInfosys = abasInfosysList?.find((candidate) => candidate.key === line.abasInfosysKey) ?? null
        return (
          <div className="d-flex flex-column gap-1">
            <select
              className="form-select form-select-sm"
              required
              disabled={disabled || abasInfosysList === null}
              value={line.abasInfosysKey ?? ''}
              aria-label={t('mapping.editor.subfetch.target')}
              onChange={(event) => changeLine(line, { abasInfosysKey: event.target.value === '' ? null : event.target.value, subfetchInputParametersJson: '[]' })}
            >
              <option value="">{t('mapping.editor.subfetch.chooseInfosys')}</option>
              {(abasInfosysList ?? []).map((candidate) => (
                <option key={candidate.key} value={candidate.key}>
                  {candidate.key}: {translate(candidate.displayName, language) || candidate.key}
                </option>
              ))}
            </select>
            {abasInfosys !== null && (
              <code className="small">
                POST infosys/data/{abasInfosys.infosysGroup}/{abasInfosys.infosysName}
              </code>
            )}
            {abasInfosysList !== null && abasInfosysList.length === 0 && <span className="text-danger small">{t('mapping.editor.subfetch.noInfosysStored')}</span>}
          </div>
        )
      },
      width: '18rem',
    },
    {
      header: t('mapping.editor.subfetch.inputs'),
      headerHint: t('mapping.editor.subfetch.inputsHint'),
      cell: (line) => {
        const abasInfosys = abasInfosysList?.find((candidate) => candidate.key === line.abasInfosysKey) ?? null
        const parameters = shownInputParametersOf(line, abasInfosys)
        if (parameters.length === 0) {
          return <span className="text-body-secondary small">{line.kind === 'SUBFETCH_INFOSYS' && abasInfosys !== null ? t('mapping.editor.subfetch.noInputs') : ''}</span>
        }
        return <div className="d-flex flex-column gap-1">{parameters.map((parameter) => inputParameterRow(line, abasInfosys, parameter))}</div>
      },
    },
  ]

  return (
    <div className="d-flex flex-column gap-3">
      <div className="row g-3">
        <div className="col-md-4">
          <label className="form-label" htmlFor={`${idPrefix}-key`}>
            {t('mapping.editor.name')}
          </label>
          <KeyInput id={`${idPrefix}-key`} required disabled={disabled || keyLocked} value={mapping.key} onChange={(key) => changeMapping({ key })} />
        </div>
        <div className="col-md-4">
          <label className="form-label" htmlFor={`${idPrefix}-target`}>
            {t('mapping.editor.targetKey')}
          </label>
          <KeyInput
            id={`${idPrefix}-target`}
            disabled={disabled}
            value={mapping.targetKey ?? ''}
            onChange={(targetKey) => changeMapping({ targetKey: targetKey === '' ? null : targetKey })}
          />
          <div className="form-text">{t('mapping.editor.targetKeyHint')}</div>
        </div>
        <div className="col-md-4">
          <label className="form-label" htmlFor={`${idPrefix}-type`}>
            {t('mapping.editor.responseObjectType')}
          </label>
          <input
            id={`${idPrefix}-type`}
            className="form-control font-monospace"
            required
            disabled={disabled}
            value={mapping.responseObjectType}
            placeholder={t('mapping.editor.responseObjectTypePlaceholder')}
            onChange={(event) => changeMapping({ responseObjectType: event.target.value })}
          />
          <div className="form-text">
            {t('mapping.editor.fetchWay')}: <code>{mapping.fetchWayJson}</code>
          </div>
        </div>
      </div>

      <div>
        <div className="fw-semibold mb-1">{t('mapping.editor.conditions')}</div>
        <div className="form-text mb-2">{t('mapping.editor.conditionsHint')}</div>
        <div className="d-flex flex-column gap-1">
          {conditions.map((condition, index) => (
            <div key={index} className="d-flex gap-2 align-items-center">
              {textInput(condition.path, t('mapping.editor.conditionPath'), (path) =>
                changeConditions(conditions.map((candidate, candidateIndex) => (candidateIndex === index ? { ...candidate, path } : candidate))),
                true,
              )}
              <span>=</span>
              {textInput(condition.equals, t('mapping.editor.conditionEquals'), (equals) =>
                changeConditions(conditions.map((candidate, candidateIndex) => (candidateIndex === index ? { ...candidate, equals } : candidate))),
              )}
              {removeButton(t('mapping.editor.removeCondition'), () => changeConditions(conditions.filter((_, candidateIndex) => candidateIndex !== index)))}
            </div>
          ))}
          <div>
            <NewEntityButton entity="ResponseMappingMatchCondition" disabled={disabled} onClick={() => changeConditions([...conditions, { path: '', equals: '' }])} />
          </div>
        </div>
      </div>

      <div>
        <div className="fw-semibold mb-2">{t('mapping.editor.lines')}</div>
        <Table
          columns={columns}
          rows={valueLines}
          rowKey={(line) => String(line.id ?? line.position)}
          onReorder={disabled ? undefined : reorderLines}
          detailTitle={(line) => line.key || line.sourcePath}
          emptyText={t('mapping.editor.linesEmpty')}
          actionsWidth={onMakeCondition ? '6rem' : '3.5rem'}
          actions={(line) => (
            <>
              {onMakeCondition && line.kind === 'VALUE' && (
                <Hoverlay text={t('mapping.editor.makeCondition')}>
                  <button type="button" className="button-other" aria-label={t('mapping.editor.makeCondition')} disabled={disabled} onClick={() => onMakeCondition(line)}>
                    <Icon name="filter" />
                  </button>
                </Hoverlay>
              )}
              {removeButton(t('mapping.editor.removeLine'), () => changeLines(valueLines.filter((candidate) => candidate !== line)))}
            </>
          )}
        />
        <div className="mt-2">
          <NewEntityButton entity="ResponseMappingLine" disabled={disabled} onClick={addValueLine} />
        </div>
      </div>

      <div>
        <div className="fw-semibold mb-1">{t('mapping.editor.subfetch.title')}</div>
        <div className="form-text mb-2">{t('mapping.editor.subfetch.hint')}</div>
        <Table
          columns={subfetchColumns}
          rows={subfetchLines}
          rowKey={(line) => String(line.id ?? line.position)}
          detailTitle={(line) => line.key || line.kind}
          emptyText={t('mapping.editor.subfetch.empty')}
          actionsWidth={onFetchSubfetch ? '6rem' : '3.5rem'}
          actions={(line) => (
            <>
              {onFetchSubfetch && (
                <Hoverlay text={t('mapping.editor.fetchSubfetch')}>
                  <button type="button" className="button-other" aria-label={t('mapping.editor.fetchSubfetch')} disabled={disabled} onClick={() => onFetchSubfetch(line)}>
                    <Icon name="abasManager" />
                  </button>
                </Hoverlay>
              )}
              {removeButton(t('mapping.editor.removeLine'), () => changeLines(valueLines, subfetchLines.filter((candidate) => candidate !== line)))}
            </>
          )}
        />
        <div className="mt-2">
          <NewEntityButton entity="ResponseMappingLine (Subfetch)" disabled={disabled} onClick={addSubfetchLine} />
        </div>
      </div>

      <div>
        <div className="fw-semibold mb-1">{t('mapping.editor.ignored')}</div>
        {ignoredPaths.length === 0 ? (
          <span className="text-body-secondary">{t('mapping.editor.noIgnored')}</span>
        ) : (
          <div className="d-flex flex-wrap gap-1">
            {ignoredPaths.map((path) => (
              <span key={path} className="badge text-bg-secondary d-inline-flex align-items-center gap-1 font-monospace fw-normal">
                {path}
                <button
                  type="button"
                  className="btn btn-link btn-sm p-0 border-0 text-reset lh-1"
                  aria-label={t('mapping.editor.removeIgnored')}
                  disabled={disabled}
                  onClick={() => changeMapping({ ignoredPathsJson: JSON.stringify(ignoredPaths.filter((candidate) => candidate !== path)) })}
                >
                  <Icon name="close" />
                </button>
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
