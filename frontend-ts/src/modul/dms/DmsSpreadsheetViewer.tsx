import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { read, utils, type WorkBook } from 'xlsx'
import { LoadingProgress } from '../../components/LoadingProgress.tsx'
import { useDmsFileBlob } from './Dms.ts'

interface DmsSpreadsheetViewerProps {
  id: string
  fileName: string
}

/**
 * Excel im Viewer (nach dem ExcelViewer der Vorlage), nur xlsx: SheetJS liest die Mappe ausschließlich lesend und zeigt
 * das gewählte Blatt als Tabelle, bei mehreren Blättern mit Knöpfen zum Umschalten. Reine Anzeige, kein Weg zurück ins
 * DMS. Die Bytes kommen nur in den Speicher (useDmsFileBlob).
 *
 * Zum innerHTML: sheet_to_html maskiert die Zellwerte selbst, es landet kein rohes fremdes HTML im DOM, nur die von
 * SheetJS gebaute Tabelle.
 */
export function DmsSpreadsheetViewer({ id, fileName }: DmsSpreadsheetViewerProps) {
  const { t } = useTranslation()
  const { blob, receivedBytes, totalBytes, errorMessage } = useDmsFileBlob(id)
  const [loaded, setLoaded] = useState<{ blob: Blob; workbook: WorkBook } | { blob: Blob; errorMessage: string } | null>(null)
  const [sheetIndex, setSheetIndex] = useState(0)

  useEffect(() => {
    if (blob === null) {
      return
    }
    let active = true
    blob
      .arrayBuffer()
      .then((bytes) => {
        if (active) {
          setLoaded({ blob, workbook: read(bytes) })
          setSheetIndex(0)
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setLoaded({ blob, errorMessage: error instanceof Error && error.message ? error.message : t('dms.viewer.renderFailed') })
        }
      })
    return () => {
      active = false
    }
    // t ändert sich nicht während des Lesens
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [blob])

  const current = loaded !== null && loaded.blob === blob ? loaded : null
  const workbook = current !== null && 'workbook' in current ? current.workbook : null
  const sheetHtml = useMemo(() => {
    const name = workbook?.SheetNames[sheetIndex]
    // header und footer leer: nur die Tabelle, kein ganzes HTML-Gerüst
    return workbook && name ? utils.sheet_to_html(workbook.Sheets[name], { header: '', footer: '' }) : null
  }, [workbook, sheetIndex])

  if (errorMessage !== null) {
    return <p className="text-danger mb-0">{errorMessage}</p>
  }
  if (current !== null && 'errorMessage' in current) {
    return <p className="text-danger mb-0">{current.errorMessage}</p>
  }
  if (workbook === null || sheetHtml === null) {
    return <LoadingProgress fileName={fileName} receivedBytes={receivedBytes} totalBytes={totalBytes} />
  }
  return (
    <div className="viewer-document" aria-label={fileName}>
      {workbook.SheetNames.length > 1 && (
        <div className="d-flex flex-wrap gap-2 mb-2" role="group" aria-label={t('dms.viewer.sheets')}>
          {workbook.SheetNames.map((name, index) => (
            <button
              key={name}
              type="button"
              className={index === sheetIndex ? 'button-save' : 'button-other'}
              aria-pressed={index === sheetIndex}
              onClick={() => setSheetIndex(index)}
            >
              {name}
            </button>
          ))}
        </div>
      )}
      <div className="viewer-sheet" dangerouslySetInnerHTML={{ __html: sheetHtml }} />
    </div>
  )
}
