import { Hoverlay } from '../../components/Hoverlay.tsx'
import { importStatusClass } from './importStatus.ts'
import { ROW_STATUS_TEXT, type RowStatus } from './MasterDataTransfer.ts'

/** Was mit einer Zeile des Imports passiert, als kleine Pille, die Erklärung im Hoverlay */
export function ImportStatusPill({ status }: { status: RowStatus }) {
  const { label, hint } = ROW_STATUS_TEXT[status]
  return (
    <Hoverlay text={hint}>
      <span className={importStatusClass(status)}>{label}</span>
    </Hoverlay>
  )
}
