import { useState, type FormEvent } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { ConfirmCancelButtons } from '../../components/ConfirmCancelButtons.tsx'
import { Popup } from '../../components/Popup.tsx'
import { showToast } from '../../components/toastStore.ts'
import { ignoreAbasSternzeit } from './AbasSternzeit.ts'

interface AbasIgnoreSternzeitPopupProps {
  /** die Kennung, die gesperrt werden soll; null heißt geschlossen */
  sternzeit: string | null
  onClose: () => void
  /** nach dem Sperren, z. B. zum Neuladen der Zahlen */
  onIgnored: () => void
}

/**
 * Eine Sternzeit auf die Sperrliste setzen (User 2026-10-10): mit optionalem Grund. Das Backend weist danach jedes
 * Einreihen mit 403 ab und entfernt einen wartenden Queue-Eintrag.
 */
export function AbasIgnoreSternzeitPopup({ sternzeit, onClose, onIgnored }: AbasIgnoreSternzeitPopupProps) {
  const { t } = useTranslation()
  const [reason, setReason] = useState('')
  const [saving, setSaving] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  function close() {
    setReason('')
    setErrorMessage(null)
    onClose()
  }

  function ignore(event: FormEvent) {
    event.preventDefault()
    if (sternzeit === null) {
      return
    }
    setSaving(true)
    setErrorMessage(null)
    ignoreAbasSternzeit(sternzeit, reason.trim() === '' ? null : reason.trim())
      .then(() => {
        showToast({
          kind: 'success',
          title: t('abasmanager.overview.ignoreTitle'),
          text: t('abasmanager.overview.ignored', { sternzeit }),
        })
        onIgnored()
        close()
      })
      .catch((error: unknown) =>
        setErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError')),
      )
      .finally(() => setSaving(false))
  }

  const footer = (
    <ConfirmCancelButtons
      onCancel={close}
      confirmLabel={t('abasmanager.overview.ignore')}
      confirmForm="abas-ignore-sternzeit-form"
      busy={saving}
    />
  )

  return (
    <Popup open={sternzeit !== null} title={t('abasmanager.overview.ignoreTitle')} footer={footer} narrow onClose={close}>
      <form id="abas-ignore-sternzeit-form" className="d-flex flex-column gap-2" onSubmit={ignore}>
        <p className="mb-0">
          <Trans
            i18nKey="abasmanager.overview.ignoreIntro"
            values={{ sternzeit: sternzeit ?? '' }}
            components={{ strong: <strong /> }}
          />
        </p>
        <input
          type="text"
          className="form-control form-control-sm"
          value={reason}
          placeholder={t('abasmanager.overview.ignoreReason')}
          aria-label={t('abasmanager.overview.ignoreReason')}
          onChange={(event) => setReason(event.target.value)}
        />
        {errorMessage !== null && <p className="text-danger mb-0">{errorMessage}</p>}
      </form>
    </Popup>
  )
}
