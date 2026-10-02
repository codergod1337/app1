/**
 * Vermittler zwischen „hier soll ein Toast erscheinen“ und der Komponente, die ihn zeigt (Toasts im Layout). Die
 * Komponente meldet sich beim Start an, danach ruft jede Stelle einfach showToast(...) auf, auch Code außerhalb von
 * React. Nicht für jeden Request, nur für Rückmeldungen, die man sonst verpassen würde.
 */

/** success: geschafft, error: schiefgegangen, info: nur zur Kenntnis */
export type ToastKind = 'success' | 'error' | 'info'

export interface ToastMessage {
  kind: ToastKind
  /** kurz, fett, z. B. „Zugriffsrechte gespeichert“ */
  title: string
  /** optional darunter, ein Satz */
  text?: string
}

type ToastHandler = (toast: ToastMessage) => void

let toastHandler: ToastHandler | null = null

/** Nur für Toasts: meldet sich beim Start an und beim Abbauen mit null wieder ab */
export function registerToastHandler(handler: ToastHandler | null) {
  toastHandler = handler
}

/** Zeigt einen Toast unten rechts. Ohne angemeldete Komponente passiert nichts. */
export function showToast(toast: ToastMessage) {
  toastHandler?.(toast)
}
