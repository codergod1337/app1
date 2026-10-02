import type { RowStatus } from './MasterDataTransfer.ts'

/** Die Farbe eines Status: neu, vorhanden, abgewählt, sonst ein Problem */
export function importStatusClass(status: RowStatus): string {
  switch (status) {
    case 'NEW':
      return 'import-status import-status-new'
    case 'EXISTING':
      return 'import-status'
    case 'SKIPPED':
      return 'import-status import-status-skipped'
    default:
      return 'import-status import-status-problem'
  }
}
