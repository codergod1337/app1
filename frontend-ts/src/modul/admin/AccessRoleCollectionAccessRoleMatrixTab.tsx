import { useEffect, useRef, useState } from 'react'
import { Matrix } from '../../components/Matrix.tsx'
import { MatrixBrushes, type MatrixBrush } from '../../components/MatrixBrushes.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { changesByRow, useMatrixCellEdits } from '../../components/useMatrixCellEdits.ts'
import type { AccessRole } from '../accessrole/AccessRole.ts'
import { AccessRoleBadge } from '../accessrole/AccessRoleBadge.tsx'
import {
  changeAccessRoleCollectionAccessRoleKeys,
  type AccessRoleCollection,
  type AccessRoleCollectionAccessRoleKeys,
} from '../accessrolecollection/AccessRoleCollection.ts'

/** Die Pinsel oben in der Ecke */
const BRUSHES: MatrixBrush<boolean>[] = [
  { value: false, label: '·', hint: 'Pinsel: nicht verleihen' },
  { value: true, label: '×', hint: 'Pinsel: verleihen' },
]

/** Verleiht die ARC laut Backend diese AR? */
function serverGranted(accessRoleCollection: AccessRoleCollection | undefined, accessRoleKey: string): boolean {
  return (accessRoleCollection?.accessRoleKeys ?? []).includes(accessRoleKey)
}

/** z. B. „1 Zuordnung“ oder „12 Zuordnungen“ */
function countText(count: number, singular: string, plural: string): string {
  return `${count} ${count === 1 ? singular : plural}`
}

/**
 * Tab „ARC-AR“ der Administration: welche AR eine ARC verleiht. Links die ARCs, oben die AR, in der Ecke der Pinsel:
 * verleihen (×) oder nicht (·).
 *
 * Malen: linke Maustaste gedrückt halten und über die Zellen fahren, jede bekommt den Wert des Pinsels. Klick: eine
 * Zelle bekommt den Wert des Pinsels, hat sie ihn schon, schaltet sie um. Speichern, Schraffur bis zur Bestätigung und
 * Toast übernimmt useMatrixCellEdits. Wer eine geänderte ARC trägt, bekommt beim nächsten Refresh die neuen AR.
 */
export function AccessRoleCollectionAccessRoleMatrixTab() {
  const { accessRoleCollections, accessRoles, errorMessage, reloadMasterData } = useMasterData()
  const [brush, setBrush] = useState(true)
  // Die Antwort des letzten Speicherns, bis die neu geladenen Stammdaten da sind
  const [saved, setSaved] = useState<{
    basis: AccessRoleCollection[] | null
    result: AccessRoleCollection[]
  } | null>(null)

  const serverAccessRoleCollections =
    saved !== null && saved.basis === accessRoleCollections ? saved.result : accessRoleCollections

  // Für die Handler, die nach dem Rendern laufen: die neuesten Stände
  const serverRef = useRef(serverAccessRoleCollections)
  const accessRoleCollectionsRef = useRef(accessRoleCollections)
  const accessRolesRef = useRef(accessRoles)
  useEffect(() => {
    serverRef.current = serverAccessRoleCollections
    accessRoleCollectionsRef.current = accessRoleCollections
    accessRolesRef.current = accessRoles
  })

  const edits = useMatrixCellEdits<boolean>({
    serverValue: (accessRoleCollectionKey, accessRoleKey) =>
      serverGranted(serverRef.current?.find((arc) => arc.key === accessRoleCollectionKey), accessRoleKey),
    // je geänderte ARC die vollständige neue Liste über alle AR
    save: (changes) => {
      const changedRows = changesByRow(changes)
      const changedAccessRoleKeys: AccessRoleCollectionAccessRoleKeys[] = []
      for (const accessRoleCollection of serverRef.current ?? []) {
        const rowChanges = changedRows.get(accessRoleCollection.key)
        if (rowChanges === undefined) {
          continue
        }
        const accessRoleKeys = (accessRolesRef.current ?? [])
          .filter((accessRole) =>
            rowChanges.has(accessRole.key)
              ? rowChanges.get(accessRole.key)
              : serverGranted(accessRoleCollection, accessRole.key),
          )
          .map((accessRole) => accessRole.key)
        changedAccessRoleKeys.push({ key: accessRoleCollection.key, accessRoleKeys })
      }
      return changeAccessRoleCollectionAccessRoleKeys(changedAccessRoleKeys).then(({ data }) => {
        serverRef.current = data
        setSaved({ basis: accessRoleCollectionsRef.current, result: data })
        // die ARCs stehen auch in den Stammdaten, z. B. für den ARC-Tab
        reloadMasterData()
        return (accessRoleCollectionKey: string, accessRoleKey: string) =>
          serverGranted(data.find((arc) => arc.key === accessRoleCollectionKey), accessRoleKey)
      })
    },
    savedToast: (cellCount, rowCount) => ({
      kind: 'success',
      title: 'Verliehene AR gespeichert',
      text: `Alles sicher in der Datenbank: ${countText(cellCount, 'Zuordnung', 'Zuordnungen')} an ${countText(rowCount, 'ARC', 'ARC')}.`,
    }),
  })

  function clickCell(accessRoleCollection: AccessRoleCollection, accessRole: AccessRole) {
    const current = edits.currentValue(accessRoleCollection.key, accessRole.key)
    edits.changeCell(accessRoleCollection.key, accessRole.key, current === brush ? !current : brush)
  }

  /** Der Wert zum Zeichnen: noch nicht bestätigt, sonst der Stand des Backends */
  function shownGranted(accessRoleCollection: AccessRoleCollection, accessRoleKey: string): boolean {
    const change = edits.pendingChange(accessRoleCollection.key, accessRoleKey)
    return change !== undefined ? change.value : serverGranted(accessRoleCollection, accessRoleKey)
  }

  const shownErrorMessage = errorMessage ?? edits.saveErrorMessage
  const loading = serverAccessRoleCollections === null || accessRoles === null

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
          rows={serverAccessRoleCollections}
          columns={accessRoles}
          rowKey={(arc) => arc.key}
          columnKey={(accessRole) => accessRole.key}
          rowsLabel="AccessRoleCollection"
          columnsLabel="AccessRole"
          cornerContent={<MatrixBrushes brushes={BRUSHES} value={brush} onChange={setBrush} />}
          rowHeader={(arc) => <AccessRoleBadge badge={arc} size="full" shape="pill" />}
          columnHeader={(accessRole) => <AccessRoleBadge badge={accessRole} size="full" shape="rect" />}
          isMarked={(arc, accessRole) => shownGranted(arc, accessRole.key)}
          cellClassName={(arc, accessRole) =>
            edits.pendingChange(arc.key, accessRole.key) !== undefined ? 'matrix-cell-pending' : undefined
          }
          cellLabel={(arc, accessRole) => {
            const now = shownGranted(arc, accessRole.key) ? 'verliehen' : 'nicht verliehen'
            const pending = edits.pendingChange(arc.key, accessRole.key) !== undefined ? ', noch nicht gespeichert' : ''
            return `${accessRole.key} über ${arc.key}: ${now}${pending}`
          }}
          onCellClick={clickCell}
          onCellPaint={(arc, accessRole) => edits.paintCell(arc.key, accessRole.key, brush)}
          onPaintEnd={edits.endPaint}
        />
      )}
    </>
  )
}
