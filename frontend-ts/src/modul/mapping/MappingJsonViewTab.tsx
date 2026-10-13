import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ContentBox } from '../../components/ContentBox.tsx'
import { FileDropzone } from '../../components/FileDropzone.tsx'
import { useMappingDraft } from './MappingDraft.ts'
import { MappingDraftBox } from './MappingDraftBox.tsx'
import { MappingJsonTreeBox } from './MappingJsonTreeBox.tsx'
import { fullPathOf } from './ResponseMapping.ts'

/** Ein geladenes JSON mit seiner Herkunft */
interface LoadedJson {
  /** Dateiname oder „eingefügt“ */
  sourceName: string
  sizeBytes: number
  value: unknown
}

/**
 * Der Tab „JSON-Ansicht“: eine Datei ablegen oder Text einfügen, dann steht das JSON als Baum da (MappingJsonTreeBox).
 * Das Laden beginnt den Mapping-Entwurf, jeder Klick in den Baum wird ein Eintrag, „Ignorieren“ räumt den Baum auf,
 * Speichern legt das Gesamt-Mapping an. Gebaut gegen die ABAS-Antworten mit 735 Kopffeldern und hundert
 * Tabellenzeilen, aber ohne Wissen über sie.
 */
export function MappingJsonViewTab() {
  const { t, i18n } = useTranslation()
  const [pastedText, setPastedText] = useState('')
  const [loaded, setLoaded] = useState<LoadedJson | null>(null)
  const [parseError, setParseError] = useState<string | null>(null)
  /** zählt jedes Laden, damit die Baum-Box mit Suche und Auswahl neu startet */
  const [loadCount, setLoadCount] = useState(0)
  const mappingDraft = useMappingDraft()
  /** die Quelle, die der Baum anspringen soll, nach einem Klick auf einen Wert im Blatt */
  const [reveal, setReveal] = useState<{ path: string; nonce: number } | null>(null)

  function parse(text: string, sourceName: string) {
    try {
      const value: unknown = JSON.parse(text)
      setLoaded({ sourceName, sizeBytes: new Blob([text]).size, value })
      setLoadCount((count) => count + 1)
      setParseError(null)
      mappingDraft.recordFetch({ kind: 'JSON', sourceName }, value)
    } catch (error: unknown) {
      setParseError(error instanceof Error ? error.message : t('common.unknownError'))
    }
  }

  function loadFiles(files: File[]) {
    const file = files[0]
    if (!file) {
      return
    }
    file
      .text()
      .then((text) => parse(text, file.name))
      .catch(() => setParseError(t('mapping.jsonView.fileUnreadable')))
  }

  const sizeText = loaded === null ? '' : `${(loaded.sizeBytes / 1024).toLocaleString(i18n.language, { maximumFractionDigits: 0 })} KB`

  let loadFooter
  if (parseError !== null) {
    loadFooter = <span className="text-danger">{t('mapping.jsonView.parseError', { error: parseError })}</span>
  } else if (loaded !== null) {
    loadFooter = <span>{t('mapping.jsonView.loaded', { name: loaded.sourceName, size: sizeText })}</span>
  } else {
    loadFooter = <span>{t('mapping.jsonView.loadHint')}</span>
  }

  return (
    <>
      <ContentBox title={t('mapping.jsonView.loadTitle')} footer={loadFooter}>
        <div className="d-flex flex-column gap-2">
          <FileDropzone onFiles={loadFiles} accept=".json,.txt,application/json,text/plain" text={t('mapping.jsonView.dropzone')} />
          <textarea
            className="form-control form-control-sm font-monospace"
            rows={4}
            value={pastedText}
            placeholder={t('mapping.jsonView.pastePlaceholder')}
            aria-label={t('mapping.jsonView.pastePlaceholder')}
            onChange={(event) => setPastedText(event.target.value)}
          />
          <div>
            <button
              type="button"
              className="button-other"
              disabled={pastedText.trim() === ''}
              onClick={() => parse(pastedText, t('mapping.jsonView.pastedSource'))}
            >
              {t('mapping.jsonView.show')}
            </button>
          </div>
        </div>
      </ContentBox>

      <MappingDraftBox
        mappingDraft={mappingDraft}
        onJumpToLine={(line) => setReveal({ path: fullPathOf(line), nonce: (reveal?.nonce ?? 0) + 1 })}
      />

      {loaded !== null && (
        <MappingJsonTreeBox
          key={loadCount}
          value={loaded.value}
          onSelect={(path, value) => mappingDraft.recordClick(path, value)}
          onIgnore={mappingDraft.current === null ? undefined : (path) => mappingDraft.ignorePath(path)}
          decisionOf={mappingDraft.current === null ? undefined : mappingDraft.decisionOf}
          reveal={reveal}
        />
      )}
    </>
  )
}
