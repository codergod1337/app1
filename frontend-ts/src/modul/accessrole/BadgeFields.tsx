import { useState } from 'react'
import { ColorField } from '../../components/ColorField.tsx'
import { SymbolPicker } from '../../components/SymbolPicker.tsx'
import { BADGE_GRADIENTS, type AccessRoleBadgeData, type BadgeGradient } from './AccessRole.ts'
import { AccessRoleBadge, type AccessRoleBadgeShape } from './AccessRoleBadge.tsx'
import type { BadgeStyleData } from './badgeStyle.ts'

/** Die Farbfelder der Badge mit ihrer Aufgabe */
const BADGE_COLOR_FIELDS = [
  { field: 'badgeTextColor', label: 'Schrift und Symbol' },
  { field: 'badgeTextShadowColor', label: 'Schatten hinter der Schrift (Kontrast)' },
  { field: 'badgeGradientStart', label: 'Verlauf, Anfang' },
  { field: 'badgeGradientEnd', label: 'Verlauf, Ende' },
  { field: 'badgeBorderColor', label: 'Rand' },
  { field: 'badgeShadowColor', label: 'Schatten' },
] as const

interface BadgeFieldsProps {
  /** Präfix der Element-ids, z. B. access-role */
  idPrefix: string
  /** die ganze Badge, damit die Vorschau auch Name und Beschreibung zeigt */
  badge: AccessRoleBadgeData
  shape: AccessRoleBadgeShape
  disabled: boolean
  /** nur die geänderten Felder, z. B. { badgeGradient: 'BEVEL' } */
  onChange: (changes: Partial<BadgeStyleData>) => void
}

/**
 * Die Eingabefelder für das Aussehen einer Badge mit Live-Vorschau, gleich bei AccessRole (Rechteck) und
 * AccessRoleCollection (Pille). Gehört in ein Bootstrap-row.
 */
export function BadgeFields({ idPrefix, badge, shape, disabled, onChange }: BadgeFieldsProps) {
  const [badgeStylesText, setBadgeStylesText] = useState(badge.badgeStyles?.join(', ') ?? '')

  function changeBadgeStyles(text: string) {
    setBadgeStylesText(text)
    // durch Komma getrennt, leer: null
    const styles = text
      .split(',')
      .map((style) => style.trim())
      .filter((style) => style !== '')
    onChange({ badgeStyles: styles.length > 0 ? styles : null })
  }

  return (
    <>
      <div className="col-12">
        <h3 className="h6 mt-2 mb-0">Badge</h3>
      </div>

      {/* Live-Vorschau: ändert sich mit jeder Eingabe */}
      <div className="col-12">
        <div className="badge-preview">
          <AccessRoleBadge badge={badge} size="full" shape={shape} />
          <AccessRoleBadge badge={badge} size="compact" shape={shape} />
        </div>
      </div>

      {BADGE_COLOR_FIELDS.map(({ field, label }) => (
        <div key={field} className="col-md-6">
          <ColorField
            id={`${idPrefix}-${field}`}
            label={label}
            value={badge[field]}
            disabled={disabled}
            onChange={(color) => onChange({ [field]: color })}
          />
        </div>
      ))}

      <div className="col-md-6">
        <label className="form-label" htmlFor={`${idPrefix}-gradient`}>
          Verlaufsart
        </label>
        <select
          id={`${idPrefix}-gradient`}
          className="form-select"
          value={badge.badgeGradient ?? ''}
          onChange={(event) => onChange({ badgeGradient: (event.target.value || null) as BadgeGradient | null })}
        >
          <option value="">Standard</option>
          {BADGE_GRADIENTS.map(({ gradient, label }) => (
            <option key={gradient} value={gradient}>
              {label}
            </option>
          ))}
        </select>
      </div>
      <div className="col-md-6">
        <label className="form-label" htmlFor={`${idPrefix}-opacity`}>
          Deckkraft: {badge.badgeOpacity ?? 100} %
        </label>
        <input
          id={`${idPrefix}-opacity`}
          className="form-range"
          type="range"
          min={25}
          max={100}
          step={5}
          value={badge.badgeOpacity ?? 100}
          onChange={(event) => {
            const opacity = Number(event.target.value)
            onChange({ badgeOpacity: opacity === 100 ? null : opacity })
          }}
        />
      </div>
      <div className="col-12">
        <label className="form-label" htmlFor={`${idPrefix}-badge-styles`}>
          Badge-Styles
        </label>
        <input
          id={`${idPrefix}-badge-styles`}
          className="form-control"
          placeholder="durch Komma getrennt, z. B. screws, shine"
          value={badgeStylesText}
          onChange={(event) => changeBadgeStyles(event.target.value)}
        />
      </div>
      <div className="col-12">
        <label className="form-label">Symbol</label>
        <SymbolPicker value={badge.symbol} disabled={disabled} onChange={(symbol) => onChange({ symbol })} />
      </div>
    </>
  )
}
