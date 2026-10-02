import { useEffect, useRef, useState } from 'react'
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
  changeFileSubClassExtensions,
  type FileSubClass,
  type FileSubClassExtensions,
} from '../filesubclass/FileSubClass.ts'
import { FileSubClassSymbol } from '../filesubclass/FileSubClassSymbol.tsx'

/** Eine Zeile der Matrix: die Kopfzeile einer Gruppe oder eine Endung */
type FileMatrixRow = { kind: 'group'; block: FileExtensionBlock } | { kind: 'extension'; fileExtension: FileExtension }

/** Die Pinsel oben in der Ecke */
const BRUSHES: MatrixBrush<boolean>[] = [
  { value: false, label: '·', hint: 'Pinsel: nicht freischalten' },
  { value: true, label: '×', hint: 'Pinsel: freischalten' },
]

/** Nimmt die Dateiart laut Backend diese Endung an? */
function serverEnabled(fileSubClass: FileSubClass | undefined, extension: string): boolean {
  return (fileSubClass?.extensions ?? []).includes(extension)
}

/** z. B. „1 Endung“ oder „12 Endungen“ */
function countText(count: number, singular: string, plural: string): string {
  return `${count} ${count === 1 ? singular : plural}`
}

/**
 * Tab „FileMatrix“ der Administration: welche Dateiart welche Endungen annimmt. Links die Endungen nach Gruppen,
 * oben die Dateiarten, in der Ecke der Pinsel: freischalten (×) oder nicht (·).
 *
 * Malen: linke Maustaste gedrückt halten und über die Endungen fahren, jede bekommt den Wert des Pinsels, die Zeilen
 * der Gruppen bleiben dabei unberührt. Klick auf eine Endung: Sie bekommt den Wert des Pinsels, hat sie ihn schon,
 * schaltet sie um. In der Zeile einer Gruppe zählt die Zelle, wie viele ihrer Endungen frei sind, ein Klick schaltet
 * alle frei oder, wenn schon alle frei sind, alle ab. Speichern, Schraffur bis zur Bestätigung und Toast übernimmt
 * useMatrixCellEdits. Den Katalog pflegt der Tab „Endungen“.
 */
export function FileMatrixTab() {
  const { fileSubClasses, fileExtensions, fileExtensionCollections, errorMessage, reloadMasterData } = useMasterData()
  const [brush, setBrush] = useState(true)
  // Die Antwort des letzten Speicherns, bis die neu geladenen Stammdaten da sind
  const [saved, setSaved] = useState<{ basis: FileSubClass[] | null; result: FileSubClass[] } | null>(null)

  const serverFileSubClasses = saved !== null && saved.basis === fileSubClasses ? saved.result : fileSubClasses

  // Für die Handler, die nach dem Rendern laufen: die neuesten Stände
  const serverRef = useRef(serverFileSubClasses)
  const fileSubClassesRef = useRef(fileSubClasses)
  const fileExtensionsRef = useRef(fileExtensions)
  useEffect(() => {
    serverRef.current = serverFileSubClasses
    fileSubClassesRef.current = fileSubClasses
    fileExtensionsRef.current = fileExtensions
  })

  // Gespeichert wird je Dateiart: Für den Hook ist deshalb die Dateiart die Zeile und die Endung die Spalte
  const edits = useMatrixCellEdits<boolean>({
    serverValue: (fileSubClassKey, extension) =>
      serverEnabled(serverRef.current?.find((fileSubClass) => fileSubClass.key === fileSubClassKey), extension),
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
        serverRef.current = data
        setSaved({ basis: fileSubClassesRef.current, result: data })
        // die Endungen stehen auch in den Stammdaten, z. B. für den FSC-Tab
        reloadMasterData()
        return (fileSubClassKey: string, extension: string) =>
          serverEnabled(data.find((fileSubClass) => fileSubClass.key === fileSubClassKey), extension)
      })
    },
    savedToast: (cellCount, rowCount) => ({
      kind: 'success',
      title: 'Endungen gespeichert',
      text: `Alles sicher in der Datenbank: ${countText(cellCount, 'Endung', 'Endungen')} an ${countText(rowCount, 'Dateiart', 'Dateiarten')}.`,
    }),
  })

  const rows: FileMatrixRow[] = []
  if (fileExtensions !== null && fileExtensionCollections !== null) {
    for (const block of fileExtensionBlocks(fileExtensionCollections, fileExtensions)) {
      rows.push({ kind: 'group', block })
      for (const fileExtension of block.fileExtensions) {
        rows.push({ kind: 'extension', fileExtension })
      }
    }
  }

  /** Der Wert zum Zeichnen: noch nicht bestätigt, sonst der Stand des Backends */
  function shownEnabled(fileSubClass: FileSubClass, extension: string): boolean {
    const change = edits.pendingChange(fileSubClass.key, extension)
    return change !== undefined ? change.value : serverEnabled(fileSubClass, extension)
  }

  function isPending(row: FileMatrixRow, fileSubClass: FileSubClass): boolean {
    const extensions =
      row.kind === 'extension'
        ? [row.fileExtension.extension]
        : row.block.fileExtensions.map(({ extension }) => extension)
    return extensions.some((extension) => edits.pendingChange(fileSubClass.key, extension) !== undefined)
  }

  function clickCell(row: FileMatrixRow, fileSubClass: FileSubClass) {
    if (row.kind === 'extension') {
      const extension = row.fileExtension.extension
      const current = edits.currentValue(fileSubClass.key, extension)
      edits.changeCell(fileSubClass.key, extension, current === brush ? !current : brush)
      return
    }
    // Gruppe: alle frei, dann alle ab, sonst alle frei
    const blockExtensions = row.block.fileExtensions.map(({ extension }) => extension)
    if (blockExtensions.length === 0) {
      return
    }
    const allEnabled = blockExtensions.every((extension) => edits.currentValue(fileSubClass.key, extension))
    edits.changeCells(
      blockExtensions.map((extension) => ({ rowKey: fileSubClass.key, columnKey: extension, value: !allEnabled })),
    )
  }

  /** Gemalt wird nur auf Endungen, die Zeilen der Gruppen bleiben unberührt */
  function paintCell(row: FileMatrixRow, fileSubClass: FileSubClass) {
    if (row.kind === 'extension') {
      edits.paintCell(fileSubClass.key, row.fileExtension.extension, brush)
    }
  }

  const shownErrorMessage = errorMessage ?? edits.saveErrorMessage
  const loading = serverFileSubClasses === null || fileExtensions === null || fileExtensionCollections === null

  return (
    <>
      {/* Nur bei Fehler oder während des Ladens, sonst steht die Matrix allein */}
      {(shownErrorMessage !== null || loading) && (
        <div className="matrix-message">
          {shownErrorMessage !== null ? (
            <span className="text-danger">{shownErrorMessage}</span>
          ) : (
            <span className="spinner-border spinner-border-sm" role="status" aria-label="lädt" />
          )}
        </div>
      )}
      {!loading && (
        <Matrix
          rows={rows}
          columns={serverFileSubClasses}
          rowKey={(row) =>
            row.kind === 'group' ? `group-${row.block.fileExtensionCollection?.key ?? 'none'}` : `extension-${row.fileExtension.extension}`
          }
          columnKey={(fileSubClass) => fileSubClass.key}
          rowsLabel="Endung"
          columnsLabel="Dateiart"
          cornerContent={<MatrixBrushes brushes={BRUSHES} value={brush} onChange={setBrush} />}
          rowClassName={(row) => (row.kind === 'group' ? 'matrix-group-row' : undefined)}
          rowHeader={(row) =>
            row.kind === 'group' ? (
              <span className="matrix-row-head-content">
                <FileShape shape={row.block.fileExtensionCollection?.symbolShape ?? null} />
                {row.block.fileExtensionCollection
                  ? translate(row.block.fileExtensionCollection.displayName)
                  : 'ohne Gruppe'}
              </span>
            ) : (
              <span className="matrix-row-head-content">
                <Hoverlay text={translate(row.fileExtension.description)}>
                  <code>.{row.fileExtension.extension}</code>
                </Hoverlay>
                {row.fileExtension.dangerous && (
                  <Hoverlay text="Achtung: heikles Format. Freischalten geht, aber nur bewusst.">
                    <span className="text-danger">
                      <Icon name="warning" />
                    </span>
                  </Hoverlay>
                )}
              </span>
            )
          }
          columnHeader={(fileSubClass) => (
            <span className="matrix-file-sub-class">
              <FileSubClassSymbol fileSubClass={fileSubClass} />
              <span>{translate(fileSubClass.displayName) || fileSubClass.key}</span>
            </span>
          )}
          isMarked={(row, fileSubClass) =>
            row.kind === 'extension' && shownEnabled(fileSubClass, row.fileExtension.extension)
          }
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
          cellLabel={(row, fileSubClass) => {
            const pending = isPending(row, fileSubClass) ? ', noch nicht gespeichert' : ''
            if (row.kind === 'group') {
              return `alle Endungen der Gruppe für ${fileSubClass.key} umschalten${pending}`
            }
            const now = shownEnabled(fileSubClass, row.fileExtension.extension) ? 'frei' : 'nicht frei'
            return `.${row.fileExtension.extension} für ${fileSubClass.key}: ${now}${pending}`
          }}
          onCellClick={clickCell}
          onCellPaint={paintCell}
          onPaintEnd={edits.endPaint}
        />
      )}
    </>
  )
}
