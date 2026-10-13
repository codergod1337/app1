import { type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { SortableList } from '../../components/SortableList.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { showToast } from '../../components/toastStore.ts'
import { useUsersSettings } from '../../components/usersSettingsContext.ts'
import { ADMIN_ACCESS_ROLE_KEY } from '../accessrole/AccessRole.ts'
import { type FileSubClass, fileSubClassNameOf, isFileSubClassShownIn, isFileSubClassWritableBy } from '../filesubclass/FileSubClass.ts'
import { FileSubClassSymbol } from '../filesubclass/FileSubClassSymbol.tsx'
import { useSession } from '../login/sessionContext.ts'
import { SolrHookValues } from '../solrmanager/SolrHookValues.tsx'
import {
  DMS_FILE_SUB_CLASS_COLLAPSED_SETTING_KEY,
  DMS_FILE_SUB_CLASS_ORDER_SETTING_KEY,
  type DmsDocumentListMode,
  dmsFieldLabel,
  dmsSolrDocumentPath,
  FILE_SUB_CLASS_FIELD_NAME,
  fileSubClassKeysOfSetting,
} from './Dms.ts'
import { readableBytes } from '../../components/readableBytes.ts'
import { DmsSolrDocumentActionButtons } from './DmsSolrDocumentActionButtons.tsx'
import type { DmsSolrDocument } from './DmsSolrDocumentFacts.tsx'

interface DmsFileSubClassDocumentsListProps {
  /** die geladenen Dokumente; der Ort lädt sie über seine Hooks */
  documents: readonly DmsSolrDocument[]
  /** der Anzeigeort, z. B. ARTIKELMAPPE: jede Dateiart mit diesem Key im Katalog ist ein Abschnitt, auch ohne Dateien */
  displayContextKey: string
  mode: DmsDocumentListMode
  /** der Weg zur Upload-Maske mit dieser Dateiart; der Knopf erscheint nur mit Schreibrecht an ihr */
  onNewFile?: (fileSubClassKey: string) => void
  /** die Herkunft für jeden Weg aus der Liste */
  origin: OriginState
}

/** Ein Abschnitt: die Dateiart und ihre Dokumente, jüngste Änderung zuerst */
interface FileSubClassSection {
  key: string
  fileSubClass: FileSubClass | null
  documents: DmsSolrDocument[]
}

/** Eine Zeile: das Dokument und seine laufende Nummer über alle Abschnitte */
interface DocumentRow {
  number: number
  document: DmsSolrDocument
}

/** Eine Liste, egal ob Solr einen Wert oder mehrere liefert */
function valuesOf(value: unknown): string[] {
  if (value === null || value === undefined) {
    return []
  }
  return (Array.isArray(value) ? value : [value]).map((entry) => String(entry))
}

function textOf(document: DmsSolrDocument, fieldName: string): string {
  const value = document[fieldName]
  return typeof value === 'string' ? value : typeof value === 'number' ? String(value) : ''
}

/**
 * Die kontextgebundene Dokumentenliste je Dateiart, nach DmsFscDokumente der Vorlage, für die Artikelmappe und jeden
 * weiteren Ort: je Dateiart ein Abschnitt mit Kopf (Symbol, Name, Position, Anzahl, Hochladen nur mit Schreibrecht)
 * und der zentralen Table darunter, feste Spaltenbreiten, damit die Abschnitte fluchten, eine laufende Nummer über
 * alle Abschnitte. Welche Dateiarten hier Abschnitte sind, sagt der Katalog über die Anzeigeorte der Dateiart (User
 * 2026-10-10), kein Code zählt sie auf; eine Dateiart mit Dateien steht immer da. Die drei Ansichten: ALL in
 * Katalogreihenfolge; WITH_FILES nur mit Dateien, in der eigenen Reihenfolge, am Griff ziehbar; FAVORITES alle, in der
 * eigenen Reihenfolge, ziehbar und je Abschnitt zuklappbar. Jedes Ziehen und jedes Klappen speichert sofort.
 */
export function DmsFileSubClassDocumentsList({ documents, displayContextKey, mode, onNewFile, origin }: DmsFileSubClassDocumentsListProps) {
  const { t, i18n } = useTranslation()
  const { language } = useLanguage()
  const navigate = useNavigate()
  const { hasAccessRole } = useSession()
  const { fileSubClasses, solrHooks, solrFields } = useMasterData()
  const { settingValue, changeSetting } = useUsersSettings()
  const labelOf = (name: string, fallback: string) => dmsFieldLabel(solrFields, name, fallback, language)

  const draggable = mode !== 'ALL'
  const collapsible = mode === 'FAVORITES'
  const savedOrder = fileSubClassKeysOfSetting(settingValue(DMS_FILE_SUB_CLASS_ORDER_SETTING_KEY))
  const collapsed = new Set(collapsible ? (fileSubClassKeysOfSetting(settingValue(DMS_FILE_SUB_CLASS_COLLAPSED_SETTING_KEY)) ?? []) : [])

  // die Abschnitte: jede Dateiart mit Dateien, dazu jede des Orts aus dem Katalog; Katalogreihenfolge, Unbekanntes hinten
  const keys = new Set<string>()
  for (const document of documents) {
    const key = textOf(document, FILE_SUB_CLASS_FIELD_NAME)
    if (key !== '') {
      keys.add(key)
    }
  }
  for (const fileSubClass of fileSubClasses ?? []) {
    if (isFileSubClassShownIn(fileSubClass, displayContextKey)) {
      keys.add(fileSubClass.key)
    }
  }
  let sections: FileSubClassSection[] = [...keys]
    .map((key) => ({
      key,
      fileSubClass: fileSubClasses?.find((candidate) => candidate.key === key) ?? null,
      documents: documents
        .filter((document) => textOf(document, FILE_SUB_CLASS_FIELD_NAME) === key)
        .sort((a, b) => textOf(b, 'lastChangedAt').localeCompare(textOf(a, 'lastChangedAt')) || textOf(a, 'id').localeCompare(textOf(b, 'id'))),
    }))
    .sort(
      (a, b) =>
        (a.fileSubClass?.listingPosition ?? Number.MAX_SAFE_INTEGER) - (b.fileSubClass?.listingPosition ?? Number.MAX_SAFE_INTEGER) || a.key.localeCompare(b.key),
    )
  if (draggable && savedOrder !== null) {
    // die eigene Reihenfolge vor der des Katalogs; was der User nie gezogen hat, bleibt dahinter in Katalogreihenfolge
    const rank = new Map(savedOrder.map((key, index) => [key, index]))
    sections = [...sections].sort((a, b) => (rank.get(a.key) ?? Number.MAX_SAFE_INTEGER) - (rank.get(b.key) ?? Number.MAX_SAFE_INTEGER))
  }
  if (mode === 'WITH_FILES') {
    sections = sections.filter((section) => section.documents.length > 0)
  }

  // die laufende Nummer über alle Abschnitte, in der gezeigten Reihenfolge
  const numberById = new Map<string, number>()
  let number = 0
  for (const section of sections) {
    for (const document of section.documents) {
      number += 1
      numberById.set(textOf(document, 'id'), number)
    }
  }

  function saveOrder(reordered: FileSubClassSection[]) {
    // was dieser Ort nicht kennt, bleibt in seiner alten Reihenfolge hinten dran; die Liste ist projektweit eine
    const newKeys = reordered.map((section) => section.key)
    const remaining = (savedOrder ?? []).filter((key) => !newKeys.includes(key))
    changeSetting(DMS_FILE_SUB_CLASS_ORDER_SETTING_KEY, JSON.stringify([...newKeys, ...remaining])).catch((error: unknown) =>
      showToast({ kind: 'error', title: t('dms.fileSubClassDocuments.orderNotSaved'), text: error instanceof Error ? error.message : undefined }),
    )
  }

  function toggleCollapsed(key: string) {
    const next = new Set(collapsed)
    if (next.has(key)) {
      next.delete(key)
    } else {
      next.add(key)
    }
    changeSetting(DMS_FILE_SUB_CLASS_COLLAPSED_SETTING_KEY, JSON.stringify([...next])).catch((error: unknown) =>
      showToast({ kind: 'error', title: t('dms.fileSubClassDocuments.collapseNotSaved'), text: error instanceof Error ? error.message : undefined }),
    )
  }

  const columns: TableColumn<DocumentRow>[] = [
    { header: '#', cell: (row) => <span className="text-muted small">{row.number}</span>, unimportant: true, width: '3rem' },
    {
      header: labelOf('fileName', t('dms.document.fileName')),
      cell: (row) => {
        const document = row.document
        const id = textOf(document, 'id')
        const fileName = textOf(document, 'fileName') || id
        const index = textOf(document, 'fileIndex')
        const description = translate(typeof document.fileDescription === 'string' ? document.fileDescription : null, language)
        return (
          <span className="d-flex flex-column">
            <span className="d-inline-flex align-items-baseline gap-2 flex-wrap">
              <button type="button" className="btn btn-link btn-sm p-0 border-0 text-start text-break" onClick={() => navigate(dmsSolrDocumentPath(id), { state: origin })}>
                {fileName}
              </button>
              {index !== '' && (
                <span className="text-muted small">
                  {labelOf('fileIndex', t('dms.document.fileIndex'))}: {index}
                </span>
              )}
            </span>
            {description !== '' && <span className="text-muted small text-truncate">{description}</span>}
          </span>
        )
      },
    },
    {
      header: labelOf('version', t('dms.document.version')),
      headerHint: t('dms.fileSubClassDocuments.versionHint'),
      cell: (row) => {
        const version = row.document.version
        return typeof version === 'number' && version > 1 ? version : ''
      },
      unimportant: true,
      width: '4rem',
    },
    {
      header: t('dms.fileSubClassDocuments.columns.links'),
      headerHint: t('dms.fileSubClassDocuments.linksHint'),
      cell: (row) => {
        const numbers = valuesOf(row.document.artikelNummer)
        if (numbers.length === 0) {
          return <span className="text-muted">—</span>
        }
        return (
          <Hoverlay text={numbers.join(', ')}>
            <span className="badge text-bg-secondary">{numbers.length}</span>
          </Hoverlay>
        )
      },
      unimportant: true,
      width: '5rem',
    },
    {
      header: t('dms.fileSubClassDocuments.columns.hooks'),
      headerHint: t('dms.fileSubClassDocuments.hooksHint'),
      cell: (row) => {
        const hooksWithValues = (solrHooks ?? []).filter((hook) => valuesOf(row.document[hook.key]).length > 0)
        if (hooksWithValues.length === 0) {
          return <span className="text-muted">—</span>
        }
        const hint: ReactNode = (
          <span className="d-flex flex-column gap-1">
            {hooksWithValues.map((hook) => (
              <SolrHookValues key={hook.key} solrHook={hook} values={valuesOf(row.document[hook.key])} language={language} origin={origin} />
            ))}
          </span>
        )
        // interaktiv: die Werte sind Badges mit dem Ziel ihres Hooks, die Maus muss sie erreichen und klicken können
        return (
          <Hoverlay text={hint} interactive>
            <span className="badge text-bg-secondary">{hooksWithValues.length}</span>
          </Hoverlay>
        )
      },
      unimportant: true,
      width: '5rem',
    },
    {
      header: labelOf('lastChangedAt', t('dms.document.lastChangedAt')),
      cell: (row) => {
        const changed = textOf(row.document, 'lastChangedAt')
        if (changed === '') {
          return ''
        }
        const date = new Date(changed)
        return Number.isNaN(date.getTime()) ? changed : date.toLocaleDateString(i18n.language)
      },
      unimportant: true,
      width: '7rem',
    },
    { header: labelOf('fileSizeBytes', t('dms.document.fileSize')), cell: (row) => readableBytes(row.document.fileSizeBytes), unimportant: true, width: '6rem' },
  ]

  function renderSection(section: FileSubClassSection, dragHandle: ReactNode | null) {
    const name = section.fileSubClass ? fileSubClassNameOf(section.fileSubClass, language) : section.key
    const isCollapsed = collapsible && collapsed.has(section.key)
    const writable = section.fileSubClass !== null && (hasAccessRole(ADMIN_ACCESS_ROLE_KEY) || isFileSubClassWritableBy(section.fileSubClass, hasAccessRole))
    const rows: DocumentRow[] = section.documents.map((document) => ({ number: numberById.get(textOf(document, 'id')) ?? 0, document }))
    return (
      <div className="d-flex flex-column gap-2">
        <div className="d-flex align-items-center gap-2 flex-wrap">
          {dragHandle}
          {collapsible && (
            <Hoverlay text={isCollapsed ? t('dms.fileSubClassDocuments.expand', { name }) : t('dms.fileSubClassDocuments.collapse', { name })}>
              <button
                type="button"
                className="btn btn-link btn-sm p-0 border-0 lh-1"
                aria-expanded={!isCollapsed}
                aria-label={isCollapsed ? t('dms.fileSubClassDocuments.expand', { name }) : t('dms.fileSubClassDocuments.collapse', { name })}
                onClick={() => toggleCollapsed(section.key)}
              >
                <Icon name={isCollapsed ? 'collapsed' : 'expand'} />
              </button>
            </Hoverlay>
          )}
          {section.fileSubClass && <FileSubClassSymbol fileSubClass={section.fileSubClass} />}
          <span className="fw-semibold">{name}</span>
          {section.fileSubClass && <span className="text-muted small">#{section.fileSubClass.listingPosition}</span>}
          <span className="badge text-bg-light fw-normal">{t('dms.fileSubClassDocuments.filesCount', { count: section.documents.length })}</span>
          {isCollapsed && section.documents.length > 0 && (
            <span className="badge text-bg-secondary">{t('dms.fileSubClassDocuments.hiddenCount', { count: section.documents.length })}</span>
          )}
          {onNewFile !== undefined && writable && (
            <span className="ms-auto">
              <NewEntityButton entity={name} onClick={() => onNewFile(section.key)} />
            </span>
          )}
        </div>
        {!isCollapsed && (
          <Table
            columns={columns}
            rows={rows}
            rowKey={(row) => textOf(row.document, 'id')}
            detailTitle={(row) => textOf(row.document, 'fileName') || textOf(row.document, 'id')}
            emptyText={t('dms.fileSubClassDocuments.noFiles')}
            actionsWidth="13rem"
            actions={(row) => (
              <DmsSolrDocumentActionButtons
                document={row.document}
                origin={origin}
                // das Schreibrecht aus writeAcl, wenn der User das Feld lesen darf; sonst nur Ansehen, Details, Download
                writeAclMatchForCurrentUser={hasAccessRole(ADMIN_ACCESS_ROLE_KEY) || valuesOf(row.document.writeAcl).some(hasAccessRole)}
              />
            )}
          />
        )}
      </div>
    )
  }

  if (sections.length === 0) {
    return <p className="mb-0 text-muted">{t('dms.fileSubClassDocuments.noSections')}</p>
  }

  if (draggable) {
    return <SortableList items={sections} itemKey={(section) => section.key} layout="vertical" onReorder={saveOrder} renderItem={renderSection} />
  }

  return (
    <div className="d-flex flex-column gap-4">
      {sections.map((section) => (
        <div key={section.key}>{renderSection(section, null)}</div>
      ))}
    </div>
  )
}
