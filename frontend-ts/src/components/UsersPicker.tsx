import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { usersDisplayName } from '../modul/profile/profileDisplay.ts'
import { suggestUsers, type Users } from '../modul/users/Users.ts'

/** Ab so vielen Zeichen fragt das Feld das Backend */
const MIN_CHARACTERS = 2

interface UsersPickerProps {
  id: string
  /** ein Treffer wurde angeklickt: der Aufrufer macht damit, was er will, das Feld leert sich */
  onPick: (users: Users) => void
  /** guids, die nicht mehr vorgeschlagen werden, z. B. weil sie schon in der Liste stehen */
  excludedGuids?: readonly string[]
  disabled?: boolean
  placeholder?: string
}

/**
 * Einen User per E-Mail finden (nach der FreigabenAuswahl und dem Message-Empfänger der Vorlage, User 2026-10-10): ab
 * zwei Zeichen fragt das Feld das Backend nach E-Mail-Schnipseln, die Treffer stehen als Knöpfe darunter, Name und
 * E-Mail, ein Klick wählt. Jede Eingabe ist eine neue Frage, ohne Ablage. Ohne Fachlogik: Was mit dem gewählten User
 * passiert, entscheidet der Aufrufer.
 */
export function UsersPicker({ id, onPick, excludedGuids = [], disabled = false, placeholder }: UsersPickerProps) {
  const { t } = useTranslation()
  const [text, setText] = useState('')
  const [suggestions, setSuggestions] = useState<Users[]>([])

  useEffect(() => {
    const needle = text.trim()
    if (needle.length < MIN_CHARACTERS) {
      return
    }
    let cancelled = false
    suggestUsers(needle)
      .then(({ data }) => {
        if (!cancelled) {
          setSuggestions(data)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setSuggestions([])
        }
      })
    return () => {
      cancelled = true
    }
  }, [text])

  function pick(users: Users) {
    onPick(users)
    setText('')
    setSuggestions([])
  }

  const shown = text.trim().length >= MIN_CHARACTERS ? suggestions.filter((users) => !excludedGuids.includes(users.guid)) : []

  return (
    <div className="d-flex flex-column gap-1">
      <input
        id={id}
        className="form-control form-control-sm"
        type="text"
        autoComplete="off"
        placeholder={placeholder ?? t('usersPicker.placeholder')}
        value={text}
        disabled={disabled}
        onChange={(event) => setText(event.target.value)}
      />
      {shown.length > 0 && (
        <div className="d-flex flex-wrap gap-1">
          {shown.map((users) => (
            <button
              key={users.guid}
              type="button"
              className="button-other"
              disabled={disabled}
              onClick={() => pick(users)}
            >
              {usersDisplayName(users)} <span className="text-muted small">{users.email}</span>
            </button>
          ))}
        </div>
      )}
      {text.trim().length >= MIN_CHARACTERS && shown.length === 0 && (
        <span className="small text-muted">{t('usersPicker.noMatch')}</span>
      )}
    </div>
  )
}
