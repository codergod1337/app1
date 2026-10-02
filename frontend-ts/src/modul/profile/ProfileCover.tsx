import { useEffect, useRef } from 'react'
import { seededRandom } from './profileDisplay.ts'

type Rgb = [number, number, number]

/** Kantenlänge eines Dreieck-Paars in Pixeln */
const CELL = 58

/** Liest eine Farbe (#rrggbb) aus einer CSS-Variablen. Fehlt sie oder ist sie kein Hex, gilt fallback. */
function readHexColor(element: Element, variable: string, fallback: Rgb): Rgb {
  const hex = getComputedStyle(element).getPropertyValue(variable).trim()
  const match = /^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(hex)
  return match ? [parseInt(match[1], 16), parseInt(match[2], 16), parseInt(match[3], 16)] : fallback
}

function mix(from: Rgb, to: Rgb, share: number): Rgb {
  return [0, 1, 2].map((i) => from[i] + (to[i] - from[i]) * share) as Rgb
}

/**
 * Zeichnet ein Low-Poly-Muster in den Farben des Themes (--profile-cover-from, --profile-cover-to,
 * --profile-cover-glow). Die Punkte kommen aus dem seed, dasselbe Profil sieht also immer gleich aus.
 */
function drawCover(canvas: HTMLCanvasElement, seed: string) {
  const box = canvas.getBoundingClientRect()
  const width = Math.max(1, Math.round(box.width))
  const height = Math.max(1, Math.round(box.height))
  const ratio = Math.min(window.devicePixelRatio || 1, 2)
  canvas.width = width * ratio
  canvas.height = height * ratio
  const context = canvas.getContext('2d')
  if (!context) {
    return
  }
  context.setTransform(ratio, 0, 0, ratio, 0, 0)

  const random = seededRandom(seed)
  const from = readHexColor(canvas, '--profile-cover-from', [30, 41, 59])
  const to = readHexColor(canvas, '--profile-cover-to', [49, 46, 129])
  const glow = readHexColor(canvas, '--profile-cover-glow', [99, 102, 241])
  const glowX = width * (0.55 + random() * 0.35)
  const glowY = height * (0.15 + random() * 0.5)
  const columns = Math.ceil(width / CELL) + 3
  const rows = Math.ceil(height / CELL) + 3

  const points: [number, number][][] = []
  for (let row = 0; row < rows; row++) {
    points.push([])
    for (let column = 0; column < columns; column++) {
      points[row].push([
        (column - 1) * CELL + (random() - 0.5) * CELL * 0.85,
        (row - 1) * CELL + (random() - 0.5) * CELL * 0.85,
      ])
    }
  }

  function triangle(a: [number, number], b: [number, number], c: [number, number]) {
    if (!context) {
      return
    }
    const centerX = (a[0] + b[0] + c[0]) / 3
    const centerY = (a[1] + b[1] + c[1]) / 3
    const share = Math.min(1, Math.max(0, (centerX / width) * 0.75 + (centerY / height) * 0.25))
    let color = mix(from, to, share)
    const distance = Math.hypot(centerX - glowX, centerY - glowY) / (width * 0.55)
    color = mix(color, glow, Math.pow(Math.max(0, 1 - distance), 2) * 0.6)
    const light = 1 + (random() - 0.5) * 0.22
    const fill = `rgb(${color.map((value) => Math.round(Math.min(255, value * light))).join(' ')})`
    context.beginPath()
    context.moveTo(a[0], a[1])
    context.lineTo(b[0], b[1])
    context.lineTo(c[0], c[1])
    context.closePath()
    context.fillStyle = fill
    // in derselben Farbe nachziehen, sonst bleiben zwischen den Dreiecken helle Haarlinien
    context.strokeStyle = fill
    context.lineWidth = 1
    context.fill()
    context.stroke()
    context.strokeStyle = 'rgb(255 255 255 / 0.05)'
    context.lineWidth = 0.6
    context.stroke()
  }

  for (let row = 0; row < rows - 1; row++) {
    for (let column = 0; column < columns - 1; column++) {
      triangle(points[row][column], points[row][column + 1], points[row + 1][column + 1])
      triangle(points[row][column], points[row + 1][column + 1], points[row + 1][column])
    }
  }

  // unten leicht abdunkeln, damit der Rand des Profilbilds ruhig steht
  const shade = context.createLinearGradient(0, height * 0.55, 0, height)
  shade.addColorStop(0, 'rgb(15 23 42 / 0)')
  shade.addColorStop(1, 'rgb(15 23 42 / 0.35)')
  context.fillStyle = shade
  context.fillRect(0, 0, width, height)
}

/** Das Titelbild des Profils, solange man keins hochladen kann. Zeichnet bei jeder Größenänderung neu. */
export function ProfileCover({ seed }: { seed: string }) {
  const canvas = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    const element = canvas.current
    if (!element) {
      return
    }
    let frame = 0
    const observer = new ResizeObserver(() => {
      cancelAnimationFrame(frame)
      frame = requestAnimationFrame(() => drawCover(element, seed))
    })
    observer.observe(element)
    return () => {
      observer.disconnect()
      cancelAnimationFrame(frame)
    }
  }, [seed])

  return <canvas ref={canvas} className="profile-cover-canvas" aria-hidden="true" />
}
