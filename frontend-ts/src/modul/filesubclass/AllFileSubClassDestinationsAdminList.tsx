import { useTranslation } from 'react-i18next'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import type { FileSubClassDestination } from './FileSubClassDestination.ts'

interface AllFileSubClassDestinationsAdminListProps {
  fileSubClassDestinations: FileSubClassDestination[]
  onEdit: (destination: FileSubClassDestination) => void
  onDelete: (destination: FileSubClassDestination) => void
  /** nach Drag and Drop: die Orte in neuer Reihenfolge */
  onReorder: (destinations: FileSubClassDestination[]) => void
}

/**
 * Tabelle aller Anzeigeorte für den Admin, ganz rechts die Aktionen Bearbeiten und Löschen; dazu je Ort, wie viele
 * Dateiarten ihn tragen. Zeigt nur an, lädt nichts: die Daten kommen als Parameter, was die Aktionen tun, bestimmt
 * der Aufrufer.
 */
export function AllFileSubClassDestinationsAdminList({ fileSubClassDestinations, onEdit, onDelete, onReorder }: AllFileSubClassDestinationsAdminListProps) {
  const { t } = useTranslation()
  const { fileSubClasses } = useMasterData()

  function fileSubClassCountOf(destination: FileSubClassDestination): number {
    return (fileSubClasses ?? []).filter((fileSubClass) => (fileSubClass.displayContextKeys ?? []).includes(destination.key)).length
  }

  const columns: TableColumn<FileSubClassDestination>[] = [
    { header: '#', cell: (destination) => destination.listingPosition, unimportant: true },
    { header: t('common.key'), cell: (destination) => <strong>{destination.key}</strong> },
    { header: t('common.displayName'), cell: (destination) => translate(destination.displayName) },
    { header: t('filesubclassdestination.description'), cell: (destination) => translate(destination.description), unimportant: true },
    { header: t('filesubclassdestination.fileSubClassCount'), cell: (destination) => fileSubClassCountOf(destination), unimportant: true, width: '7rem' },
  ]

  return (
    <Table
      columns={columns}
      rows={fileSubClassDestinations}
      onReorder={onReorder}
      rowKey={(destination) => destination.key}
      detailTitle={(destination) => destination.key}
      emptyText={t('filesubclassdestination.empty')}
      actions={(destination) => (
        <>
          <EditButton label={t('common.edit')} onClick={() => onEdit(destination)} />
          <DeleteButton label={t('common.deleteAction')} onClick={() => onDelete(destination)} />
        </>
      )}
    />
  )
}
