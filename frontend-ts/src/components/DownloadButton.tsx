import { Hoverlay } from './Hoverlay.tsx'
import { Icon } from './Icon.tsx'

/**
 * Pfeil zum Herunterladen: ein Link mit download, kein Knopf, damit der Browser die Datei selbst holt. Aussehen im Theme,
 * immer in Türkistönen.
 */
export function DownloadButton({ label, href }: { label: string; href: string }) {
  return (
    <Hoverlay text={label}>
      <a className="download-button" href={href} download aria-label={label}>
        <Icon name="download" />
      </a>
    </Hoverlay>
  )
}
