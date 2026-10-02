import { useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

/** Luft zwischen Anker und Hoverlay */
const GAP = 6

interface HoverlayProps {
  /** Text oder Inhalt des Hoverlays. Leer: es wird nichts umhüllt. */
  text: ReactNode
  /** für Anker am rechten Rand: das Hoverlay wächst nach links statt aus dem Bild */
  alignRight?: boolean
  children: ReactNode
}

/**
 * Das aufpoppende Feld beim Zeigen auf etwas. Ersetzt jedes title-Attribut: dessen Kästchen gehört dem Browser und
 * lässt sich nicht ans Theme anpassen. Das Aussehen (.hoverlay) bestimmt das Theme.
 *
 * Hängt per Portal an document.body, sonst schnitte es die ContentBox (overflow: hidden) ab. In einem Popup hängt
 * es am Popup, weil ein modales dialog über allem anderen liegt.
 */
export function Hoverlay({ text, alignRight = false, children }: HoverlayProps) {
  const anchor = useRef<HTMLSpanElement>(null)
  const [position, setPosition] = useState<{ top: number; left?: number; right?: number } | null>(null)
  const [container, setContainer] = useState<Element>(document.body)

  if (text === null || text === undefined || text === '') {
    return children
  }

  function show() {
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

  return (
    <>
      <span
        ref={anchor}
        className="hoverlay-anchor"
        onMouseEnter={show}
        onMouseLeave={() => setPosition(null)}
        onFocus={show}
        onBlur={() => setPosition(null)}
      >
        {children}
      </span>
      {position &&
        createPortal(
          <span className="hoverlay" role="tooltip" style={position}>
            {text}
          </span>,
          container,
        )}
    </>
  )
}
