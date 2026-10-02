import { Hoverlay } from './Hoverlay.tsx'

/** Ein Pinsel: der Wert, den er malt */
export interface MatrixBrush<V> {
  value: V
  /** kurz, steht auf dem Knopf, z. B. R */
  label: string
  /** im Hoverlay, z. B. „Pinsel: lesen“ */
  hint: string
}

interface MatrixBrushesProps<V> {
  brushes: MatrixBrush<V>[]
  value: V
  onChange: (value: V) => void
}

/** Die Pinsel einer Matrix, oben in der Ecke (cornerContent). Genau einer ist gewählt. */
export function MatrixBrushes<V>({ brushes, value, onChange }: MatrixBrushesProps<V>) {
  return (
    <div className="matrix-brushes" role="radiogroup" aria-label="Pinsel">
      {brushes.map((brush) => (
        <Hoverlay key={brush.label} text={brush.hint}>
          <button
            type="button"
            role="radio"
            aria-checked={Object.is(brush.value, value)}
            aria-label={brush.hint}
            className={Object.is(brush.value, value) ? 'matrix-brush active' : 'matrix-brush'}
            onClick={() => onChange(brush.value)}
          >
            {brush.label}
          </button>
        </Hoverlay>
      ))}
    </div>
  )
}
