import { useRef, type MouseEvent, type PointerEvent } from 'react'

/**
 * Verschieben mit gedrückter rechter Maustaste, für Flächen, die breiter und höher als das Fenster sind (Matrix).
 * Der Container scrollt in sich selbst. Das Kontextmenü ist dort aus, sonst ginge es beim Loslassen auf.
 *
 *   const { ref, handlers } = usePanScroll<HTMLDivElement>()
 *   <div ref={ref} {...handlers}>…</div>
 */
export function usePanScroll<T extends HTMLElement>() {
  const ref = useRef<T>(null)
  const start = useRef<{ x: number; y: number; scrollLeft: number; scrollTop: number } | null>(null)

  function onPointerDown(event: PointerEvent<T>) {
    const container = ref.current
    if (event.button !== 2 || !container) {
      return
    }
    start.current = { x: event.clientX, y: event.clientY, scrollLeft: container.scrollLeft, scrollTop: container.scrollTop }
    container.setPointerCapture(event.pointerId)
    container.classList.add('panning')
  }

  function onPointerMove(event: PointerEvent<T>) {
    const container = ref.current
    if (!start.current || !container) {
      return
    }
    container.scrollLeft = start.current.scrollLeft - (event.clientX - start.current.x)
    container.scrollTop = start.current.scrollTop - (event.clientY - start.current.y)
  }

  function stop(event: PointerEvent<T>) {
    const container = ref.current
    if (!start.current || !container) {
      return
    }
    start.current = null
    container.releasePointerCapture(event.pointerId)
    container.classList.remove('panning')
  }

  return {
    ref,
    handlers: {
      onPointerDown,
      onPointerMove,
      onPointerUp: stop,
      onPointerCancel: stop,
      onContextMenu: (event: MouseEvent<T>) => event.preventDefault(),
    },
  }
}
