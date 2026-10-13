import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../../api/client.ts'
import { JsonTree } from '../../components/JsonTree.tsx'
import { Popup } from '../../components/Popup.tsx'
import { fetchAbasSternzeitRaw } from './AbasSternzeit.ts'

interface AbasSternzeitResponsePopupProps {
  /** die Kennung, deren Antwort gezeigt wird; null heißt geschlossen */
  sternzeit: string | null
  onClose: () => void
}

/** Die geholte Antwort, an ihre Kennung gebunden: Für eine andere Kennung zählt sie nicht, dann läuft die Abfrage */
interface LoadedResponse {
  sternzeit: string
  value: unknown
  errorMessage: string | null
}

/**
 * Die rohe Antwort von ABAS zu einer Sternzeit, live geholt und als Baum gezeigt (JsonTree): zum Hineinschauen, wenn
 * ein Typ unbekannt ist, und der Platz, an dem die Adresse eines Wertes fürs Mapping ausgewählt wird. Mit Suche über
 * Schlüssel und Werte, der angeklickte Knoten zeigt seinen Pfad.
 */
export function AbasSternzeitResponsePopup({ sternzeit, onClose }: AbasSternzeitResponsePopupProps) {
  const { t } = useTranslation()
  const [loaded, setLoaded] = useState<LoadedResponse | null>(null)
  const [filter, setFilter] = useState('')
  const [selectedPath, setSelectedPath] = useState<string | null>(null)

  useEffect(() => {
    if (sternzeit === null) {
      return
    }
    let current = true
    // alle Kopffelder, wie der Worker sie holt
    fetchAbasSternzeitRaw(sternzeit, [], [])
      .then((result) => {
        if (current) {
          setLoaded({ sternzeit, value: result.data, errorMessage: null })
        }
      })
      .catch((error: unknown) => {
        if (current) {
          setLoaded({
            sternzeit,
            value: null,
            errorMessage: error instanceof RequestError ? error.message : t('common.unknownError'),
          })
        }
      })
    return () => {
      current = false
    }
  }, [sternzeit, t])

  // nur, was zur gezeigten Kennung gehört; eine alte Antwort bleibt unsichtbar, bis die neue da ist
  const shown = loaded !== null && loaded.sternzeit === sternzeit ? loaded : null
  const errorMessage = shown?.errorMessage ?? null

  const footer = (
    <span className="d-inline-flex align-items-center gap-2 flex-wrap">
      {selectedPath !== null && <code className="text-break">{selectedPath}</code>}
      <button type="button" className="button-cancel" onClick={onClose}>
        {t('common.close')}
      </button>
    </span>
  )

  return (
    <Popup
      open={sternzeit !== null}
      title={t('abasmanager.overview.responseTitle', { sternzeit: sternzeit ?? '' })}
      footer={footer}
      onClose={onClose}
    >
      {errorMessage !== null && (
        <p className="text-danger mb-0">{t('abasmanager.overview.responseFailed', { error: errorMessage })}</p>
      )}
      {shown === null && (
        <p className="mb-0">
          <span className="spinner-border spinner-border-sm me-2" role="status" aria-label={t('common.loading')} />
          {t('abasmanager.overview.responseLoading')}
        </p>
      )}
      {shown !== null && errorMessage === null && (
        <div className="d-flex flex-column gap-2">
          <input
            type="search"
            className="form-control form-control-sm"
            value={filter}
            placeholder={t('mapping.jsonView.searchPlaceholder')}
            aria-label={t('mapping.jsonView.searchPlaceholder')}
            onChange={(event) => setFilter(event.target.value)}
          />
          <JsonTree
            value={shown.value}
            expandedDepth={2}
            filter={filter}
            onSelect={(path) => setSelectedPath(path)}
            selectedPath={selectedPath}
          />
        </div>
      )}
    </Popup>
  )
}
