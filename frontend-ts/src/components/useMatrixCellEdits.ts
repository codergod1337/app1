import { useEffect, useRef, useState } from 'react'
import { RequestError } from '../api/client.ts'
import { showToast, type ToastMessage } from './toastStore.ts'

/** So lange muss nach der letzten Bestätigung Ruhe sein, dann kommt der Toast */
const SAVED_TOAST_DELAY_MS = 2000

/** Eine geänderte Zelle einer Matrix */
export interface MatrixCellChange<V> {
  rowKey: string
  columnKey: string
  value: V
}

interface MatrixCellEditsOptions<V> {
  /** Wert einer Zelle laut Backend, mit dem neuesten Stand. Läuft nur in Handlern, darf also aus Refs lesen. */
  serverValue: (rowKey: string, columnKey: string) => V
  /**
   * Speichert die Zellen, die vom Backend abweichen, in einem Request. Übernimmt die Antwort als neuen Stand und
   * liefert, was nun im Backend steht: Daran wird jede Zelle einzeln bestätigt.
   */
  save: (changes: MatrixCellChange<V>[]) => Promise<(rowKey: string, columnKey: string) => V>
  /** der Toast, wenn alles bestätigt ist und zwei Sekunden Ruhe war */
  savedToast: (cellCount: number, rowCount: number) => ToastMessage
}

function cellKey(rowKey: string, columnKey: string): string {
  return `${rowKey}|${columnKey}`
}

/**
 * Die Änderungen je Zeile, z. B. um daraus je Zeile die vollständige neue Liste zu bauen. „Zeile“ ist dabei, was
 * als Ganzes gespeichert wird: Der Aufrufer wählt, welcher Key rowKey ist, unabhängig davon, wie die Matrix steht.
 */
export function changesByRow<V>(changes: MatrixCellChange<V>[]): Map<string, Map<string, V>> {
  const byRow = new Map<string, Map<string, V>>()
  for (const { rowKey, columnKey, value } of changes) {
    const rowChanges = byRow.get(rowKey) ?? new Map<string, V>()
    rowChanges.set(columnKey, value)
    byRow.set(rowKey, rowChanges)
  }
  return byRow
}

/**
 * Zellen einer Matrix bearbeiten, ohne dass etwas stockt. Beim Malen wird nur lokal markiert, höchstens einmal pro
 * Bildschirmframe gezeichnet und erst beim Loslassen gespeichert: ein Request mit allen geänderten Zellen. Wird
 * währenddessen weitergemalt, folgt danach der nächste.
 *
 * Bis das Backend eine Zelle bestätigt hat, liefert pendingChange sie, die Matrix schraffiert sie. Bestätigt heißt:
 * Die Antwort enthält für diese Zelle genau den gesetzten Wert. Ist alles bestätigt und zwei Sekunden Ruhe, kommt zur
 * Sicherheit noch ein Toast.
 */
export function useMatrixCellEdits<V>(options: MatrixCellEditsOptions<V>) {
  const optionsRef = useRef(options)
  useEffect(() => {
    optionsRef.current = options
  })
  // Die Map ist der Stand für die Logik, der State nur fürs Zeichnen
  const pendingRef = useRef(new Map<string, MatrixCellChange<V>>())
  const [pending, setPending] = useState<Map<string, MatrixCellChange<V>>>(() => new Map())
  const frameRef = useRef(0)
  const savingRef = useRef(false)
  const saveAgainRef = useRef(false)
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)
  // Für den Toast: was seit dem letzten bestätigt wurde und ob dabei etwas schiefging
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const confirmedCellKeysRef = useRef(new Set<string>())
  const confirmedRowKeysRef = useRef(new Set<string>())
  const failedSinceToastRef = useRef(false)

  /** Den State sofort auf den Stand der Map bringen */
  function flushPending() {
    if (frameRef.current !== 0) {
      cancelAnimationFrame(frameRef.current)
      frameRef.current = 0
    }
    setPending(new Map(pendingRef.current))
  }

  /** Den State beim nächsten Bildschirmframe nachziehen, beim Malen nicht öfter */
  function schedulePendingFlush() {
    if (frameRef.current === 0) {
      frameRef.current = requestAnimationFrame(() => {
        frameRef.current = 0
        setPending(new Map(pendingRef.current))
      })
    }
  }

  /** Der Wert einer Zelle für die Logik, nur in Handlern: noch nicht bestätigt, sonst laut Backend */
  function currentValue(rowKey: string, columnKey: string): V {
    const change = pendingRef.current.get(cellKey(rowKey, columnKey))
    return change !== undefined ? change.value : optionsRef.current.serverValue(rowKey, columnKey)
  }

  /** Beim Malen: nur merken und zeichnen, gespeichert wird beim Loslassen (endPaint) */
  function paintCell(rowKey: string, columnKey: string, value: V) {
    clearTimeout(toastTimerRef.current)
    pendingRef.current.set(cellKey(rowKey, columnKey), { rowKey, columnKey, value })
    schedulePendingFlush()
  }

  /** Ein Klick: sofort zeichnen und speichern */
  function changeCell(rowKey: string, columnKey: string, value: V) {
    changeCells([{ rowKey, columnKey, value }])
  }

  /** Mehrere Zellen auf einmal, z. B. alle einer Gruppe: sofort zeichnen und in einem Request speichern */
  function changeCells(changes: MatrixCellChange<V>[]) {
    clearTimeout(toastTimerRef.current)
    for (const change of changes) {
      pendingRef.current.set(cellKey(change.rowKey, change.columnKey), change)
    }
    flushPending()
    saveCells()
  }

  /** Nach dem Loslassen eines Malstrichs */
  function endPaint() {
    flushPending()
    saveCells()
  }

  /** Abgeschickte Zellen fallen weg, außer sie wurden seit dem Abschicken noch einmal anders gesetzt */
  function removeSent(sent: Map<string, MatrixCellChange<V>>) {
    for (const [key, change] of sent) {
      if (pendingRef.current.get(key)?.value === change.value) {
        pendingRef.current.delete(key)
      }
    }
    flushPending()
  }

  /**
   * Ist nichts mehr offen, kein Request unterwegs und keine Zelle unbestätigt, kommt nach zwei Sekunden Ruhe der
   * Toast. Jede neue Zelle hält ihn auf. Ging seit dem letzten Toast etwas schief, steht das schon an der Matrix,
   * dann kein Toast „alles gespeichert“.
   */
  function scheduleSavedToast() {
    clearTimeout(toastTimerRef.current)
    if (savingRef.current || pendingRef.current.size > 0) {
      return
    }
    if (failedSinceToastRef.current) {
      failedSinceToastRef.current = false
      confirmedCellKeysRef.current.clear()
      confirmedRowKeysRef.current.clear()
      return
    }
    if (confirmedCellKeysRef.current.size === 0) {
      return
    }
    toastTimerRef.current = setTimeout(() => {
      const toast = optionsRef.current.savedToast(confirmedCellKeysRef.current.size, confirmedRowKeysRef.current.size)
      confirmedCellKeysRef.current.clear()
      confirmedRowKeysRef.current.clear()
      showToast(toast)
    }, SAVED_TOAST_DELAY_MS)
  }

  /** Schickt alle Zellen, die vom Backend abweichen, in einem Request. Läuft schon einer, folgt dieser danach. */
  function saveCells() {
    if (savingRef.current) {
      saveAgainRef.current = true
      return
    }
    const { serverValue, save } = optionsRef.current
    const sent = new Map(pendingRef.current)
    const changes = [...sent.values()].filter(
      (change) => !Object.is(change.value, serverValue(change.rowKey, change.columnKey)),
    )

    // Alles Gesetzte entspricht schon dem Backend, z. B. über Gesetztes gemalt
    if (changes.length === 0) {
      removeSent(sent)
      scheduleSavedToast()
      return
    }

    savingRef.current = true
    setSaveErrorMessage(null)
    save(changes)
      .then((valueAfter) => {
        // Jede Zelle gegen die Antwort: Steht dort genau der gesetzte Wert, ist sie bestätigt
        let notTakenCount = 0
        for (const change of changes) {
          if (Object.is(valueAfter(change.rowKey, change.columnKey), change.value)) {
            confirmedCellKeysRef.current.add(cellKey(change.rowKey, change.columnKey))
            confirmedRowKeysRef.current.add(change.rowKey)
          } else {
            notTakenCount++
          }
        }
        if (notTakenCount > 0) {
          failedSinceToastRef.current = true
          setSaveErrorMessage(
            `${notTakenCount} ${notTakenCount === 1 ? 'Zelle hat' : 'Zellen haben'} das Backend nicht übernommen, ` +
              'die Matrix zeigt den gespeicherten Stand.',
          )
        }
        removeSent(sent)
      })
      .catch((error: unknown) => {
        failedSinceToastRef.current = true
        setSaveErrorMessage(error instanceof RequestError ? error.message : 'Unbekannter Fehler')
        // nicht gespeichert: zurück auf den Stand des Backends
        removeSent(sent)
      })
      .finally(() => {
        savingRef.current = false
        if (saveAgainRef.current) {
          saveAgainRef.current = false
          saveCells()
        } else {
          scheduleSavedToast()
        }
      })
  }

  return {
    /** Fürs Zeichnen: die noch nicht bestätigte Änderung einer Zelle, undefined wenn es keine gibt */
    pendingChange: (rowKey: string, columnKey: string): MatrixCellChange<V> | undefined =>
      pending.get(cellKey(rowKey, columnKey)),
    currentValue,
    paintCell,
    changeCell,
    changeCells,
    endPaint,
    saveErrorMessage,
  }
}
