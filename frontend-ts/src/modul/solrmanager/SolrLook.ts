import type { CSSProperties } from 'react'
import type { PackSymbol } from '../../components/PackSymbol.ts'
import type { BadgeGradient } from '../accessrole/AccessRole.ts'

/**
 * Das Aussehen eines Kerns (Emblem), wie das Backend es liefert (SolrLook.java): ein Verlauf aus drei Farben, Schrift,
 * Schriftschatten, Schatten, Verlaufsart und Symbol. Farben immer Hex, null: Standard aus basic.css. Hooks haben kein
 * Aussehen in den Daten, ihr Chip-Look steht fest in basic.css.
 */
export interface SolrLook {
  /** Hauptfarbe: bei einfarbig die Fläche, im Verlauf die Mitte */
  mainColor: string | null
  /** Verlauf, Anfang. null: die Hauptfarbe */
  gradientStart: string | null
  /** Verlauf, Ende. null: die Hauptfarbe */
  gradientEnd: string | null
  /** Schrift und Symbol */
  textColor: string | null
  /** Schatten hinter der Schrift, für Kontrast auf jedem Hintergrund */
  textShadowColor: string | null
  /** Schatten um das Element */
  shadowColor: string | null
  /** Verlaufsart, dieselben Arten wie bei den Badges */
  gradient: BadgeGradient | null
  symbol: PackSymbol | null
}

/** Alles auf Standard */
export const EMPTY_SOLR_LOOK: SolrLook = {
  mainColor: null,
  gradientStart: null,
  gradientEnd: null,
  textColor: null,
  textShadowColor: null,
  shadowColor: null,
  gradient: null,
  symbol: null,
}

/**
 * Die Farben als CSS-Variablen für ein Element mit der Klasse solr-look. null-Farben werden gar nicht gesetzt, dann
 * greift der Standard aus basic.css.
 */
export function solrLookStyle(look: SolrLook): CSSProperties {
  return {
    '--look-main': look.mainColor ?? undefined,
    '--look-gradient-start': look.gradientStart ?? undefined,
    '--look-gradient-end': look.gradientEnd ?? undefined,
    '--look-text': look.textColor ?? undefined,
    '--look-text-shadow': look.textShadowColor ?? undefined,
    '--look-shadow': look.shadowColor ?? undefined,
  } as CSSProperties
}

/** Die Klassen für Verlauf und Farben: solr-look und, falls gewählt, die Verlaufsart */
export function solrLookClassNames(look: SolrLook): string[] {
  const classNames = ['solr-look']
  if (look.gradient !== null) {
    classNames.push(`solr-look-gradient-${look.gradient.toLowerCase()}`)
  }
  return classNames
}
