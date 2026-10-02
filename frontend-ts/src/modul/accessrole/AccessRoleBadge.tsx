import { useContext, type CSSProperties } from 'react'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { MasterDataContext } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { PackIcon } from '../../components/PackIcon.tsx'
import type { AccessRoleBadgeData } from './AccessRole.ts'

export type AccessRoleBadgeSize = 'full' | 'compact'

/** rect: AccessRole (abgerundetes Rechteck), pill: AccessRoleCollection */
export type AccessRoleBadgeShape = 'rect' | 'pill'

/** Kurzform ohne Symbol: die Anfangsbuchstaben der ersten beiden Teile des Keys, z. B. FILE_READ → FR, ADMIN → AD */
function shortKey(key: string): string {
  // bei einer ARC zählt das Präfix ARC_ nicht mit
  const parts = key.replace(/^ARC_/, '').split('_')
  return parts.length > 1 ? parts[0][0] + parts[1][0] : parts[0].slice(0, 2)
}

interface AccessRoleBadgeProps {
  /** eine AR, oder eine ARC: Dann stehen ihre AR (accessRoleKeys) im Hoverlay */
  badge: AccessRoleBadgeData & { accessRoleKeys?: string[] | null }
  size: AccessRoleBadgeSize
  /** Standard rect (AccessRole), pill für die AccessRoleCollection */
  shape?: AccessRoleBadgeShape
  /** false: ohne eigenes Hoverlay, z. B. für die AR im Hoverlay einer ARC. Ein Hoverlay im Hoverlay geht nicht. */
  withHoverlay?: boolean
}

/**
 * Eine AccessRole als Badge: abgerundetes Rechteck, die Ecken bleiben als Ecken erkennbar. Die AccessRoleCollection
 * nutzt dieselbe Badge in Pillenform (shape="pill"). Das Aussehen steht in basic.css und ist in jedem Theme gleich,
 * die Farben kommen als CSS-Variablen.
 *
 * full: nur der Anzeigename (ohne Symbol), im Hoverlay die Beschreibung.
 * compact: nur das Symbol (ohne Symbol die Kurzform des Keys), im Hoverlay Anzeigename und Beschreibung.
 * Bei einer ARC stehen im Hoverlay unter der Beschreibung alle AR, die sie verleiht, als Kompakt-Badges.
 */
export function AccessRoleBadge({ badge, size, shape = 'rect', withHoverlay = true }: AccessRoleBadgeProps) {
  // ohne Stammdaten (außerhalb des Providers) eben ohne die AR im Hoverlay
  const accessRoles = useContext(MasterDataContext)?.accessRoles ?? null
  const name = translate(badge.displayName)
  const description = translate(badge.description)

  // null-Farben werden gar nicht gesetzt, dann greift der Standard aus basic.css
  const style = {
    '--badge-text': badge.badgeTextColor ?? undefined,
    '--badge-text-shadow': badge.badgeTextShadowColor ?? undefined,
    '--badge-gradient-start': badge.badgeGradientStart ?? undefined,
    '--badge-gradient-end': badge.badgeGradientEnd ?? undefined,
    '--badge-border': badge.badgeBorderColor ?? undefined,
    '--badge-shadow': badge.badgeShadowColor ?? undefined,
    opacity: badge.badgeOpacity !== null ? badge.badgeOpacity / 100 : undefined,
  } as CSSProperties

  const classNames = ['access-role-badge', `access-role-badge-${size}`]
  if (shape === 'pill') {
    classNames.push('access-role-badge-pill')
  }
  if (badge.badgeGradient !== null) {
    classNames.push(`access-role-badge-gradient-${badge.badgeGradient.toLowerCase()}`)
  }
  // Präfix, damit ein Style-Name nie mit anderen Klassen zusammenstößt
  for (const badgeStyle of badge.badgeStyles ?? []) {
    classNames.push(`access-role-badge-style-${badgeStyle}`)
  }

  const badgeElement = (
    <span className={classNames.join(' ')} style={style}>
      {size === 'full' && <span>{name}</span>}
      {size === 'compact' && (badge.symbol ? <PackIcon symbol={badge.symbol} /> : <span>{shortKey(badge.key)}</span>)}
    </span>
  )

  if (!withHoverlay) {
    return badgeElement
  }

  // Bei einer ARC: die AR, die sie verleiht, in der Reihenfolge der AR-Liste
  const grantedAccessRoles =
    shape === 'pill' && accessRoles !== null && badge.accessRoleKeys
      ? accessRoles.filter((accessRole) => badge.accessRoleKeys?.includes(accessRole.key))
      : []

  // Text über den AR: bei compact der Name, sonst die Beschreibung. Dann trennt sie eine Linie.
  const hasText = (size === 'compact' && name !== '') || description !== ''

  const hoverlayText = (
    <>
      {size === 'compact' && name}
      {size === 'compact' && description && <br />}
      {description}
      {grantedAccessRoles.length > 0 && (
        <span className={hasText ? 'hoverlay-badges hoverlay-badges-separated' : 'hoverlay-badges'}>
          {grantedAccessRoles.map((accessRole) => (
            <AccessRoleBadge key={accessRole.key} badge={accessRole} size="compact" withHoverlay={false} />
          ))}
        </span>
      )}
    </>
  )
  // Ohne Beschreibung und ohne AR gibt es bei full nichts zu zeigen
  const hasHoverlay = size === 'compact' || description !== '' || grantedAccessRoles.length > 0

  return <Hoverlay text={hasHoverlay ? hoverlayText : null}>{badgeElement}</Hoverlay>
}
