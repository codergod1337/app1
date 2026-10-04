import type { ReactNode } from 'react'
import { Link } from 'react-router'
import type { IconName } from '../branding/icons.ts'
import { Icon } from './Icon.tsx'

interface TileProps {
  icon: IconName
  title: string
  /** Ziel der Kachel, z. B. /admin */
  to: string
  /** ein kleines Zeichen oben rechts, außerhalb des Links, damit es nicht mitklickt: z. B. das i mit den AR des Moduls */
  cornerHint?: ReactNode
}

/** Eine Kachel: Symbol und Titel, ein Klick führt zum Ziel. */
export function Tile({ icon, title, to, cornerHint }: TileProps) {
  return (
    <div className="tile-frame">
      <Link className="tile" to={to}>
        <Icon name={icon} />
        <span>{title}</span>
      </Link>
      {cornerHint !== undefined && <span className="tile-corner">{cornerHint}</span>}
    </div>
  )
}
