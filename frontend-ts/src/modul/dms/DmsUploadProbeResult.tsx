import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { LanguageCode } from '../../branding/languages.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { solrHookValuesOf } from '../solrmanager/SolrHook.ts'
import { SolrHookFields } from '../solrmanager/SolrHookFields.tsx'
import type { DmsUploadProbe } from './Dms.ts'

/** So viel vom Text steht sofort da, der Rest auf Knopfdruck */
const TEXT_PREVIEW_CHARS = 4000

interface DmsUploadProbeResultProps {
  probe: DmsUploadProbe
  language: LanguageCode
}

/** Eine Zeile Name und Wert, für die geprüften Felder und die Tika-Metadaten */
interface NameValueRow {
  name: string
  value: string
}

/**
 * Die Antwort der Upload-Probe, so wie sie ankam: die Datei mit Hash, die geprüften Felder in der Reihenfolge des
 * Kerns, alles von Tika (Erkanntes, jede Metadate, der Text). Vier Boxen, damit jede für sich lesbar bleibt.
 */
export function DmsUploadProbeResult({ probe, language }: DmsUploadProbeResultProps) {
  const { t } = useTranslation()
  const { solrHooks } = useMasterData()
  const [showAllText, setShowAllText] = useState(false)
  // die Hooks des Dokuments über das Hook-Modul: Chip und Werte als Badges, Artikelnummern als Link
  const hooks = solrHookValuesOf(probe.document, solrHooks ?? [])

  const nameValueColumns: TableColumn<NameValueRow>[] = [
    { header: t('dms.result.name'), width: '18rem', cell: (row) => <code>{row.name}</code> },
    { header: t('dms.result.valueColumn'), cell: (row) => <span className="text-break">{row.value}</span> },
  ]

  const documentRows: NameValueRow[] = Object.entries(probe.document).map(([name, value]) => ({
    name,
    value: Array.isArray(value) ? value.map(String).join('; ') : String(value),
  }))
  const metadataRows: NameValueRow[] = Object.entries(probe.tika.metadata).map(([name, values]) => ({
    name,
    value: values.join('; '),
  }))

  const formatDate = (value: string | null) =>
    value === null ? t('dms.result.unknown') : new Date(value).toLocaleString(language)

  const text = probe.tika.text
  const textTruncated = !showAllText && text.length > TEXT_PREVIEW_CHARS

  return (
    <>
      <ContentBox title={t('dms.result.title')}>
        <dl className="details-list">
          <div>
            <dt>{t('dms.result.file')}</dt>
            <dd>{probe.fileName}</dd>
          </div>
          <div>
            <dt>{t('dms.result.fileExtension')}</dt>
            <dd>{probe.fileExtension === '' ? t('dms.result.unknown') : probe.fileExtension}</dd>
          </div>
          <div>
            <dt>{t('dms.result.contentType')}</dt>
            <dd>{probe.contentType ?? t('dms.result.unknown')}</dd>
          </div>
          <div>
            <dt>{t('dms.result.size')}</dt>
            <dd>{t('dms.result.bytes', { count: probe.sizeBytes })}</dd>
          </div>
          <div>
            <dt>{t('dms.result.sha256')}</dt>
            <dd>
              <code className="text-break">{probe.sha256}</code>
            </dd>
          </div>
          <div>
            <dt>{t('dms.result.stored')}</dt>
            <dd>{probe.stored ? t('dms.result.yes') : t('dms.result.notStored')}</dd>
          </div>
          {probe.tika.ok ? (
            <>
              <div>
                <dt>{t('dms.result.mimeType')}</dt>
                <dd>{probe.tika.mimeType ?? t('dms.result.unknown')}</dd>
              </div>
              <div>
                <dt>{t('dms.result.createdAt')}</dt>
                <dd>{formatDate(probe.tika.createdAt)}</dd>
              </div>
              <div>
                <dt>{t('dms.result.modifiedAt')}</dt>
                <dd>{formatDate(probe.tika.modifiedAt)}</dd>
              </div>
              <div>
                <dt>{t('dms.result.orientation')}</dt>
                <dd>{probe.tika.orientation ?? t('dms.result.unknown')}</dd>
              </div>
              <div>
                <dt>{t('dms.result.duration')}</dt>
                <dd>{t('dms.result.ms', { count: probe.tika.durationMs })}</dd>
              </div>
            </>
          ) : (
            <div>
              <dt>Tika</dt>
              <dd className="text-danger">{t('dms.result.tikaFailed', { error: probe.tika.error ?? '' })}</dd>
            </div>
          )}
        </dl>
      </ContentBox>

      <ContentBox title={t('dms.result.preview')}>
        {!probe.preview.attempted && <span className="text-secondary">{t('dms.result.previewSkipped')}</span>}
        {probe.preview.attempted && !probe.preview.ok && (
          <span className="text-danger">{t('dms.result.previewFailed', { error: probe.preview.error ?? '' })}</span>
        )}
        {probe.preview.ok && probe.preview.base64Png !== null && (
          <div className="d-flex flex-wrap align-items-start gap-3">
            <img
              className="dms-preview-image"
              src={`data:image/png;base64,${probe.preview.base64Png}`}
              alt={t('dms.result.preview')}
              width={200}
              height={200}
            />
            <dl className="details-list">
              <div>
                <dt>{t('dms.result.previewSource')}</dt>
                <dd>
                  {probe.preview.sourceWidth} × {probe.preview.sourceHeight}
                </dd>
              </div>
              <div>
                <dt>{t('dms.result.orientation')}</dt>
                <dd>{probe.preview.orientation}</dd>
              </div>
              <div>
                <dt>{t('dms.result.previewPng')}</dt>
                <dd>{t('dms.result.bytes', { count: probe.preview.bytes })}</dd>
              </div>
              <div>
                <dt>{t('dms.result.duration')}</dt>
                <dd>{t('dms.result.ms', { count: probe.preview.durationMs })}</dd>
              </div>
            </dl>
          </div>
        )}
      </ContentBox>

      <ContentBox title={t('dms.result.document')}>
        <div className="d-flex flex-column gap-3">
          <SolrHookFields hooks={hooks} language={language} title={t('dms.result.hooks')} />
          <Table
            columns={nameValueColumns}
            rows={documentRows}
            rowKey={(row) => row.name}
            detailTitle={(row) => row.name}
            emptyText={t('dms.result.documentEmpty')}
          />
        </div>
      </ContentBox>

      {probe.tika.ok && (
        <>
          <ContentBox title={t('dms.result.metadata')}>
            <Table
              columns={nameValueColumns}
              rows={metadataRows}
              rowKey={(row) => row.name}
              detailTitle={(row) => row.name}
              emptyText={t('dms.result.metadataEmpty')}
            />
          </ContentBox>

          <ContentBox
            title={t('dms.result.text')}
            actions={
              text.length > TEXT_PREVIEW_CHARS ? (
                <button type="button" className="button-cancel" onClick={() => setShowAllText((current) => !current)}>
                  {showAllText ? t('dms.result.showLess') : t('dms.result.showAll')}
                </button>
              ) : undefined
            }
          >
            <p className="small text-secondary mb-2">{t('dms.result.chars', { count: probe.tika.textLength })}</p>
            {text.length === 0 ? (
              <span className="text-secondary">{t('dms.result.textEmpty')}</span>
            ) : (
              <pre className="dms-text-preview">
                {textTruncated ? `${text.slice(0, TEXT_PREVIEW_CHARS)} …` : text}
              </pre>
            )}
          </ContentBox>
        </>
      )}
    </>
  )
}
