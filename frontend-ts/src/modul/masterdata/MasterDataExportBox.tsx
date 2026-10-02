import { useState } from 'react'
import { RequestError } from '../../api/client.ts'
import { downloadBase64File } from '../../components/base64File.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import { showToast } from '../../components/toastStore.ts'
import {
  ALL_MASTER_DATA_SECTIONS,
  MASTER_DATA_SECTIONS,
  exportMasterData,
  type MasterDataSection,
} from './MasterDataTransfer.ts'

/** Export der Stammdaten: Bereiche anhaken, ZIP herunterladen. Passwörter und Sitzungen sind nie dabei. */
export function MasterDataExportBox() {
  const [sections, setSections] = useState<MasterDataSection[]>(ALL_MASTER_DATA_SECTIONS)
  const [exporting, setExporting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  /** An- oder abhaken, die Reihenfolge bleibt die des Imports */
  function toggleSection(section: MasterDataSection) {
    setSections((current) =>
      current.includes(section)
        ? current.filter((candidate) => candidate !== section)
        : ALL_MASTER_DATA_SECTIONS.filter((candidate) => current.includes(candidate) || candidate === section),
    )
  }

  function startExport() {
    setExporting(true)
    setErrorMessage(null)
    exportMasterData(sections)
      .then(({ data }) => {
        downloadBase64File(data.fileName, data.zip, 'application/zip')
        const total = Object.values(data.counts).reduce((sum, count) => sum + (count ?? 0), 0)
        showToast({ kind: 'success', title: 'Export fertig', text: `${data.fileName} mit ${total} Datensätzen` })
      })
      .catch((error: unknown) => setErrorMessage(error instanceof RequestError ? error.message : 'Unbekannter Fehler'))
      .finally(() => setExporting(false))
  }

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      <span className="ms-auto d-flex gap-2">
        <button type="button" className="button-save" disabled={exporting || sections.length === 0} onClick={startExport}>
          <Icon name="download" /> Exportieren
        </button>
      </span>
    </>
  )

  return (
    <ContentBox
      title={
        <>
          <Icon name="download" /> Export
        </>
      }
      footer={footer}
    >
      <p className="mb-3">
        Alles, was im Admin gepflegt wird, als ZIP mit einer Datei je Bereich. Passwörter, Sitzungen und 2FA sind nie
        dabei.
      </p>
      <fieldset className="masterdata-sections" disabled={exporting}>
        {MASTER_DATA_SECTIONS.map(({ section, label }) => (
          <div key={section} className="form-check">
            <input
              id={`export-${section}`}
              className="form-check-input"
              type="checkbox"
              checked={sections.includes(section)}
              onChange={() => toggleSection(section)}
            />
            <label className="form-check-label" htmlFor={`export-${section}`}>
              {label}
            </label>
          </div>
        ))}
      </fieldset>
      <div className="d-flex gap-2 mt-3">
        <button
          type="button"
          className="button-other"
          disabled={exporting}
          onClick={() => setSections(ALL_MASTER_DATA_SECTIONS)}
        >
          alle
        </button>
        <button type="button" className="button-other" disabled={exporting} onClick={() => setSections([])}>
          keine
        </button>
      </div>
    </ContentBox>
  )
}
