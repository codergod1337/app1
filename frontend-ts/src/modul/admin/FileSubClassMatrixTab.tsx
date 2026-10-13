import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { Matrix } from '../../components/Matrix.tsx'
import { MatrixBrushes, type MatrixBrush } from '../../components/MatrixBrushes.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { changesByRow, useMatrixCellEdits } from '../../components/useMatrixCellEdits.ts'
import type { FileExtension } from '../fileextension/FileExtension.ts'
import { fileExtensionBlocks, type FileExtensionBlock } from '../fileextension/fileExtensionBlocks.ts'
import { FileShape } from '../fileextension/FileShape.tsx'
import {
  changeFileSubClassDisplayContextKeys,
  changeFileSubClassExtensions,
  isFileSubClassShownIn,
  type FileSubClass,
  type FileSubClassDisplayContextKeys,
  type FileSubClassExtensions,
} from '../filesubclass/FileSubClass.ts'
import type { FileSubClassDestination } from '../filesubclass/FileSubClassDestination.ts'
import { FileSubClassSymbol } from '../filesubclass/FileSubClassSymbol.tsx'

/** Eine Zeile der Matrix: die Kopfzeile einer Endungsgruppe, eine Endung, der Trennstrich oder ein Anzeigeort */
type FileSubClassMatrixRow =
  | { kind: 'group'; block: FileExtensionBlock }
  | { kind: 'extension'; fileExtension: FileExtension }
  | { kind: 'separator' }
  | { kind: 'destination'; destination: FileSubClassDestination }

/** Nimmt die Dateiart laut Backend diese Endung an? */
function serverEnabled(fileSubClass: FileSubClass | undefined, extension: string): boolean {
  return (fileSubClass?.extensions ?? []).includes(extension)
}

/** Steht die Dateiart laut Backend an diesem Anzeigeort? */
function serverShownIn(fileSubClass: FileSubClass | undefined, destinationKey: string): boolean {
  return fileSubClass !== undefined && isFileSubClassShownIn(fileSubClass, destinationKey)
}

function fileSubClassByKey(fileSubClasses: FileSubClass[] | null, key: string): FileSubClass | undefined {
  return fileSubClasses?.find((fileSubClass) => fileSubClass.key === key)
}

/**
 * Tab „FSC-Matrix“ der Administration (User 2026-10-13, vorher „FileMatrix“ nur mit den Endungen): oben die
 * Dateiarten, links erst die Endungen nach Gruppen, dann ein dicker Trennstrich, dann die Anzeigeorte. In der Ecke
 * der Pinsel: setzen (×) oder entfernen (·). Eine Endung freischalten heißt, die Dateiart nimmt sie beim Upload an;
 * einen Anzeigeort zuweisen heißt, die Dateiart steht dort als Abschnitt (displayContextKeys), z. B. im Dokumente-Tab
 * der Artikelmappe.
 *
 * Malen: linke Maustaste gedrückt halten und über die Zellen fahren, jede Endung und jeder Anzeigeort bekommt den
 * Wert des Pinsels, Gruppenzeilen und der Trennstrich bleiben unberührt. Klick: die Zelle bekommt den Wert des
 * Pinsels, hat sie ihn schon, schaltet sie um. In der Zeile einer Gruppe zählt die Zelle, wie viele ihrer Endungen frei
 * sind, ein Klick schaltet alle frei oder, wenn schon alle frei sind, alle ab. Endungen und Anzeigeorte werden
 * getrennt gespeichert (zwei Endpunkte, je Dateiart die ganze Liste), Schraffur bis zur Bestätigung und Toast
 * übernimmt useMatrixCellEdits. Den Katalog der Endungen pflegt der Tab „Endungen“, die Orte der Tab „Anzeigeorte“.
 */
export function FileSubClassMatrixTab() {
  const { t } = useTranslation()
  const {
    fileSubClasses,
    fileSubClassDestinations,
    fileExtensions,
    fileExtensionCollections,
    errorMessage,
    reloadMasterData,
  } = useMasterData()
  const [brush, setBrush] = useState(true)
  // Die Antwort des letzten Speicherns, bis die neu geladenen Stammdaten da sind
  const [saved, setSaved] = useState<{ basis: FileSubClass[] | null; result: FileSubClass[] } | null>(null)

  const serverFileSubClasses = saved !== null && saved.basis === fileSubClasses ? saved.result : fileSubClasses

  // Für die Handler, die nach dem Rendern laufen: die neuesten Stände
  const serverRef = useRef(serverFileSubClasses)
  const fileSubClassesRef = useRef(fileSubClasses)
  const fileExtensionsRef = useRef(fileExtensions)
  const destinationsRef = useRef(fileSubClassDestinations)
  useEffect(() => {
    serverRef.current = serverFileSubClasses
    fileSubClassesRef.current = fileSubClasses
    fileExtensionsRef.current = fileExtensions
    destinationsRef.current = fileSubClassDestinations
  })

  const brushes: MatrixBrush<boolean>[] = [
    { value: false, label: '·', hint: t('fscmatrix.brushOff') },
    { value: true, label: '×', hint: t('fscmatrix.brushOn') },
  ]

  /** Die Antwort eines Speicherns wird der neue Stand, bis die Stammdaten nachgeladen sind */
  function takeSaved(savedFileSubClasses: FileSubClass[]) {
    serverRef.current = savedFileSubClasses
    setSaved({ basis: fileSubClassesRef.current, result: savedFileSubClasses })
    // Endungen und Anzeigeorte stehen auch in den Stammdaten, z. B. für den FSC-Tab und die Artikelmappe
    reloadMasterData()
  }

  // Gespeichert wird je Dateiart: Für die Hooks ist deshalb die Dateiart die Zeile, Endung oder Anzeigeort die Spalte
  const extensionEdits = useMatrixCellEdits<boolean>({
    serverValue: (fileSubClassKey, extension) =>
      serverEnabled(fileSubClassByKey(serverRef.current, fileSubClassKey), extension),
    // je geänderte Dateiart die vollständige neue Liste, in der Reihenfolge des Katalogs
    save: (changes) => {
      const changedRows = changesByRow(changes)
      const changedExtensions: FileSubClassExtensions[] = []
      for (const fileSubClass of serverRef.current ?? []) {
        const rowChanges = changedRows.get(fileSubClass.key)
        if (rowChanges === undefined) {
          continue
        }
        const extensions = (fileExtensionsRef.current ?? [])
          .filter(({ extension }) =>
            rowChanges.has(extension) ? rowChanges.get(extension) : serverEnabled(fileSubClass, extension),
          )
          .map(({ extension }) => extension)
        changedExtensions.push({ key: fileSubClass.key, extensions })
      }
      return changeFileSubClassExtensions(changedExtensions).then(({ data }) => {
        takeSaved(data)
        return (fileSubClassKey: string, extension: string) =>
          serverEnabled(fileSubClassByKey(data, fileSubClassKey), extension)
      })
    },
    savedToast: (cellCount, rowCount) => ({
      kind: 'success',
      title: t('fscmatrix.extensionsSavedTitle'),
      text: t('fscmatrix.savedText', {
        cells: t('fscmatrix.extensionCount', { count: cellCount }),
        fileSubClasses: t('fscmatrix.fileSubClassCount', { count: rowCount }),
      }),
    }),
  })

  const destinationEdits = useMatrixCellEdits<boolean>({
    serverValue: (fileSubClassKey, destinationKey) =>
      serverShownIn(fileSubClassByKey(serverRef.current, fileSubClassKey), destinationKey),
    // je geänderte Dateiart die vollständige neue Liste, in der Reihenfolge der Anzeigeorte
    save: (changes) => {
      const changedRows = changesByRow(changes)
      const changedDisplayContextKeys: FileSubClassDisplayContextKeys[] = []
      for (const fileSubClass of serverRef.current ?? []) {
        const rowChanges = changedRows.get(fileSubClass.key)
        if (rowChanges === undefined) {
          continue
        }
        const displayContextKeys = (destinationsRef.current ?? [])
          .filter(({ key }) => (rowChanges.has(key) ? rowChanges.get(key) : serverShownIn(fileSubClass, key)))
          .map(({ key }) => key)
        changedDisplayContextKeys.push({ key: fileSubClass.key, displayContextKeys })
      }
      return changeFileSubClassDisplayContextKeys(changedDisplayContextKeys).then(({ data }) => {
        takeSaved(data)
        return (fileSubClassKey: string, destinationKey: string) =>
          serverShownIn(fileSubClassByKey(data, fileSubClassKey), destinationKey)
      })
    },
    savedToast: (cellCount, rowCount) => ({
      kind: 'success',
      title: t('fscmatrix.destinationsSavedTitle'),
      text: t('fscmatrix.savedText', {
        cells: t('fscmatrix.destinationCount', { count: cellCount }),
        fileSubClasses: t('fscmatrix.fileSubClassCount', { count: rowCount }),
      }),
    }),
  })

  const rows: FileSubClassMatrixRow[] = []
  if (fileExtensions !== null && fileExtensionCollections !== null && fileSubClassDestinations !== null) {
    for (const block of fileExtensionBlocks(fileExtensionCollections, fileExtensions)) {
      rows.push({ kind: 'group', block })
      for (const fileExtension of block.fileExtensions) {
        rows.push({ kind: 'extension', fileExtension })
      }
    }
    rows.push({ kind: 'separator' })
    for (const destination of fileSubClassDestinations) {
      rows.push({ kind: 'destination', destination })
    }
  }

  /** Der Wert zum Zeichnen: noch nicht bestätigt, sonst der Stand des Backends */
  function shownEnabled(fileSubClass: FileSubClass, extension: string): boolean {
    const change = extensionEdits.pendingChange(fileSubClass.key, extension)
    return change !== undefined ? change.value : serverEnabled(fileSubClass, extension)
  }

  /** Der Wert zum Zeichnen: noch nicht bestätigt, sonst der Stand des Backends */
  function shownInDestination(fileSubClass: FileSubClass, destinationKey: string): boolean {
    const change = destinationEdits.pendingChange(fileSubClass.key, destinationKey)
    return change !== undefined ? change.value : serverShownIn(fileSubClass, destinationKey)
  }

  function isMarked(row: FileSubClassMatrixRow, fileSubClass: FileSubClass): boolean {
    if (row.kind === 'extension') {
      return shownEnabled(fileSubClass, row.fileExtension.extension)
    }
    if (row.kind === 'destination') {
      return shownInDestination(fileSubClass, row.destination.key)
    }
    return false
  }

  function isPending(row: FileSubClassMatrixRow, fileSubClass: FileSubClass): boolean {
    if (row.kind === 'extension') {
      return extensionEdits.pendingChange(fileSubClass.key, row.fileExtension.extension) !== undefined
    }
    if (row.kind === 'group') {
      return row.block.fileExtensions.some(
        ({ extension }) => extensionEdits.pendingChange(fileSubClass.key, extension) !== undefined,
      )
    }
    if (row.kind === 'destination') {
      return destinationEdits.pendingChange(fileSubClass.key, row.destination.key) !== undefined
    }
    return false
  }

  function clickCell(row: FileSubClassMatrixRow, fileSubClass: FileSubClass) {
    if (row.kind === 'extension') {
      const extension = row.fileExtension.extension
      const current = extensionEdits.currentValue(fileSubClass.key, extension)
      extensionEdits.changeCell(fileSubClass.key, extension, current === brush ? !current : brush)
      return
    }
    if (row.kind === 'destination') {
      const destinationKey = row.destination.key
      const current = destinationEdits.currentValue(fileSubClass.key, destinationKey)
      destinationEdits.changeCell(fileSubClass.key, destinationKey, current === brush ? !current : brush)
      return
    }
    if (row.kind !== 'group') {
      return
    }
    // Gruppe: alle frei, dann alle ab, sonst alle frei
    const blockExtensions = row.block.fileExtensions.map(({ extension }) => extension)
    if (blockExtensions.length === 0) {
      return
    }
    const allEnabled = blockExtensions.every((extension) => extensionEdits.currentValue(fileSubClass.key, extension))
    extensionEdits.changeCells(
      blockExtensions.map((extension) => ({ rowKey: fileSubClass.key, columnKey: extension, value: !allEnabled })),
    )
  }

  /** Gemalt wird nur auf Endungen und Anzeigeorten, Gruppenzeilen und Trennstrich bleiben unberührt */
  function paintCell(row: FileSubClassMatrixRow, fileSubClass: FileSubClass) {
    if (row.kind === 'extension') {
      extensionEdits.paintCell(fileSubClass.key, row.fileExtension.extension, brush)
    } else if (row.kind === 'destination') {
      destinationEdits.paintCell(fileSubClass.key, row.destination.key, brush)
    }
  }

  /** Ein Strich kann über beide Blöcke gehen: jeder Hook speichert, was bei ihm offen ist */
  function endPaint() {
    extensionEdits.endPaint()
    destinationEdits.endPaint()
  }

  function rowHeader(row: FileSubClassMatrixRow) {
    if (row.kind === 'group') {
      return (
        <span className="matrix-row-head-content">
          <FileShape shape={row.block.fileExtensionCollection?.symbolShape ?? null} />
          {row.block.fileExtensionCollection
            ? translate(row.block.fileExtensionCollection.displayName)
            : t('fscmatrix.withoutGroup')}
        </span>
      )
    }
    if (row.kind === 'extension') {
      return (
        <span className="matrix-row-head-content">
          <Hoverlay text={translate(row.fileExtension.description)}>
            <code>.{row.fileExtension.extension}</code>
          </Hoverlay>
          {row.fileExtension.dangerous && (
            <Hoverlay text={t('fscmatrix.dangerousHint')}>
              <span className="text-danger">
                <Icon name="warning" />
              </span>
            </Hoverlay>
          )}
        </span>
      )
    }
    if (row.kind === 'separator') {
      return (
        <span className="matrix-row-head-content">
          <Icon name="fileSubClassDestinations" />
          {t('fscmatrix.destinations')}
        </span>
      )
    }
    return (
      <span className="matrix-row-head-content">
        <Hoverlay text={translate(row.destination.description)}>
          <span>{translate(row.destination.displayName) || row.destination.key}</span>
        </Hoverlay>
      </span>
    )
  }

  function cellLabel(row: FileSubClassMatrixRow, fileSubClass: FileSubClass): string {
    const pending = isPending(row, fileSubClass) ? t('fscmatrix.pending') : ''
    if (row.kind === 'group') {
      return t('fscmatrix.cellGroup', { key: fileSubClass.key, pending })
    }
    if (row.kind === 'extension') {
      const state = shownEnabled(fileSubClass, row.fileExtension.extension)
        ? t('fscmatrix.extensionOn')
        : t('fscmatrix.extensionOff')
      return t('fscmatrix.cellExtension', { extension: row.fileExtension.extension, key: fileSubClass.key, state, pending })
    }
    if (row.kind === 'destination') {
      const state = shownInDestination(fileSubClass, row.destination.key)
        ? t('fscmatrix.destinationOn')
        : t('fscmatrix.destinationOff')
      return t('fscmatrix.cellDestination', {
        destination: translate(row.destination.displayName) || row.destination.key,
        key: fileSubClass.key,
        state,
        pending,
      })
    }
    return t('fscmatrix.destinations')
  }

  const shownErrorMessage = errorMessage ?? extensionEdits.saveErrorMessage ?? destinationEdits.saveErrorMessage
  const loading =
    serverFileSubClasses === null ||
    fileExtensions === null ||
    fileExtensionCollections === null ||
    fileSubClassDestinations === null

  return (
    <>
      {/* Nur bei Fehler oder während des Ladens, sonst steht die Matrix allein */}
      {(shownErrorMessage !== null || loading) && (
        <div className="matrix-message">
          {shownErrorMessage !== null ? (
            <span className="text-danger">{shownErrorMessage}</span>
          ) : (
            <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
          )}
        </div>
      )}
      {!loading && (
        <Matrix
          rows={rows}
          columns={serverFileSubClasses}
          rowKey={(row) => {
            if (row.kind === 'group') {
              return `group-${row.block.fileExtensionCollection?.key ?? 'none'}`
            }
            if (row.kind === 'extension') {
              return `extension-${row.fileExtension.extension}`
            }
            if (row.kind === 'separator') {
              return 'separator-destinations'
            }
            return `destination-${row.destination.key}`
          }}
          columnKey={(fileSubClass) => fileSubClass.key}
          rowsLabel={t('fscmatrix.rowsLabel')}
          columnsLabel={t('fscmatrix.columnsLabel')}
          cornerContent={<MatrixBrushes brushes={brushes} value={brush} onChange={setBrush} />}
          rowClassName={(row) => (row.kind === 'group' ? 'matrix-group-row' : undefined)}
          isSeparatorRow={(row) => row.kind === 'separator'}
          rowHeader={rowHeader}
          columnHeader={(fileSubClass) => (
            <span className="matrix-file-sub-class">
              <FileSubClassSymbol fileSubClass={fileSubClass} />
              <span>{translate(fileSubClass.displayName) || fileSubClass.key}</span>
            </span>
          )}
          isMarked={isMarked}
          cellContent={(row, fileSubClass) => {
            if (row.kind !== 'group') {
              return undefined
            }
            const count = row.block.fileExtensions.filter(({ extension }) => shownEnabled(fileSubClass, extension)).length
            const total = row.block.fileExtensions.length
            return (
              <span className={total > 0 && count === total ? 'matrix-count matrix-count-full' : 'matrix-count'}>
                {count}/{total}
              </span>
            )
          }}
          cellClassName={(row, fileSubClass) => (isPending(row, fileSubClass) ? 'matrix-cell-pending' : undefined)}
          cellLabel={cellLabel}
          onCellClick={clickCell}
          onCellPaint={paintCell}
          onPaintEnd={endPaint}
        />
      )}
    </>
  )
}
