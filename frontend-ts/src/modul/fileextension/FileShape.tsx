import type { SymbolShape } from './FileExtensionCollection.ts'

/** Ecken und Drehung der Vielecke. Beim Quadrat und Achteck gedreht, damit eine Kante oben liegt. */
const POLYGONS: Record<Exclude<SymbolShape, 'CIRCLE'>, { corners: number; rotation: number }> = {
  SQUARE: { corners: 4, rotation: 45 },
  TRIANGLE: { corners: 3, rotation: 0 },
  DIAMOND: { corners: 4, rotation: 0 },
  PENTAGON: { corners: 5, rotation: 0 },
  HEXAGON: { corners: 6, rotation: 0 },
  HEPTAGON: { corners: 7, rotation: 0 },
  OCTAGON: { corners: 8, rotation: 22.5 },
}

/** Die Ecken eines regelmäßigen Vielecks im Feld 24 × 24, die erste oben (bei Drehung 0) */
function polygonPoints(corners: number, rotation: number): string {
  return Array.from({ length: corners }, (_, index) => {
    const angle = ((rotation + (360 / corners) * index) * Math.PI) / 180
    return `${(12 + 10 * Math.sin(angle)).toFixed(2)},${(12 - 10 * Math.cos(angle)).toFixed(2)}`
  }).join(' ')
}

/**
 * Die Form einer Gruppe von Endungen als Umriss in der Schriftfarbe. Später sitzt darin das Symbol der Dateiart.
 * null ist die Standardform: ein gestrichelter Kreis. Sieht in jedem Theme gleich aus (basic.css).
 */
export function FileShape({ shape }: { shape: SymbolShape | null }) {
  return (
    <svg className="file-shape" viewBox="0 0 24 24" aria-hidden="true">
      {shape === null && <circle cx="12" cy="12" r="10" strokeDasharray="3 3" />}
      {shape === 'CIRCLE' && <circle cx="12" cy="12" r="10" />}
      {shape !== null && shape !== 'CIRCLE' && (
        <polygon points={polygonPoints(POLYGONS[shape].corners, POLYGONS[shape].rotation)} />
      )}
    </svg>
  )
}
