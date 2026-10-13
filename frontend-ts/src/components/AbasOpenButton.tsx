import { useTranslation } from 'react-i18next'
import { Hoverlay } from './Hoverlay.tsx'

interface AbasOpenButtonProps {
  /** die ABAS-Objekt-id, z. B. (87448,2,0); ohne sie erscheint nichts, ein Knopf, der nichts öffnet, gehört nicht auf die Seite */
  sternzeit: string
  /** was im Hoverlay steht, z. B. „Artikel 11500123“; ohne Angabe die Sternzeit selbst */
  hint?: string
  /** nur die Bildmarke, ohne Beschriftung: in Tabellenzeilen und hinter einem Namen im Kopf */
  compact?: boolean
  className?: string
}

/**
 * In ABAS öffnen, überall derselbe Knopf (nach dem AbasKnopf der Vorlage, User 2026-10-11): jede Entität mit einer
 * Sternzeit lässt sich so in der ABAS-GUI öffnen, Artikel, Kontakte, Belege. Der Link ist das Schema abasurl:<sternzeit>,
 * ohne Schrägstriche, weil Chrome sonst einen anhängt und ABAS die Klammer als Hostnamen liest; den Registry-Eintrag
 * dafür hat der Arbeitsplatz. Groß steht da „öffnen“, beim Darüberfahren die Sternzeit, der Wechsel wächst wie beim
 * NewEntityButton; schmal nur die Bildmarke, was er öffnet, sagt das Hoverlay. Die Marke behält die Farben von abas,
 * deshalb ein heller Knopf. Das Aussehen gestaltet das Theme (abas-open-button*), basic.css enthält nichts dafür.
 */
export function AbasOpenButton({ sternzeit, hint, compact = false, className = '' }: AbasOpenButtonProps) {
  const { t } = useTranslation()
  if (sternzeit === '') {
    return null
  }
  const label = t('common.openInAbasLabel', { sternzeit })
  const hoverlayText = hint ?? t('common.sternzeit', { sternzeit })

  if (compact) {
    return (
      <Hoverlay text={hoverlayText}>
        <a href={`abasurl:${sternzeit}`} className={`abas-open-button abas-open-button-compact ${className}`.trim()} aria-label={label}>
          <img src="/abas-zeichen.svg" alt="" className="abas-open-button-mark" />
        </a>
      </Hoverlay>
    )
  }

  return (
    <Hoverlay text={hoverlayText}>
      <a href={`abasurl:${sternzeit}`} className={`abas-open-button ${className}`.trim()} aria-label={label}>
        <img src="/abas-zeichen.svg" alt="" className="abas-open-button-mark" />
        <span className="abas-open-button-text abas-open-button-text-open" aria-hidden="true">
          {t('common.openInAbas')}
        </span>
        <span className="abas-open-button-text abas-open-button-text-hover" aria-hidden="true">
          {t('common.sternzeit', { sternzeit })}
        </span>
      </a>
    </Hoverlay>
  )
}
