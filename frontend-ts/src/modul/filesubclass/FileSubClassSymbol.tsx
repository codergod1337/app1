import { PackIcon } from '../../components/PackIcon.tsx'
import type { FileSubClass } from './FileSubClass.ts'

/**
 * Das Symbol einer Dateiart in ihrer Farbe, ohne Symbol die ersten zwei Buchstaben des keys. Die Form drumherum kommt
 * später von der Gruppe der Endung. Sieht in jedem Theme gleich aus (basic.css).
 */
export function FileSubClassSymbol({ fileSubClass }: { fileSubClass: Pick<FileSubClass, 'key' | 'symbol' | 'color'> }) {
  return (
    <span className="file-sub-class-symbol" style={{ color: fileSubClass.color ?? undefined }} aria-hidden="true">
      {fileSubClass.symbol ? <PackIcon symbol={fileSubClass.symbol} /> : fileSubClass.key.slice(0, 2)}
    </span>
  )
}
