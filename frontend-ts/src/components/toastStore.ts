/**
 * Vermittler zwischen „hier soll ein Toast erscheinen“ und der Komponente, die ihn zeigt (Toasts im Layout). Die
 * Komponente meldet sich beim Start an, danach ruft jede Stelle einfach showToast(...) auf, auch Code außerhalb von
 * React. Nicht für jeden Request, nur für Rückmeldungen, die man sonst verpassen würde.
 *
 * Ein Toast lässt sich nachträglich ändern (updateToast): ein Upload zeigt so einen einzigen Toast, der mitläuft und
 * am Ende zum Ergebnis wird, statt eine Kette von Meldungen.
 */

/** success: geschafft, error: schiefgegangen, info: nur zur Kenntnis, upload: läuft gerade, mit Fortschritt */
export type ToastKind = 'success' | 'error' | 'info' | 'upload'

export interface ToastMessage {
  kind: ToastKind
  /** kurz, fett, z. B. „Zugriffsrechte gespeichert“ */
  title: string
  /** optional darunter, ein Satz */
  text?: string
  /** bleibt stehen, bis ein updateToast ihn freigibt oder dismissToast ihn schließt, z. B. solange ein Upload läuft */
  sticky?: boolean
  /** 0 bis 100: der Balken unten zeigt diesen Fortschritt statt der ablaufenden Zeit */
  progress?: number | null
}

export interface ToastHandler {
  show: (id: number, toast: ToastMessage) => void
  update: (id: number, patch: Partial<ToastMessage>) => void
  dismiss: (id: number) => void
}

let toastHandler: ToastHandler | null = null
let nextId = 1

/** Nur für Toasts: meldet sich beim Start an und beim Abbauen mit null wieder ab */
export function registerToastHandler(handler: ToastHandler | null) {
  toastHandler = handler
}

/** Zeigt einen Toast unten rechts. Die id dient updateToast und dismissToast. Ohne angemeldete Komponente passiert nichts. */
export function showToast(toast: ToastMessage): number {
  const id = nextId++
  toastHandler?.show(id, toast)
  return id
}

/** Ändert einen stehenden Toast, z. B. Text und Fortschritt, oder macht ihn mit sticky false zum ablaufenden Ergebnis */
export function updateToast(id: number, patch: Partial<ToastMessage>) {
  toastHandler?.update(id, patch)
}

export function dismissToast(id: number) {
  toastHandler?.dismiss(id)
}
