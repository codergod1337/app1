import { useState, type ClipboardEvent, type KeyboardEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { DeleteButton } from './DeleteButton.tsx'

interface ValuesInputProps {
  values: readonly string[]
  onChange: (values: string[]) => void
  /** Beispielwert im leeren Feld, bestimmt auch die Breite des Feldes */
  placeholder?: string
  /** loggt zusätzlich bei diesem Zeichen ein, z. B. ; */
  separator?: string
  ariaLabel?: string
  disabled?: boolean
  /**
   * Wie ein eingeloggter Wert aussieht. Fehlt es, ein schlichtes Kästchen mit Mülleimer. Der Aufrufer zeichnet z. B.
   * die Badge seines Moduls und setzt remove an seinen Mülleimer.
   */
  renderValue?: (value: string, remove: () => void) => ReactNode
}

/**
 * Die Eingabe einer Werte-Liste, nach WerteInput der Vorlage: getippt wird in ein schmales Feld, mit Leertaste, Tab,
 * Enter oder dem Trennzeichen wird der Wert eingeloggt, der Mülleimer entfernt ihn wieder. Bewusst dumm: Liste und
 * Setter kommen als Props.
 *
 * Auf Menge ausgelegt: eingefügter Text (aus Tabelle, Liste, Mail) wird an Leerzeichen, Zeilenumbruch, Semikolon und
 * Komma zerlegt und in einem Rutsch eingeloggt. Enter loggt ein, statt ein umgebendes Formular abzuschicken, beim
 * Verlassen des Feldes wird ein angefangener Rest übernommen, so geht beim direkten Klick auf Speichern nichts
 * verloren. Doppelte Werte werden still verschluckt. Die Liste rollt ab einigen Zeilen in sich (values-list).
 */
export function ValuesInput({
  values,
  onChange,
  placeholder = '',
  separator,
  ariaLabel,
  disabled = false,
  renderValue,
}: ValuesInputProps) {
  const { t } = useTranslation()
  const [input, setInput] = useState('')

  /** Hängt alles an, was neu und nicht leer ist: eine Liste, ein onChange */
  function add(newValues: string[]) {
    const clean: string[] = []
    for (const raw of newValues) {
      const value = raw.trim()
      if (value !== '' && !values.includes(value) && !clean.includes(value)) {
        clean.push(value)
      }
    }
    if (clean.length > 0) {
      onChange([...values, ...clean])
    }
  }

  function commit() {
    if (input.trim() === '') {
      return
    }
    add([input])
    setInput('')
  }

  function commitOnKey(event: KeyboardEvent<HTMLInputElement>) {
    const commits = event.key === ' ' || event.key === 'Tab' || event.key === 'Enter' || event.key === separator
    if (!commits) {
      return
    }
    // ein leeres Tab bleibt normale Fokus-Navigation
    if (event.key === 'Tab' && input.trim() === '') {
      return
    }
    event.preventDefault()
    commit()
  }

  /** Eine eingefügte Liste zerlegen, ein einzelner Wert bleibt normales Einfügen */
  function splitPaste(event: ClipboardEvent<HTMLInputElement>) {
    const text = event.clipboardData.getData('text')
    const parts = text.split(/[\s;,]+/).filter((part) => part.trim() !== '')
    if (parts.length < 2) {
      return
    }
    event.preventDefault()
    add(parts)
    setInput('')
  }

  function remove(value: string) {
    onChange(values.filter((candidate) => candidate !== value))
  }

  return (
    <span className="values-input">
      {values.length > 0 && (
        <span className="values-list">
          {values.map((value) =>
            renderValue ? (
              <span key={value}>{renderValue(value, () => remove(value))}</span>
            ) : (
              <span key={value} className="values-input-value">
                {value}
                <DeleteButton label={t('common.removeValue', { value })} disabled={disabled} onClick={() => remove(value)} />
              </span>
            ),
          )}
        </span>
      )}
      {/* Die Breite richtet sich nach dem Platzhalter, nicht nach dem Platz: ein Feld bis zum Rand verspricht mehr Eingabe */}
      <input
        className="form-control form-control-sm w-auto"
        size={Math.max(14, placeholder.length + 6)}
        value={input}
        disabled={disabled}
        placeholder={placeholder}
        aria-label={ariaLabel ?? t('common.valuesInputAria')}
        onChange={(event) => setInput(event.target.value)}
        onKeyDown={commitOnKey}
        onPaste={splitPaste}
        onBlur={commit}
      />
    </span>
  )
}
