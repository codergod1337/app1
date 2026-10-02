import type { AccessRoleBadgeData } from './AccessRole.ts'
import { AccessRoleBadge, type AccessRoleBadgeShape } from './AccessRoleBadge.tsx'

interface BadgeListProps {
  /** die keys in der gewünschten Reihenfolge */
  keys: string[] | null
  /** alle bekannten Badges, daraus werden die keys aufgelöst */
  badges: AccessRoleBadgeData[]
  shape: AccessRoleBadgeShape
}

/** Kompakte Badges zu einer Liste von keys. Ein key ohne Badge (z. B. gelöscht) erscheint als Text. */
export function BadgeList({ keys, badges, shape }: BadgeListProps) {
  return (
    <span className="badge-list">
      {(keys ?? []).map((key) => {
        const badge = badges.find((candidate) => candidate.key === key)
        return badge ? (
          <AccessRoleBadge key={key} badge={badge} size="compact" shape={shape} />
        ) : (
          <code key={key}>{key}</code>
        )
      })}
    </span>
  )
}
