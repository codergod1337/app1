import { useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'
import { CancelButton } from './CancelButton.tsx'
import { ContentBox } from './ContentBox.tsx'

interface PopupProps {
  open: boolean
  title: ReactNode
  /** Inhalt des contentbox-footer */
  footer?: ReactNode
  /** schmal (24rem statt 64rem): für ein einzelnes Bedienelement, etwa einen Regler */
  narrow?: boolean
  onClose: () => void
  children: ReactNode
}

/**
 * Ein Popup über der Seite: eine ContentBox in einem modalen dialog-Element. Schließt mit dem Knopf, mit Esc und
 * mit einem Klick auf den abgedunkelten Hintergrund. Gescrollt wird nur in contentbox-main.
 *
 * Hängt per Portal an document.body, damit es nie in einem fremden form landet (Enter im Suchfeld schickte es sonst ab).
 */
export function Popup({ open, title, footer, narrow = false, onClose, children }: PopupProps) {
  const { t } = useTranslation()
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

  const closeButton = <CancelButton label={t('common.close')} onClick={onClose} />

  return createPortal(
    <dialog
      ref={dialog}
      className={narrow ? 'popup popup-narrow' : 'popup'}
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
