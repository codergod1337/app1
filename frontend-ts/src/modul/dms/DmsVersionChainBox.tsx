import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { ContentBox } from '../../components/ContentBox.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import type { OriginState } from '../../components/Origin.ts'
import { solrCoreColorStyle, solrCoreOfKey } from '../solrmanager/SolrCore.ts'
import {
  DMS_CORE_KEY,
  DMS_STATE_ACTIVE,
  dmsFieldLabel,
  dmsSolrDocumentPath,
  dmsVersionOf,
  getDmsVersionChain,
  STATUS_FIELD_NAME,
} from './Dms.ts'

interface DmsVersionChainBoxProps {
  versionChain: string
  /** die gerade geöffnete Fassung, der Zug: hervorgehoben und nicht verlinkt */
  currentId: string
  /** woher man kam, damit die Links der anderen Fassungen es weiterreichen */
  origin?: OriginState
}

/**
 * Der Fahrplan einer Versionskette (nach DmsVersionsFahrplan der Vorlage, gebaut 2026-10-08): alle Fassungen
 * untereinander wie Haltestellen an einer Linie in der Kernfarbe, die neueste oben (User 2026-10-08, anders als die
 * Vorlage). Die geöffnete ist der Zug,
 * hervorgehoben und nicht verlinkt, alle anderen sind ein Klick entfernt. Eigene ContentBox unter den Details, denn
 * eine Box gehört nicht in eine andere; sie erscheint nur bei mehr als einer Fassung, ein Fahrplan mit einer
 * Haltestelle wäre Ballast. Fällt sie aus, bleibt die Detailansicht vollständig.
 */
export function DmsVersionChainBox({ versionChain, currentId, origin }: DmsVersionChainBoxProps) {
  const { t, i18n } = useTranslation()
  const { language } = useLanguage()
  const { solrCores, solrFields, entityStates } = useMasterData()
  const [members, setMembers] = useState<Record<string, unknown>[] | null>(null)

  useEffect(() => {
    let cancelled = false
    getDmsVersionChain(versionChain)
      .then(({ data }) => {
        if (!cancelled) {
          // die neueste Fassung oben (User 2026-10-08), unabhängig davon, wie das Backend sortiert hat
          setMembers([...data].sort((a, b) => dmsVersionOf(b) - dmsVersionOf(a)))
        }
      })
      .catch(() => {
        if (!cancelled) {
          setMembers([])
        }
      })
    return () => {
      cancelled = true
    }
  }, [versionChain])

  if (members === null || members.length < 2) {
    return null
  }

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
    const name = state ? translate(state.displayName, language) : ''
    return name !== '' ? name : value
  }

  const indexLabel = dmsFieldLabel(solrFields, 'fileIndex', t('dms.document.fileIndex'), language)

  return (
    <ContentBox title={t('dms.versionChain.title', { count: members.length })}>
      <ol className="dms-version-chain" style={solrCoreColorStyle(solrCoreOfKey(solrCores, DMS_CORE_KEY))}>
        {members.map((member) => {
          const id = String(member.id ?? '')
          const current = id === currentId
          const fileName = typeof member.fileName === 'string' ? member.fileName : id
          const fileIndex = typeof member.fileIndex === 'string' ? member.fileIndex : null
          const description = typeof member.fileDescription === 'string' ? translate(member.fileDescription, language) : ''
          const status = member[STATUS_FIELD_NAME]
          return (
            <li key={id} className={current ? 'dms-version-chain-stop current' : 'dms-version-chain-stop'}>
              <span className="dms-version-chain-version">v{dmsVersionOf(member)}</span>
              <span className="dms-version-chain-facts">
                {current ? (
                  <span className="dms-version-chain-name" aria-label={t('dms.versionChain.current')}>
                    {fileName}
                  </span>
                ) : (
                  <Link to={dmsSolrDocumentPath(id)} state={origin} className="dms-version-chain-name">
                    {fileName}
                  </Link>
                )}
                <span className="dms-version-chain-meta">
                  {fileIndex !== null && fileIndex !== '' && (
                    <span>
                      {indexLabel}: {fileIndex}
                    </span>
                  )}
                  <span>{dateTime(member.documentUploadedAt) ?? '—'}</span>
                  {typeof status === 'string' && status !== DMS_STATE_ACTIVE && <span>{statusName(status)}</span>}
                </span>
                {description !== '' && <span className="dms-version-chain-description">{description}</span>}
              </span>
            </li>
          )
        })}
      </ol>
    </ContentBox>
  )
}
