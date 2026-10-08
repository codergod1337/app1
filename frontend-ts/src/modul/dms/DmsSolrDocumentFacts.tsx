import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { useLanguage } from '../../components/languageContext.ts'
import { dmsFieldLabel, DOCUMENT_LANGUAGE_FIELD_NAME, readableBytes, STATUS_FIELD_NAME } from './Dms.ts'
import { DmsDocumentLanguageFlag } from './DmsDocumentLanguageFlag.tsx'

/** Ein Dokument, wie es der Server liefert: nur die Felder, die der User lesen darf */
export type DmsSolrDocument = Record<string, unknown>

/** Der letzte Eintrag einer Liste oder der Wert selbst, als Text */
function lastOf(value: unknown): string | null {
  if (Array.isArray(value)) {
    return value.length > 0 ? String(value[value.length - 1]) : null
  }
  return value === null || value === undefined ? null : String(value)
}

/** Eine Angabe der Infobox: Beschriftung und Wert. Fehlt der Wert, steht ein Strich: Das Feld fehlt dem User oder ist leer. */
function InfoboxLine({ label, value }: { label: string; value: ReactNode }) {
  const empty = value === null || value === undefined || value === ''
  return (
    <div className="dms-infobox-line">
      <span>{label}</span>
      <span className="text-break">{empty ? '—' : value}</span>
    </div>
  )
}

/**
 * Die technischen Kennungen (id, Versionskette) nur im Hoverlay an einem kleinen Schlüssel neben dem Dateinamen: Man
 * braucht sie zum Nachschlagen und Verlinken, nicht beim Lesen, die id steht ohnehin in der Adresse (Vorgabe 2026-10-03).
 */
export function DmsSolrDocumentIdentifiersHint({ document }: { document: DmsSolrDocument }) {
  const { t } = useTranslation()
  const text = (
    <span className="dms-identifiers">
      <span>
        <span className="text-muted">{t('dms.document.id')}</span> <code>{String(document.id ?? '')}</code>
      </span>
      {typeof document.versionChain === 'string' && (
        <span>
          <span className="text-muted">{t('dms.document.versionChain')}</span> <code>{document.versionChain}</code>
        </span>
      )}
    </span>
  )
  return (
    <Hoverlay text={text}>
      <span className="dms-identifiers-symbol" tabIndex={0} aria-label={t('dms.document.identifiers')}>
        <Icon name="documentId" />
      </span>
    </Hoverlay>
  )
}

/**
 * Die Infobox eines DMS-Dokuments nach der DmsInfoBox der Vorlage: alles, was feststeht, ganz oben unter dem Kopf,
 * mehrspaltig im dezenten Rahmen, für Details und Bearbeiten dieselbe. Der Status zuerst, der Wert übersetzt; dann
 * Version, Index, Größe, Typ, Sprache als Flagge, Downloads, erstellt, hochgeladen von, zuletzt geändert von. Ein Feld,
 * das der User nicht lesen darf, fehlt im Dokument und steht als Strich, nie als Fehler.
 */
export function DmsSolrDocumentInfobox({ document }: { document: DmsSolrDocument }) {
  const { t, i18n } = useTranslation()
  const { language } = useLanguage()
  const { entityStates, solrFields } = useMasterData()
  /** die Beschriftung aus der Felddefinition, sonst aus dem Sprachkatalog */
  const labelOf = (name: string, fallback: string) => dmsFieldLabel(solrFields, name, fallback, language)

  function dateTime(value: unknown): string | null {
    if (typeof value !== 'string') {
      return null
    }
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString(i18n.language)
  }

  function statusName(value: unknown): string | null {
    if (typeof value !== 'string') {
      return null
    }
    const state = entityStates?.find((candidate) => candidate.key === value)
    const name = state ? translate(state.displayName) : ''
    return name !== '' ? `${name} (${value})` : value
  }

  const uploader = typeof document.uploader === 'string' ? document.uploader : null
  const lastChangedBy = lastOf(document.changingUsers) ?? uploader

  return (
    <div className="dms-infobox">
      <InfoboxLine label={labelOf(STATUS_FIELD_NAME, t('dms.document.status'))} value={statusName(document[STATUS_FIELD_NAME])} />
      <InfoboxLine label={labelOf('version', t('dms.document.version'))} value={lastOf(document.version)} />
      <InfoboxLine label={labelOf('fileIndex', t('dms.document.fileIndex'))} value={lastOf(document.fileIndex)} />
      <InfoboxLine label={labelOf('fileSizeBytes', t('dms.document.fileSize'))} value={readableBytes(document.fileSizeBytes)} />
      <InfoboxLine label={labelOf('mimeType', t('dms.document.mimeType'))} value={lastOf(document.mimeType)} />
      <InfoboxLine
        label={labelOf(DOCUMENT_LANGUAGE_FIELD_NAME, t('dms.document.documentLanguage'))}
        value={<DmsDocumentLanguageFlag code={document[DOCUMENT_LANGUAGE_FIELD_NAME]} />}
      />
      <InfoboxLine label={labelOf('downloadCount', t('dms.document.downloadCount'))} value={lastOf(document.downloadCount)} />
      <InfoboxLine
        label={labelOf('isPublic', t('dms.document.isPublic'))}
        value={
          typeof document.isPublic === 'boolean'
            ? t(document.isPublic ? 'dms.document.isPublicYes' : 'dms.document.isPublicNo')
            : null
        }
      />
      <InfoboxLine label={labelOf('documentCreatedAt', t('dms.document.createdAt'))} value={dateTime(document.documentCreatedAt)} />
      <InfoboxLine
        label={labelOf('documentUploadedAt', t('dms.document.uploadedAt'))}
        value={`${dateTime(document.documentUploadedAt) ?? '—'} · ${uploader ?? '—'}`}
      />
      <InfoboxLine
        label={labelOf('lastChangedAt', t('dms.document.lastChangedAt'))}
        value={`${dateTime(document.lastChangedAt) ?? '—'} · ${lastChangedBy ?? '—'}`}
      />
    </div>
  )
}
