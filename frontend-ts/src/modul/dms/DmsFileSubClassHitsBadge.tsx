import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import type { OriginState } from '../../components/Origin.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { ADMIN_ACCESS_ROLE_KEY } from '../accessrole/AccessRole.ts'
import { fileSubClassNameOf } from '../filesubclass/FileSubClass.ts'
import { FileSubClassSymbol } from '../filesubclass/FileSubClassSymbol.tsx'
import { useSession } from '../login/sessionContext.ts'
import { DMS_ACCESS_ROLE_KEY, dmsFieldLabel, dmsSolrDocumentPath, FILE_SUB_CLASS_FIELD_NAME, resolveDmsDocumentsPageByExactFieldValues } from './Dms.ts'
import { DmsSolrDocumentActionButtons } from './DmsSolrDocumentActionButtons.tsx'
import type { DmsSolrDocument } from './DmsSolrDocumentFacts.tsx'

interface DmsFileSubClassHitsBadgeProps {
  /** die Dateiart, deren Symbol steht und nach der aufgelöst wird, z. B. DATENBLATT */
  fileSubClassKey: string
  /** je Hook ein Wert, z. B. artikelNummer: mehrere Hooks sind UND */
  valueByHookName: Record<string, string>
  /** die Herkunft für jeden Weg aus der Trefferliste */
  origin: OriginState
}

/** Was zu einem Schlüssel geladen ist: die Treffer, oder der Grund, warum nicht */
interface LoadedHits {
  key: string
  hits: DmsSolrDocument[] | null
  errorMessage: string | null
}

/** So lange gilt ein geladener Stand je Dateiart und Hookwerten, danach fragt der nächste Einbau neu */
const CACHE_TTL_MS = 60_000

/** Zwischenspeicher im Speicher der Seite: dieselbe Nummer in zwei Zeilen fragt das DMS nur einmal */
const hitsCache = new Map<string, { at: number; pending: Promise<DmsSolrDocument[]> }>()

function cacheKeyOf(fileSubClassKey: string, valueByHookName: Record<string, string>): string {
  return JSON.stringify([fileSubClassKey, Object.entries(valueByHookName).sort(([a], [b]) => a.localeCompare(b))])
}

/** Das Auflösen: die Dateiart plus jeder Hook mit seinem Wert, alles UND; die Antwort trägt nur lesbare Felder */
function loadHits(cacheKey: string): Promise<DmsSolrDocument[]> {
  const cached = hitsCache.get(cacheKey)
  if (cached !== undefined && Date.now() - cached.at < CACHE_TTL_MS) {
    return cached.pending
  }
  const [fileSubClassKey, entries] = JSON.parse(cacheKey) as [string, [string, string][]]
  const fieldValuesByFieldName: Record<string, unknown[]> = { [FILE_SUB_CLASS_FIELD_NAME]: [fileSubClassKey] }
  for (const [hookName, value] of entries) {
    fieldValuesByFieldName[hookName] = [value]
  }
  const pending = resolveDmsDocumentsPageByExactFieldValues({ fieldValuesByFieldName, page: 0, size: 0, sortFieldName: null, sortDescending: false }).then((result) => result.data.documents)
  // ein Fehler bleibt nicht im Speicher, der nächste Einbau versucht es neu
  pending.catch(() => hitsCache.delete(cacheKey))
  hitsCache.set(cacheKey, { at: Date.now(), pending })
  return pending
}

function textOf(document: DmsSolrDocument, fieldName: string): string {
  const value = document[fieldName]
  return typeof value === 'string' ? value : typeof value === 'number' ? String(value) : ''
}

function valuesOf(value: unknown): string[] {
  if (value === null || value === undefined) {
    return []
  }
  return (Array.isArray(value) ? value : [value]).map((entry) => String(entry))
}

/**
 * Das Symbol einer Dateiart mit der Zahl der DMS-Treffer dahinter, aufgelöst nach der Dateiart und einem oder mehreren
 * Hooks (User 2026-10-13, die Datenblätter je Stücklistenzeile): Auflösen, keine Suche; das Backend prüft die Tür DMS
 * und die Lese-ARs der Felder. Ohne die AR DMS steht nichts und es wird nichts gefragt. Im interaktiven Hoverlay die
 * Treffer in der zentralen Table mit dem Weg zur Dokumentseite und den Aktionsknöpfen. Für jeden Ort, der zu einem
 * Wert die Dateien einer Dateiart zeigen will.
 */
export function DmsFileSubClassHitsBadge({ fileSubClassKey, valueByHookName, origin }: DmsFileSubClassHitsBadgeProps) {
  const { t, i18n } = useTranslation()
  const { language } = useLanguage()
  const navigate = useNavigate()
  const { hasAccessRole } = useSession()
  const { fileSubClasses, solrFields } = useMasterData()
  const allowed = hasAccessRole(DMS_ACCESS_ROLE_KEY)
  const fileSubClass = fileSubClasses?.find((candidate) => candidate.key === fileSubClassKey) ?? null
  const cacheKey = cacheKeyOf(fileSubClassKey, valueByHookName)
  const [lastLoaded, setLastLoaded] = useState<LoadedHits | null>(null)

  useEffect(() => {
    if (!allowed) {
      return
    }
    let cancelled = false
    loadHits(cacheKey)
      .then((hits) => {
        if (!cancelled) {
          setLastLoaded({ key: cacheKey, hits, errorMessage: null })
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setLastLoaded({ key: cacheKey, hits: null, errorMessage: error instanceof RequestError ? error.message : t('common.unknownError') })
        }
      })
    return () => {
      cancelled = true
    }
  }, [allowed, cacheKey, t])

  if (!allowed || fileSubClass === null) {
    return null
  }

  const loaded = lastLoaded !== null && lastLoaded.key === cacheKey ? lastLoaded : null
  const hits = loaded?.hits ?? null
  const name = fileSubClassNameOf(fileSubClass, language)
  const values = Object.values(valueByHookName).join(', ')
  const labelOf = (fieldName: string, fallback: string) => dmsFieldLabel(solrFields, fieldName, fallback, language)

  const columns: TableColumn<DmsSolrDocument>[] = [
    {
      header: labelOf('fileName', t('dms.document.fileName')),
      cell: (document) => {
        const id = textOf(document, 'id')
        return (
          <button type="button" className="btn btn-link btn-sm p-0 border-0 text-start text-break" onClick={() => navigate(dmsSolrDocumentPath(id), { state: origin })}>
            {textOf(document, 'fileName') || id}
          </button>
        )
      },
    },
    { header: labelOf('fileIndex', t('dms.document.fileIndex')), cell: (document) => textOf(document, 'fileIndex'), unimportant: true, width: '5rem' },
    {
      header: labelOf('version', t('dms.document.version')),
      cell: (document) => (typeof document.version === 'number' && document.version > 1 ? document.version : ''),
      unimportant: true,
      width: '4rem',
    },
    {
      header: labelOf('lastChangedAt', t('dms.document.lastChangedAt')),
      cell: (document) => {
        const changed = textOf(document, 'lastChangedAt')
        const date = new Date(changed)
        return changed === '' ? '' : Number.isNaN(date.getTime()) ? changed : date.toLocaleDateString(i18n.language)
      },
      unimportant: true,
      width: '6rem',
    },
  ]

  let content
  if (loaded?.errorMessage) {
    content = <span className="text-danger">{t('dms.fileSubClassHits.failed', { error: loaded.errorMessage })}</span>
  } else if (hits === null) {
    content = <span>{t('common.loading')}</span>
  } else if (hits.length === 0) {
    content = <span>{t('dms.fileSubClassHits.none', { name })}</span>
  } else {
    content = (
      <Table
        columns={columns}
        rows={hits}
        rowKey={(document) => textOf(document, 'id')}
        detailTitle={(document) => textOf(document, 'fileName') || textOf(document, 'id')}
        emptyText={t('dms.fileSubClassHits.none', { name })}
        actionsWidth="13rem"
        actions={(document) => (
          <DmsSolrDocumentActionButtons
            document={document}
            origin={origin}
            writeAclMatchForCurrentUser={hasAccessRole(ADMIN_ACCESS_ROLE_KEY) || valuesOf(document.writeAcl).some(hasAccessRole)}
          />
        )}
      />
    )
  }

  const hoverlay = (
    <span className="hoverlay-explanation">
      <strong>{t('dms.fileSubClassHits.title', { name, values })}</strong>
      {content}
    </span>
  )

  return (
    <Hoverlay text={hoverlay} interactive>
      <span className={hits !== null && hits.length === 0 ? 'd-inline-flex align-items-center gap-1 opacity-50' : 'd-inline-flex align-items-center gap-1'}>
        <FileSubClassSymbol fileSubClass={fileSubClass} />
        <span className="small">({hits === null ? '…' : hits.length})</span>
      </span>
    </Hoverlay>
  )
}
