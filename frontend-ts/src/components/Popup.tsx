import { useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { ContentBox } from './ContentBox.tsx'

interface PopupProps {
  open: boolean
  title: ReactNode
  /** Inhalt des contentbox-footer */
  footer?: ReactNode
  onClose: () => void
  children: ReactNode
}

/**
 * Ein Popup über der Seite: eine ContentBox in einem modalen dialog-Element. Schließt mit dem Knopf, mit Esc und
 * mit einem Klick auf den abgedunkelten Hintergrund. Gescrollt wird nur in contentbox-main.
 *
 * Hängt per Portal an document.body, damit es nie in einem fremden form landet (Enter im Suchfeld schickte es sonst ab).
 */
export function Popup({ open, title, footer, onClose, children }: PopupProps) {
  const dialog = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const element = dialog.current
    if (!element) {
      return
    }
    if (open && !element.open) {
      element.showModal()
    } else if (!open && element.open) {
      element.close()
    }
  }, [open])

  const closeButton = (
    <button type="button" className="button-cancel" onClick={onClose}>
      Schließen
    </button>
  )

  return createPortal(
    <dialog
      ref={dialog}
      className="popup"
      onClose={onClose}
      // Klick auf den Hintergrund: das Ziel ist dann das dialog-Element selbst, nicht sein Inhalt
      onClick={(event) => {
        if (event.target === dialog.current) {
          onClose()
        }
      }}
    >
      {open && (
        <ContentBox title={title} actions={closeButton} footer={footer}>
          {children}
        </ContentBox>
      )}
    </dialog>,
    document.body,
  )
}
