import { SYMBOL_PACKS, type PackSymbol } from '../../components/PackSymbol.ts'
import { BADGE_GRADIENTS, type AccessRoleBadgeData } from './AccessRole.ts'

/** Die Felder, die das Aussehen einer Badge bestimmen, gleich bei AccessRole und AccessRoleCollection. */
export type BadgeStyleData = Pick<
  AccessRoleBadgeData,
  | 'badgeTextColor'
  | 'badgeTextShadowColor'
  | 'badgeGradientStart'
  | 'badgeGradientEnd'
  | 'badgeBorderColor'
  | 'badgeShadowColor'
  | 'badgeGradient'
  | 'badgeStyles'
  | 'symbol'
  | 'badgeOpacity'
>

/** Alles auf Standard (aus basic.css, in jedem Theme gleich) */
export const EMPTY_BADGE_STYLE: BadgeStyleData = {
  badgeTextColor: null,
  badgeTextShadowColor: null,
  badgeGradientStart: null,
  badgeGradientEnd: null,
  badgeBorderColor: null,
  badgeShadowColor: null,
  badgeGradient: null,
  badgeStyles: null,
  symbol: null,
  badgeOpacity: null,
}

/** Zufällige Farbe als Hex (#rrggbb) */
function randomColor(): string {
  return `#${Math.floor(Math.random() * 0x1000000)
    .toString(16)
    .padStart(6, '0')}`
}

function randomItem<T>(items: readonly T[]): T {
  return items[Math.floor(Math.random() * items.length)]
}

/** Zufällige Farben und Verlaufsart, damit man zum Testen schnell etwas anlegen kann. Das Symbol kommt über randomSymbol. */
export function randomBadgeStyle(): BadgeStyleData {
  return {
    ...EMPTY_BADGE_STYLE,
    badgeTextColor: randomColor(),
    badgeTextShadowColor: randomColor(),
    badgeGradientStart: randomColor(),
    badgeGradientEnd: randomColor(),
    badgeBorderColor: randomColor(),
    badgeShadowColor: randomColor(),
    badgeGradient: randomItem(BADGE_GRADIENTS).gradient,
  }
}

/** Zufälliges Symbol aus allen Paketen. Die Symbolliste ist groß und wird erst hier nachgeladen. */
export async function randomSymbol(): Promise<PackSymbol> {
  const { SYMBOL_CATALOG } = await import('../../components/symbolCatalog.ts')
  const pack = randomItem(SYMBOL_PACKS).pack
  return { pack, id: randomItem(SYMBOL_CATALOG[pack]) }
}
