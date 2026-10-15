import { useEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

/** Luft zwischen Anker und Hoverlay */
const GAP = 6

/** So lange darf die Maus vom Anker zum interaktiven Hoverlay unterwegs sein, bevor es schließt */
const INTERACTIVE_CLOSE_DELAY_MS = 180

interface HoverlayProps {
  /** Text oder Inhalt des Hoverlays. Leer: es wird nichts umhüllt. */
  text: ReactNode
  /** für Anker am rechten Rand: das Hoverlay wächst nach links statt aus dem Bild */
  alignRight?: boolean
  /**
   * Das Hoverlay bleibt offen, solange die Maus über Anker oder Hoverlay ist, und nimmt Klicks an: für Inhalte mit
   * Links und Knöpfen, z. B. eine Trefferliste (User 2026-10-13). Ohne die Angabe schließt es, sobald die Maus den
   * Anker verlässt.
   */
  interactive?: boolean
  children: ReactNode
}

/**
 * Das aufpoppende Feld beim Zeigen auf etwas. Ersetzt jedes title-Attribut: dessen Kästchen gehört dem Browser und
 * lässt sich nicht ans Theme anpassen. Das Aussehen (.hoverlay) bestimmt das Theme.
 *
 * Hängt per Portal an document.body, sonst schnitte es die ContentBox (overflow: hidden) ab. In einem Popup hängt
 * es am Popup, weil ein modales dialog über allem anderen liegt.
 */
export function Hoverlay({ text, alignRight = false, interactive = false, children }: HoverlayProps) {
  const anchor = useRef<HTMLSpanElement>(null)
  const closeTimer = useRef<number | null>(null)
  const [position, setPosition] = useState<{ top: number; left?: number; right?: number } | null>(null)
  const [container, setContainer] = useState<Element>(document.body)

  // ein laufender Schließ-Timer darf nach dem Abbau nichts mehr setzen
  useEffect(
    () => () => {
      if (closeTimer.current !== null) {
        window.clearTimeout(closeTimer.current)
      }
    },
    [],
  )

  if (text === null || text === undefined || text === '') {
    return children
  }

  function cancelClose() {
    if (closeTimer.current !== null) {
      window.clearTimeout(closeTimer.current)
      closeTimer.current = null
    }
  }

  function show() {
    cancelClose()
    const box = anchor.current?.getBoundingClientRect()
    if (!box) {
      return
    }
    // In einem Popup (modales dialog) liegt alles darüber in der obersten Ebene: dorthin, sonst bliebe es verdeckt
    setContainer(anchor.current?.closest('dialog') ?? document.body)
    // unterhalb des Ankers, nach oben wäre es in der ersten Zeile aus dem Bild
    setPosition(
      alignRight
        ? { top: box.bottom + GAP, right: window.innerWidth - box.right }
        : { top: box.bottom + GAP, left: box.left },
    )
  }

  /** Schließen, beim interaktiven Hoverlay erst nach der Frist, damit die Maus hinüberwandern kann */
  function hide() {
    if (!interactive) {
      setPosition(null)
      return
    }
    cancelClose()
    closeTimer.current = window.setTimeout(() => {
      closeTimer.current = null
      setPosition(null)
    }, INTERACTIVE_CLOSE_DELAY_MS)
  }

  return (
    <>
      <span ref={anchor} className="hoverlay-anchor" onMouseEnter={show} onMouseLeave={hide} onFocus={show} onBlur={hide}>
        {children}
      </span>
      {position &&
        createPortal(
          <span
            className={interactive ? 'hoverlay hoverlay-interactive' : 'hoverlay'}
            role="tooltip"
            // ein interaktives endet am unteren Fensterrand und rollt, sonst wäre langer Inhalt unten abgeschnitten
            style={interactive ? { ...position, maxHeight: `calc(100vh - ${position.top + GAP}px)` } : position}
            onMouseEnter={interactive ? cancelClose : undefined}
            onMouseLeave={interactive ? hide : undefined}
          >
            {text}
          </span>,
          container,
        )}
    </>
  )
}
