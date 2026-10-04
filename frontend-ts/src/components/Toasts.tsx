import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react'
import type { IconName } from '../branding/icons.ts'
import { Icon } from './Icon.tsx'
import { registerToastHandler, type ToastKind, type ToastMessage } from './toastStore.ts'

/** So lange steht ein Toast, solange die Maus nicht darauf ruht */
const TOAST_DURATION_MS = 5000
/** So viel Zeit hat das Theme zum Ausblenden, dann ist der Toast weg */
const TOAST_LEAVE_MS = 300

const ICON_BY_KIND: Record<ToastKind, IconName> = {
  success: 'check',
  error: 'error',
  info: 'info',
  upload: 'upload',
}

interface ShownToast extends ToastMessage {
  id: number
  /** wird gerade ausgeblendet */
  leaving: boolean
}

/**
 * Die Toasts unten rechts, steht einmal im Layout. Aufgerufen wird über showToast, updateToast und dismissToast
 * (toastStore.ts). Die Klassen heißen app-toast-*, weil .toast Bootstrap gehört. Das Aussehen gestaltet das Theme
 * komplett, basic.css stellt nur die Leiste an den Rand.
 */
export function Toasts() {
  const [toasts, setToasts] = useState<ShownToast[]>([])

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.map((toast) => (toast.id === id ? { ...toast, leaving: true } : toast)))
    setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), TOAST_LEAVE_MS)
  }, [])

  useEffect(() => {
    registerToastHandler({
      show: (id, toast) => setToasts((current) => [...current, { ...toast, id, leaving: false }]),
      update: (id, patch) =>
        setToasts((current) => current.map((toast) => (toast.id === id ? { ...toast, ...patch } : toast))),
      dismiss,
    })
    return () => registerToastHandler(null)
  }, [dismiss])

  return (
    <div className="app-toast-container" role="status" aria-live="polite">
      {toasts.map((toast) => (
        <ToastItem key={toast.id} toast={toast} onDismiss={dismiss} />
      ))}
    </div>
  )
}

/**
 * Ein Toast. Ruht die Maus darauf oder steht der Fokus darin, hält die Zeit an, der Balken unten auch. Ein sticky
 * Toast läuft nicht ab, seine Zeit beginnt erst, wenn ein Update ihn freigibt. Mit progress zeigt der Balken den
 * Fortschritt statt der Zeit.
 */
function ToastItem({ toast, onDismiss }: { toast: ShownToast; onDismiss: (id: number) => void }) {
  const [paused, setPaused] = useState(false)
  const remainingMsRef = useRef(TOAST_DURATION_MS)
  const sticky = toast.sticky === true

  useEffect(() => {
    if (paused || toast.leaving || sticky) {
      return
    }
    const startedAt = Date.now()
    const timer = setTimeout(() => onDismiss(toast.id), remainingMsRef.current)
    return () => {
      clearTimeout(timer)
      remainingMsRef.current -= Date.now() - startedAt
    }
  }, [paused, toast.leaving, sticky, toast.id, onDismiss])

  const className = ['app-toast', `app-toast-${toast.kind}`, paused && 'app-toast-paused', toast.leaving && 'app-toast-leaving']
    .filter(Boolean)
    .join(' ')
  // Fortschritt oder stehend: der Balken zeigt den Stand, nicht die Zeit
  const manualBar = sticky || (toast.progress !== undefined && toast.progress !== null)
  const progress = Math.max(0, Math.min(100, toast.progress ?? 0))

  return (
    <div
      className={className}
      style={{ '--app-toast-duration': `${TOAST_DURATION_MS}ms` } as CSSProperties}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
    >
      <span className="app-toast-icon" aria-hidden="true">
        <Icon name={ICON_BY_KIND[toast.kind]} />
      </span>
      <div className="app-toast-body">
        <div className="app-toast-title">{toast.title}</div>
        {toast.text && <div className="app-toast-text">{toast.text}</div>}
      </div>
      <button type="button" className="app-toast-close" aria-label="Schließen" onClick={() => onDismiss(toast.id)}>
        <Icon name="close" />
      </button>
      <span
        className={manualBar ? 'app-toast-progress app-toast-progress-manual' : 'app-toast-progress'}
        style={manualBar ? { transform: `scaleX(${progress / 100})` } : undefined}
        aria-hidden="true"
      />
    </div>
  )
}
