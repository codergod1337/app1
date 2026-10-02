import { useEffect, useLayoutEffect, useRef, useState, type ChangeEvent, type ClipboardEvent } from 'react'
import { formatGuid, GUID_RULE_TEXT, isGuidPaste, isValidGuid, toGuidHex } from './guidRule.ts'

interface GuidInputProps {
  id: string
  /** die guid mit Bindestrichen, beim Tippen auch unvollständig */
  value: string
  onChange: (guid: string) => void
  required?: boolean
  disabled?: boolean
}

/** Stelle im formatierten Text hinter dem hexCount-ten Hex-Zeichen, Bindestriche zählen nicht mit */
function caretPosition(guidText: string, hexCount: number): number {
  let seenHex = 0
  for (let index = 0; index < guidText.length; index++) {
    if (seenHex === hexCount) {
      return index
    }
    if (guidText[index] !== '-') {
      seenHex++
    }
  }
  return guidText.length
}

/**
 * Eingabefeld für jede guid im System. Lässt beim Tippen nur 0–9 und a–f zu (A–F wird klein) und setzt die
 * Bindestriche selbst. Eingefügter Text mit anderen Zeichen wird komplett abgelehnt, damit aus Müll keine halbe
 * guid entsteht. Solange die guid unvollständig ist, zeigt das Feld die Regel an und das Formular lässt sich nicht
 * abschicken.
 */
export function GuidInput({ id, value, onChange, required = false, disabled = false }: GuidInputProps) {
  const input = useRef<HTMLInputElement>(null)
  // Wohin der Cursor nach dem Formatieren gehört, gezählt in Hex-Zeichen davor. null: nicht anfassen
  const caretHexCount = useRef<number | null>(null)
  const [pasteRejected, setPasteRejected] = useState(false)
  const invalid = value !== '' && !isValidGuid(value)

  // Der Browser hält das Formular auf, solange die guid ungültig ist
  useEffect(() => {
    input.current?.setCustomValidity(invalid ? GUID_RULE_TEXT : '')
  }, [invalid])

  // Nach dem Formatieren den Cursor wieder hinter dasselbe Hex-Zeichen setzen, sonst spränge er ans Ende
  useLayoutEffect(() => {
    const hexCount = caretHexCount.current
    if (input.current === null || hexCount === null) {
      return
    }
    caretHexCount.current = null
    const caret = caretPosition(value, hexCount)
    input.current.setSelectionRange(caret, caret)
  }, [value])

  function changeGuid(event: ChangeEvent<HTMLInputElement>) {
    const text = event.target.value
    const caret = event.target.selectionStart ?? text.length
    caretHexCount.current = toGuidHex(text.slice(0, caret)).length
    setPasteRejected(false)
    onChange(formatGuid(toGuidHex(text)))
  }

  function pasteGuid(event: ClipboardEvent<HTMLInputElement>) {
    if (!isGuidPaste(event.clipboardData.getData('text'))) {
      event.preventDefault()
      setPasteRejected(true)
    }
  }

  return (
    <>
      <input
        ref={input}
        id={id}
        className={invalid ? 'form-control font-monospace is-invalid' : 'form-control font-monospace'}
        required={required}
        disabled={disabled}
        // kein maxLength: eine eingefügte guid mit {} wäre sonst schon vor dem Filtern abgeschnitten
        autoComplete="off"
        spellCheck={false}
        value={value}
        onChange={changeGuid}
        onPaste={pasteGuid}
      />
      {invalid && <div className="invalid-feedback">{GUID_RULE_TEXT}</div>}
      {pasteRejected && <div className="form-text text-danger">kein gültiger guid-Text, nicht eingefügt</div>}
    </>
  )
}
