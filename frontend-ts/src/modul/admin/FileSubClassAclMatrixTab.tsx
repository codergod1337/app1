import { useEffect, useRef, useState } from 'react'
import { Matrix } from '../../components/Matrix.tsx'
import { MatrixBrushes, type MatrixBrush } from '../../components/MatrixBrushes.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { changesByRow, useMatrixCellEdits } from '../../components/useMatrixCellEdits.ts'
import type { AccessRole } from '../accessrole/AccessRole.ts'
import { AccessRoleBadge } from '../accessrole/AccessRoleBadge.tsx'
import {
  changeFileSubClassAccessRoleKeys,
  type FileSubClass,
  type FileSubClassAccessRoleKeys,
} from '../filesubclass/FileSubClass.ts'
import { FileSubClassSymbol } from '../filesubclass/FileSubClassSymbol.tsx'

/** Was eine AR an einer Dateiart darf. W schließt Lesen ein. */
type Access = 'W' | 'R' | null

/** Die Pinsel oben in der Ecke */
const BRUSHES: MatrixBrush<Access>[] = [
  { value: null, label: '·', hint: 'Pinsel: kein Zugriff' },
  { value: 'R', label: 'R', hint: 'Pinsel: lesen' },
  { value: 'W', label: 'W', hint: 'Pinsel: schreiben, schließt Lesen ein' },
]

/** Weiterschalten beim Klick auf eine Zelle, die schon den Wert des Pinsels hat: · → R → W → · */
function nextAccess(access: Access): Access {
  return access === null ? 'R' : access === 'R' ? 'W' : null
}

/** Was die AR laut Backend an der Dateiart darf */
function serverAccess(fileSubClass: FileSubClass | undefined, accessRoleKey: string): Access {
  if ((fileSubClass?.writeAccessRoleKeys ?? []).includes(accessRoleKey)) {
    return 'W'
  }
  if ((fileSubClass?.readAccessRoleKeys ?? []).includes(accessRoleKey)) {
    return 'R'
  }
  return null
}

/** z. B. „1 Recht“ oder „12 Rechte“ */
function countText(count: number, singular: string, plural: string): string {
  return `${count} ${count === 1 ? singular : plural}`
}

/**
 * Tab „FSC-ACL“ der Administration: welche AR eine Dateiart lesen (R) oder schreiben (W) darf. Links die
 * Dateiarten, oben die AR, in der Ecke der Pinsel: kein Zugriff, R oder W.
 *
 * Malen: linke Maustaste gedrückt halten und über die Zellen fahren, jede bekommt den Wert des Pinsels. Klick: eine
 * Zelle bekommt den Wert des Pinsels, hat sie ihn schon, schaltet sie weiter (· → R → W → ·). Speichern, Schraffur
 * bis zur Bestätigung und Toast übernimmt useMatrixCellEdits.
 */
export function FileSubClassAclMatrixTab() {
  const { fileSubClasses, accessRoles, errorMessage, reloadMasterData } = useMasterData()
  const [brush, setBrush] = useState<Access>('R')
  // Die Antwort des letzten Speicherns, bis die neu geladenen Stammdaten da sind
  const [saved, setSaved] = useState<{ basis: FileSubClass[] | null; result: FileSubClass[] } | null>(null)

  const serverFileSubClasses = saved !== null && saved.basis === fileSubClasses ? saved.result : fileSubClasses

  // Für die Handler, die nach dem Rendern laufen: die neuesten Stände
  const serverRef = useRef(serverFileSubClasses)
  const fileSubClassesRef = useRef(fileSubClasses)
  const accessRolesRef = useRef(accessRoles)
  useEffect(() => {
    serverRef.current = serverFileSubClasses
    fileSubClassesRef.current = fileSubClasses
    accessRolesRef.current = accessRoles
  })

  const edits = useMatrixCellEdits<Access>({
    serverValue: (fileSubClassKey, accessRoleKey) =>
      serverAccess(serverRef.current?.find((fileSubClass) => fileSubClass.key === fileSubClassKey), accessRoleKey),
    // je geänderte Dateiart die vollständigen neuen Listen über alle AR
    save: (changes) => {
      const changedRows = changesByRow(changes)
      const changedAccessRoleKeys: FileSubClassAccessRoleKeys[] = []
      for (const fileSubClass of serverRef.current ?? []) {
        const rowChanges = changedRows.get(fileSubClass.key)
        if (rowChanges === undefined) {
          continue
        }
        const readAccessRoleKeys: string[] = []
        const writeAccessRoleKeys: string[] = []
        for (const accessRole of accessRolesRef.current ?? []) {
          const access = rowChanges.has(accessRole.key)
            ? (rowChanges.get(accessRole.key) ?? null)
            : serverAccess(fileSubClass, accessRole.key)
          if (access === 'W') {
            writeAccessRoleKeys.push(accessRole.key)
          }
          if (access !== null) {
            readAccessRoleKeys.push(accessRole.key)
          }
        }
        changedAccessRoleKeys.push({ key: fileSubClass.key, readAccessRoleKeys, writeAccessRoleKeys })
      }
      return changeFileSubClassAccessRoleKeys(changedAccessRoleKeys).then(({ data }) => {
        serverRef.current = data
        setSaved({ basis: fileSubClassesRef.current, result: data })
        // die Rechte stehen auch in den Stammdaten, z. B. für den FSC-Tab
        reloadMasterData()
        return (fileSubClassKey: string, accessRoleKey: string) =>
          serverAccess(data.find((fileSubClass) => fileSubClass.key === fileSubClassKey), accessRoleKey)
      })
    },
    savedToast: (cellCount, rowCount) => ({
      kind: 'success',
      title: 'Zugriffsrechte gespeichert',
      text: `Alles sicher in der Datenbank: ${countText(cellCount, 'Recht', 'Rechte')} an ${countText(rowCount, 'Dateiart', 'Dateiarten')}.`,
    }),
  })

  function clickCell(fileSubClass: FileSubClass, accessRole: AccessRole) {
    const current = edits.currentValue(fileSubClass.key, accessRole.key)
    edits.changeCell(fileSubClass.key, accessRole.key, current === brush ? nextAccess(current) : brush)
  }

  /** Der Wert zum Zeichnen: noch nicht bestätigt, sonst der Stand des Backends */
  function shownAccess(fileSubClass: FileSubClass, accessRoleKey: string): Access {
    const change = edits.pendingChange(fileSubClass.key, accessRoleKey)
    return change !== undefined ? change.value : serverAccess(fileSubClass, accessRoleKey)
  }

  const shownErrorMessage = errorMessage ?? edits.saveErrorMessage
  const loading = serverFileSubClasses === null || accessRoles === null

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
          rows={serverFileSubClasses}
          columns={accessRoles}
          rowKey={(fileSubClass) => fileSubClass.key}
          columnKey={(accessRole) => accessRole.key}
          rowsLabel="Dateiart"
          columnsLabel="AccessRole"
          cornerContent={<MatrixBrushes brushes={BRUSHES} value={brush} onChange={setBrush} />}
          rowHeader={(fileSubClass) => (
            <span className="matrix-row-head-content">
              <FileSubClassSymbol fileSubClass={fileSubClass} />
              {translate(fileSubClass.displayName) || fileSubClass.key}
            </span>
          )}
          columnHeader={(accessRole) => <AccessRoleBadge badge={accessRole} size="full" shape="rect" />}
          isMarked={(fileSubClass, accessRole) => shownAccess(fileSubClass, accessRole.key) !== null}
          cellContent={(fileSubClass, accessRole) => {
            const access = shownAccess(fileSubClass, accessRole.key)
            if (access === 'W') {
              return <span className="matrix-mark">W</span>
            }
            if (access === 'R') {
              return <span className="matrix-read">R</span>
            }
            return undefined
          }}
          cellClassName={(fileSubClass, accessRole) =>
            edits.pendingChange(fileSubClass.key, accessRole.key) !== undefined ? 'matrix-cell-pending' : undefined
          }
          cellLabel={(fileSubClass, accessRole) => {
            const access = shownAccess(fileSubClass, accessRole.key)
            const now = access === 'W' ? 'schreiben' : access === 'R' ? 'lesen' : 'kein Zugriff'
            const pending =
              edits.pendingChange(fileSubClass.key, accessRole.key) !== undefined ? ', noch nicht gespeichert' : ''
            return `${accessRole.key} an ${fileSubClass.key}: ${now}${pending}`
          }}
          onCellClick={clickCell}
          onCellPaint={(fileSubClass, accessRole) => edits.paintCell(fileSubClass.key, accessRole.key, brush)}
          onPaintEnd={edits.endPaint}
        />
      )}
    </>
  )
}
