import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ContentBox } from '../../components/ContentBox.tsx'
import { FileDropzone } from '../../components/FileDropzone.tsx'
import { Icon } from '../../components/Icon.tsx'
import { readableBytes } from '../../components/readableBytes.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { showToast } from '../../components/toastStore.ts'
import {
  abasSternzeitExportUrl,
  abasSternzeitImportGroupKey,
  type AbasSternzeitImportCheck,
  type AbasSternzeitImportGroup,
  type AbasSternzeitImportResult,
  type AbasSternzeitImportSelection,
  checkAbasSternzeitImportFile,
  importAbasSternzeitenFromFile,
} from './AbasSternzeit.ts'

/** Wo der Import steht: Datei gewählt, wird geprüft, ist geprüft, wird importiert, ist importiert */
type TransferPhase = 'idle' | 'checking' | 'checked' | 'importing' | 'imported'

interface AbasSternzeitTransferBoxProps {
  /** nach einem Import, z. B. um die Kopfzahlen neu zu laden */
  onImported?: () => void
}

/**
 * Export und Import der Sternzeiten (User 2026-10-13), eine Box im Tab „Übersicht“. Der Export ist ein normaler
 * Download. Der Import geht in zwei Schritten: Prüfen lädt die Datei hoch und zeigt je Objekttyp, ohne Typ je
 * Datenbank, wie viele Sternzeiten darin stehen; nach der Wahl der Gruppen lädt Importieren dieselbe Datei noch einmal
 * hoch, und nur die gewählten Gruppen kommen zum Holen in die Queue. Beide Schritte melden den Fortschritt laufend:
 * erst die hochgeladenen Bytes, dann die Zwischenstände des Servers.
 */
export function AbasSternzeitTransferBox({ onImported }: AbasSternzeitTransferBoxProps) {
  const { t, i18n } = useTranslation()
  const [file, setFile] = useState<File | null>(null)
  const [phase, setPhase] = useState<TransferPhase>('idle')
  const [uploadPercent, setUploadPercent] = useState<number | null>(null)
  const [check, setCheck] = useState<AbasSternzeitImportCheck | null>(null)
  const [selectedGroupKeys, setSelectedGroupKeys] = useState<Set<string>>(() => new Set())
  const [result, setResult] = useState<AbasSternzeitImportResult | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const running = phase === 'checking' || phase === 'importing'

  function formatNumber(value: number): string {
    return value.toLocaleString(i18n.language)
  }

  function errorMessageOf(error: unknown): string {
    return error instanceof Error && error.message !== '' ? error.message : t('common.unknownError')
  }

  function groupLabel(group: AbasSternzeitImportGroup): string {
    if (group.objectType !== null) {
      return group.objectType
    }
    return group.abasDatabaseNumber !== null
      ? t('abasmanager.transfer.groupDatabase', { number: group.abasDatabaseNumber })
      : t('abasmanager.transfer.groupUnknown')
  }

  function trackUpload(sentBytes: number, totalBytes: number) {
    setUploadPercent(totalBytes > 0 ? Math.round((sentBytes * 100) / totalBytes) : null)
  }

  /** Eine neue Datei: alles vom vorigen Lauf weg */
  function chooseFile(files: File[]) {
    setFile(files[0] ?? null)
    setPhase('idle')
    setCheck(null)
    setSelectedGroupKeys(new Set())
    setResult(null)
    setErrorMessage(null)
    setUploadPercent(null)
  }

  function runCheck() {
    if (file === null) {
      return
    }
    setPhase('checking')
    setCheck(null)
    setSelectedGroupKeys(new Set())
    setResult(null)
    setErrorMessage(null)
    setUploadPercent(0)
    checkAbasSternzeitImportFile(file, setCheck, trackUpload)
      .then((finalCheck) => {
        setCheck(finalCheck)
        setPhase('checked')
      })
      .catch((error: unknown) => {
        setErrorMessage(errorMessageOf(error))
        setPhase('idle')
      })
  }

  function runImport() {
    if (file === null || check === null) {
      return
    }
    const selection: AbasSternzeitImportSelection = { objectTypes: [], abasDatabaseNumbers: [] }
    for (const group of check.groups) {
      if (!selectedGroupKeys.has(abasSternzeitImportGroupKey(group))) {
        continue
      }
      if (group.objectType !== null) {
        selection.objectTypes.push(group.objectType)
      } else if (group.abasDatabaseNumber !== null) {
        selection.abasDatabaseNumbers.push(group.abasDatabaseNumber)
      }
    }
    setPhase('importing')
    setResult(null)
    setErrorMessage(null)
    setUploadPercent(0)
    importAbasSternzeitenFromFile(file, selection, setResult, trackUpload)
      .then((finalResult) => {
        setResult(finalResult)
        setPhase('imported')
        showToast({
          kind: 'success',
          title: t('abasmanager.transfer.importedTitle'),
          text: resultText(finalResult),
        })
        onImported?.()
      })
      .catch((error: unknown) => {
        const message = errorMessageOf(error)
        setErrorMessage(message)
        setPhase('checked')
        showToast({ kind: 'error', title: t('abasmanager.transfer.importFailedTitle'), text: message })
      })
  }

  function toggleGroup(groupKey: string) {
    setSelectedGroupKeys((current) => {
      const next = new Set(current)
      if (next.has(groupKey)) {
        next.delete(groupKey)
      } else {
        next.add(groupKey)
      }
      return next
    })
  }

  function resultText(importResult: AbasSternzeitImportResult): string {
    return t('abasmanager.transfer.resultText', {
      found: formatNumber(importResult.found),
      filtered: formatNumber(importResult.filtered),
      queued: formatNumber(importResult.queued),
      skipped: formatNumber(importResult.skipped),
      ignored: formatNumber(importResult.ignored),
    })
  }

  // Erst laufen die Bytes zum Server, danach liest er: dann zählen seine Zwischenstände
  let progressText: string | null = null
  if (running) {
    if (uploadPercent !== null && uploadPercent < 100) {
      progressText = t('abasmanager.transfer.uploading', { percent: uploadPercent })
    } else if (phase === 'checking') {
      progressText = t('abasmanager.transfer.reading', { count: formatNumber(check?.sternzeitCount ?? 0) })
    } else {
      progressText = t('abasmanager.transfer.importProgress', {
        found: formatNumber(result?.found ?? 0),
        queued: formatNumber(result?.queued ?? 0),
      })
    }
  }

  const columns: TableColumn<AbasSternzeitImportGroup>[] = [
    {
      header: t('abasmanager.transfer.columns.select'),
      width: '4rem',
      cell: (group) => {
        const groupKey = abasSternzeitImportGroupKey(group)
        return (
          <input
            type="checkbox"
            className="form-check-input"
            checked={selectedGroupKeys.has(groupKey)}
            disabled={running}
            aria-label={t('abasmanager.transfer.selectGroup', { group: groupLabel(group) })}
            onChange={() => toggleGroup(groupKey)}
          />
        )
      },
    },
    {
      header: t('abasmanager.transfer.columns.group'),
      headerHint: t('abasmanager.transfer.groupHint'),
      cell: (group) =>
        group.objectType !== null ? (
          <strong>{group.objectType}</strong>
        ) : (
          <span className="text-body-secondary">{groupLabel(group)}</span>
        ),
    },
    {
      header: t('abasmanager.transfer.columns.count'),
      width: '8rem',
      cell: (group) => formatNumber(group.sternzeitCount),
    },
    {
      header: t('abasmanager.transfer.columns.example'),
      width: '10rem',
      unimportant: true,
      cell: (group) => group.exampleSternzeit,
    },
  ]

  return (
    <ContentBox title={t('abasmanager.transfer.title')} footer={t('abasmanager.transfer.footer')}>
      <div className="d-flex flex-column gap-3">
        <div className="d-flex align-items-center gap-2 flex-wrap">
          <span>{t('abasmanager.transfer.exportText')}</span>
          <a className="button-other" href={abasSternzeitExportUrl()} download>
            <Icon name="download" /> {t('abasmanager.transfer.exportButton')}
          </a>
        </div>

        <FileDropzone onFiles={chooseFile} disabled={running} text={t('abasmanager.transfer.dropzone')} />

        {file !== null && (
          <div className="d-flex align-items-center gap-2 flex-wrap">
            <span>
              <strong>{file.name}</strong> {readableBytes(file.size)}
            </span>
            <button type="button" className="button-other" disabled={running} onClick={runCheck}>
              {t('abasmanager.transfer.check')}
            </button>
          </div>
        )}

        {progressText !== null && (
          <span className="d-inline-flex align-items-center gap-2">
            <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
            {progressText}
          </span>
        )}

        {errorMessage !== null && <p className="text-danger mb-0">{errorMessage}</p>}

        {check !== null && phase !== 'checking' && (
          <>
            <p className="mb-0">
              {check.sternzeitCount === 0
                ? t('abasmanager.transfer.noSternzeit')
                : t('abasmanager.transfer.checkSummary', {
                    count: formatNumber(check.sternzeitCount),
                    invalid: formatNumber(check.invalidCount),
                  })}
            </p>
            {check.groups.length > 0 && (
              <Table
                columns={columns}
                rows={check.groups}
                rowKey={abasSternzeitImportGroupKey}
                detailTitle={groupLabel}
                emptyText={t('abasmanager.transfer.noSternzeit')}
              />
            )}
            <div>
              <button
                type="button"
                className="button-save"
                disabled={running || selectedGroupKeys.size === 0}
                onClick={runImport}
              >
                {t('abasmanager.transfer.import')}
              </button>
            </div>
          </>
        )}

        {result !== null && phase === 'imported' && <p className="mb-0">{resultText(result)}</p>}
      </div>
    </ContentBox>
  )
}
