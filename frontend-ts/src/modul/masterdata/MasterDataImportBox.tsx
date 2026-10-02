import { useRef, useState, type ChangeEvent } from 'react'
import { RequestError } from '../../api/client.ts'
import { fileToBase64 } from '../../components/base64File.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { Icon } from '../../components/Icon.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { showToast } from '../../components/toastStore.ts'
import { MasterDataImportSection } from './MasterDataImportSection.tsx'
import {
  ALL_MASTER_DATA_SECTIONS,
  MASTER_DATA_SECTIONS,
  checkMasterDataImport,
  decisionOfConflict,
  importMasterData,
  type MasterDataImportCheck,
  type MasterDataSection,
  type UsersConflictDecision,
} from './MasterDataTransfer.ts'
import { UsersConflictCard } from './UsersConflictCard.tsx'

/** Die gewählte Datei, als Base64 für die Requests */
interface ChosenFile {
  name: string
  size: number
  base64: string
}

function errorText(error: unknown): string {
  return error instanceof RequestError ? error.message : 'Unbekannter Fehler'
}

/**
 * Import der Stammdaten: ZIP wählen, die Vorschau zeigt je Bereich, was neu käme, was es schon gibt und was nicht
 * geht. Jede Änderung an der Auswahl rechnet die Vorschau neu, denn was angehakt ist, entscheidet, welche Verweise
 * gelten. Erst der Knopf schreibt, in einer Transaktion.
 */
export function MasterDataImportBox() {
  const { reloadMasterData } = useMasterData()
  const [chosenFile, setChosenFile] = useState<ChosenFile | null>(null)
  const [sections, setSections] = useState<MasterDataSection[]>([])
  const [decisions, setDecisions] = useState<Record<string, UsersConflictDecision>>({})
  const [check, setCheck] = useState<MasterDataImportCheck | null>(null)
  const [checking, setChecking] = useState(false)
  const [importing, setImporting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  // Nur die Antwort der letzten Prüfung zählt, ältere kommen vielleicht später an
  const checkNumberRef = useRef(0)

  function runCheck(
    zip: string,
    nextSections: MasterDataSection[],
    nextDecisions: Record<string, UsersConflictDecision>,
    onChecked?: (result: MasterDataImportCheck) => void,
  ) {
    const checkNumber = ++checkNumberRef.current
    setChecking(true)
    setErrorMessage(null)
    checkMasterDataImport({ zip, sections: nextSections, usersConflictDecisions: nextDecisions })
      .then(({ data }) => {
        if (checkNumber === checkNumberRef.current) {
          setCheck(data)
          onChecked?.(data)
        }
      })
      .catch((error: unknown) => {
        if (checkNumber === checkNumberRef.current) {
          setErrorMessage(errorText(error))
        }
      })
      .finally(() => {
        if (checkNumber === checkNumberRef.current) {
          setChecking(false)
        }
      })
  }

  function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    // zurücksetzen, sonst löst dieselbe Datei ein zweites Mal kein change aus
    event.target.value = ''
    if (!file) {
      return
    }
    setCheck(null)
    setDecisions({})
    fileToBase64(file)
      .then((base64) => {
        setChosenFile({ name: file.name, size: file.size, base64 })
        // mit allen Bereichen prüfen, danach sind die angehakt, die im ZIP stecken
        runCheck(base64, ALL_MASTER_DATA_SECTIONS, {}, (result) =>
          setSections(result.sections.filter((sectionCheck) => sectionCheck.present).map(({ section }) => section)),
        )
      })
      .catch(() => setErrorMessage('die Datei ließ sich nicht lesen'))
  }

  function toggleSection(section: MasterDataSection) {
    if (chosenFile === null) {
      return
    }
    const nextSections = sections.includes(section)
      ? sections.filter((candidate) => candidate !== section)
      : ALL_MASTER_DATA_SECTIONS.filter((candidate) => sections.includes(candidate) || candidate === section)
    setSections(nextSections)
    runCheck(chosenFile.base64, nextSections, decisions)
  }

  function changeDecision(fileGuid: string, decision: UsersConflictDecision) {
    if (chosenFile === null) {
      return
    }
    const nextDecisions = { ...decisions, [fileGuid]: decision }
    setDecisions(nextDecisions)
    runCheck(chosenFile.base64, sections, nextDecisions)
  }

  function startImport() {
    if (chosenFile === null) {
      return
    }
    setImporting(true)
    setErrorMessage(null)
    importMasterData({ zip: chosenFile.base64, sections, usersConflictDecisions: decisions })
      .then(({ data }) => {
        const selectedChecks = data.sections.filter((sectionCheck) => sections.includes(sectionCheck.section))
        const written = selectedChecks.reduce((sum, sectionCheck) => sum + sectionCheck.newCount, 0)
        const existing = selectedChecks.reduce((sum, sectionCheck) => sum + sectionCheck.existingCount, 0)
        showToast({
          kind: 'success',
          title: 'Stammdaten importiert',
          text: `${written} Datensätze angelegt, ${existing} gab es schon.`,
        })
        reloadMasterData()
        // die Vorschau zeigt jetzt den neuen Stand: alles Importierte ist vorhanden
        runCheck(chosenFile.base64, sections, decisions)
      })
      .catch((error: unknown) => {
        setErrorMessage(errorText(error))
        showToast({ kind: 'error', title: 'Import fehlgeschlagen', text: errorText(error) })
      })
      .finally(() => setImporting(false))
  }

  const newTotal =
    check?.sections
      .filter((sectionCheck) => sections.includes(sectionCheck.section))
      .reduce((sum, sectionCheck) => sum + sectionCheck.newCount, 0) ?? 0
  const labelBySection = new Map(MASTER_DATA_SECTIONS.map(({ section, label }) => [section, label]))
  const busy = checking || importing

  const footer = (
    <>
      {errorMessage !== null && <span className="text-danger">{errorMessage}</span>}
      {checking && errorMessage === null && (
        <span className="spinner-border spinner-border-sm" role="status" aria-label="prüft" />
      )}
      <span className="ms-auto d-flex gap-2">
        <button
          type="button"
          className="button-save"
          disabled={busy || check === null || !check.ready || newTotal === 0}
          onClick={startImport}
        >
          <Icon name="upload" /> {newTotal > 0 ? `${newTotal} Datensätze importieren` : 'Importieren'}
        </button>
      </span>
    </>
  )

  return (
    <ContentBox
      title={
        <>
          <Icon name="upload" /> Import
        </>
      }
      footer={footer}
    >
      <p className="mb-3">
        Was es schon gibt, wird übersprungen und nie verändert. Neue User bekommen das Startpasswort 123, sofern es zu
        ihrer guid noch keine Zugangsdaten gibt.
      </p>
      <input
        className="form-control"
        type="file"
        accept=".zip,application/zip"
        aria-label="Export der Stammdaten wählen (ZIP)"
        disabled={busy}
        onChange={chooseFile}
      />

      {chosenFile !== null && check !== null && (
        <>
          <p className="import-meta mt-3">
            {chosenFile.name}, {Math.ceil(chosenFile.size / 1024)} KB
            {check.exportedAt !== null && <>, exportiert am {new Date(check.exportedAt).toLocaleString('de-DE')}</>}
            {check.exportedBy !== null && <> von {check.exportedBy}</>}
            {check.formatVersion !== null && <>, Format-Version {check.formatVersion}</>}
          </p>

          {check.blockers.length > 0 && (
            <div className="danger-callout">
              <Icon name="warning" />
              <div>
                <strong>Der Import ist blockiert.</strong>
                <ul>
                  {check.blockers.map((blocker) => (
                    <li key={blocker}>{blocker}</li>
                  ))}
                </ul>
              </div>
            </div>
          )}

          {check.adminGrants.length > 0 && (
            <div className="danger-callout">
              <Icon name="warning" />
              <div>
                <strong>Durch den Import bekämen ADMIN:</strong>
                <ul>
                  {check.adminGrants.map((adminGrant) => (
                    <li key={adminGrant}>{adminGrant}</li>
                  ))}
                </ul>
              </div>
            </div>
          )}

          {check.usersConflicts.length > 0 && (
            <div className="mb-3">
              <h3 className="h6">User mit gleicher E-Mail, aber anderer guid</h3>
              {check.usersConflicts.map((usersConflict) => (
                <UsersConflictCard
                  key={usersConflict.fileGuid}
                  usersConflict={usersConflict}
                  decision={decisions[usersConflict.fileGuid] ?? decisionOfConflict(usersConflict)}
                  disabled={busy}
                  onChange={(decision) => changeDecision(usersConflict.fileGuid, decision)}
                />
              ))}
            </div>
          )}

          {check.ready &&
            check.sections.map((sectionCheck) => (
              <MasterDataImportSection
                key={sectionCheck.section}
                sectionCheck={sectionCheck}
                label={labelBySection.get(sectionCheck.section) ?? sectionCheck.section}
                selected={sections.includes(sectionCheck.section)}
                disabled={busy}
                onToggle={() => toggleSection(sectionCheck.section)}
              />
            ))}
        </>
      )}
    </ContentBox>
  )
}
