import type { AccessRoleBadgeData } from './AccessRole.ts'
import { AccessRoleBadge, type AccessRoleBadgeShape } from './AccessRoleBadge.tsx'

interface BadgeToggleListProps {
  /** alle Badges, die gewählt werden können */
  badges: AccessRoleBadgeData[]
  selectedKeys: string[]
  onChange: (selectedKeys: string[]) => void
  shape: AccessRoleBadgeShape
  disabled?: boolean
}

/** Badges zum Anklicken: gewählte sind voll sichtbar, die anderen blass. Die Reihenfolge ist die der Badges. */
export function BadgeToggleList({ badges, selectedKeys, onChange, shape, disabled = false }: BadgeToggleListProps) {
  function toggle(key: string) {
    const selected = selectedKeys.includes(key)
    // in der Reihenfolge der Badges halten, nicht in der Klick-Reihenfolge
    onChange(
      badges
        .map((badge) => badge.key)
        .filter((candidate) => (candidate === key ? !selected : selectedKeys.includes(candidate))),
    )
  }

  if (badges.length === 0) {
    return <span className="text-body-secondary">keine vorhanden</span>
  }

  return (
    <div className="badge-list">
      {badges.map((badge) => (
        <button
          key={badge.key}
          type="button"
          className={selectedKeys.includes(badge.key) ? 'badge-toggle active' : 'badge-toggle'}
          aria-pressed={selectedKeys.includes(badge.key)}
          disabled={disabled}
          onClick={() => toggle(badge.key)}
        >
          <AccessRoleBadge badge={badge} size="full" shape={shape} />
        </button>
      ))}
    </div>
  )
}
