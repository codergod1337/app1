import { useState, type ReactNode } from 'react'
import { RequestError } from '../api/client.ts'
import { DeleteButton } from './DeleteButton.tsx'
import { Popup } from './Popup.tsx'

interface DeletePopupProps {
  open: boolean
  title: string
  /** die Frage, z. B. „Soll … wirklich gelöscht werden?“ */
  children: ReactNode
  /** löscht wirklich, z. B. () => deleteUsers(guid) */
  onConfirm: () => Promise<unknown>
  /** nach erfolgreichem Löschen, z. B. Liste neu laden */
  onDeleted: () => void
  onClose: () => void
}

/** Die Sicherheitsabfrage vor jedem Löschen. Erst „Löschen“ im Popup löscht wirklich, ein Fehler bleibt im Popup stehen. */
export function DeletePopup({ open, title, children, onConfirm, onDeleted, onClose }: DeletePopupProps) {
  const [deleting, setDeleting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  function close() {
    setErrorMessage(null)
    onClose()
  }

  function confirmDelete() {
    setDeleting(true)
    setErrorMessage(null)
    onConfirm()
      .then(() => {
        close()
        onDeleted()
      })
      .catch((error: unknown) => setErrorMessage(error instanceof RequestError ? error.message : 'Unbekannter Fehler'))
      .finally(() => setDeleting(false))
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-cancel" disabled={deleting} onClick={close}>
          Abbrechen
        </button>
        <DeleteButton label={title} disabled={deleting} onClick={confirmDelete}>
          Löschen
        </DeleteButton>
      </span>
    </>
  )

  return (
    <Popup open={open} title={title} footer={footer} onClose={close}>
      <p className="mb-0">{children}</p>
    </Popup>
  )
}
