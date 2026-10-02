import { translate } from '../../components/multilingual.ts'
import { importStatusClass } from './importStatus.ts'
import { ImportStatusPill } from './ImportStatusPill.tsx'
import type { SectionCheck } from './MasterDataTransfer.ts'

interface MasterDataImportSectionProps {
  sectionCheck: SectionCheck
  label: string
  /** zum Schreiben angehakt */
  selected: boolean
  disabled: boolean
  onToggle: () => void
}

/**
 * Ein Bereich in der Vorschau des Imports: oben anhaken und die Zähler, aufgeklappt jede Zeile mit Status und
 * Meldungen. Bereiche mit Problemen stehen offen.
 */
export function MasterDataImportSection({ sectionCheck, label, selected, disabled, onToggle }: MasterDataImportSectionProps) {
  const { section, present, newCount, existingCount, skippedCount, problemCount, rows } = sectionCheck

  return (
    <details className={selected ? 'import-section' : 'import-section unselected'} open={problemCount > 0}>
      <summary>
        <input
          className="form-check-input mt-0"
          type="checkbox"
          aria-label={`${label} importieren`}
          checked={selected}
          disabled={disabled || !present}
          onChange={onToggle}
        />
        <span className="fw-semibold">{label}</span>
        {!present && <span className="small">nicht im ZIP</span>}
        {present && !selected && <span className="small">wird nicht importiert</span>}
        <span className="import-counts">
          {newCount > 0 && <span className={importStatusClass('NEW')}>{newCount} neu</span>}
          {existingCount > 0 && <span className={importStatusClass('EXISTING')}>{existingCount} vorhanden</span>}
          {skippedCount > 0 && <span className={importStatusClass('SKIPPED')}>{skippedCount} abgewählt</span>}
          {problemCount > 0 && <span className={importStatusClass('INVALID')}>{problemCount} mit Problem</span>}
        </span>
      </summary>
      {rows.length > 0 && (
        <ul className="import-rows">
          {rows.map((row, index) => (
            // Zeilen mit doppeltem Schlüssel gibt es, deshalb mit Index
            <li key={`${section}-${row.id}-${index}`} className="import-row">
              <ImportStatusPill status={row.status} />
              <div>
                <span>{translate(row.displayName) || row.label}</span>
                {row.id !== row.label && <code>{row.id}</code>}
                {row.messages.length > 0 && (
                  <ul className="import-row-messages">
                    {row.messages.map((message) => (
                      <li key={message}>{message}</li>
                    ))}
                  </ul>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </details>
  )
}
