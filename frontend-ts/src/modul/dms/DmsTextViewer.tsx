import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { LoadingProgress } from '../../components/LoadingProgress.tsx'
import { useDmsFileBlob } from './Dms.ts'

/** Über dieser Größe wird nur der Anfang gezeigt, der Browser soll nicht einfrieren (Vorlage) */
const MAX_CHARACTERS = 500_000

interface DmsTextViewerProps {
  id: string
  fileName: string
}

/**
 * Den Text der Datei lesen: UTF-8, und wenn die Bytes kein gültiges UTF-8 sind, Windows-1252. Ältere Textdateien aus
 * Windows kommen so, sonst stünden statt Umlauten Ersatzzeichen da.
 */
async function textOf(blob: Blob): Promise<string> {
  const bytes = await blob.arrayBuffer()
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes)
  } catch {
    return new TextDecoder('windows-1252').decode(bytes)
  }
}

/**
 * Text im Viewer (nach dem TextViewer der Vorlage), für txt, md, csv und log: roh, ohne Markdown-Darstellung, man will
 * sehen, was drinsteht, auch die Zeichen, die ein Renderer verschlucken würde. Lange Zeilen brechen um. Die Bytes
 * kommen nur in den Speicher (useDmsFileBlob), reine Anzeige.
 */
export function DmsTextViewer({ id, fileName }: DmsTextViewerProps) {
  const { t, i18n } = useTranslation()
  const { blob, receivedBytes, totalBytes, errorMessage } = useDmsFileBlob(id)
  const [loaded, setLoaded] = useState<{ blob: Blob; text: string } | null>(null)

  useEffect(() => {
    if (blob === null) {
      return
    }
    let active = true
    textOf(blob).then((text) => {
      if (active) {
        setLoaded({ blob, text })
      }
    })
    return () => {
      active = false
    }
  }, [blob])

  if (errorMessage !== null) {
    return <p className="text-danger mb-0">{errorMessage}</p>
  }
  const current = loaded !== null && loaded.blob === blob ? loaded : null
  if (current === null) {
    return <LoadingProgress fileName={fileName} receivedBytes={receivedBytes} totalBytes={totalBytes} />
  }
  const shortened = current.text.length > MAX_CHARACTERS
  return (
    <div className="viewer-text">
      {shortened && <p className="text-warning small">{t('dms.viewer.textShortened', { count: MAX_CHARACTERS, formatted: MAX_CHARACTERS.toLocaleString(i18n.language) })}</p>}
      <pre>{shortened ? current.text.slice(0, MAX_CHARACTERS) : current.text}</pre>
    </div>
  )
}
