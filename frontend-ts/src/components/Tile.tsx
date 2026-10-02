import { Link } from 'react-router'
import type { IconName } from '../branding/icons.ts'
import { Icon } from './Icon.tsx'

interface TileProps {
  icon: IconName
  title: string
  /** Ziel der Kachel, z. B. /admin */
  to: string
}

/** Eine Kachel: Symbol und Titel, ein Klick führt zum Ziel. */
export function Tile({ icon, title, to }: TileProps) {
  return (
    <Link className="tile" to={to}>
      <Icon name={icon} />
      <span>{title}</span>
    </Link>
  )
}
