import { DeleteButton } from '../../components/DeleteButton.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { translate } from '../../components/multilingual.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import type { FileSubClass } from './FileSubClass.ts'
import { FileSubClassSymbol } from './FileSubClassSymbol.tsx'

interface AllFileSubClassesAdminListProps {
  fileSubClasses: FileSubClass[]
  onEdit: (fileSubClass: FileSubClass) => void
  onDelete: (fileSubClass: FileSubClass) => void
  /** nach Drag and Drop: die Dateiarten in neuer Reihenfolge */
  onReorder: (fileSubClasses: FileSubClass[]) => void
}

/**
 * Tabelle aller Dateiarten für den Admin, ganz rechts die Aktionen Bearbeiten und Löschen. Wer lesen und schreiben
 * darf, zeigt die FSC-ACL-Matrix. Zeigt nur an, lädt nichts: die Daten kommen als Parameter, was die Aktionen tun,
 * bestimmt der Aufrufer.
 */
export function AllFileSubClassesAdminList({
  fileSubClasses,
  onEdit,
  onDelete,
  onReorder,
}: AllFileSubClassesAdminListProps) {
  const columns: TableColumn<FileSubClass>[] = [
    { header: '#', cell: (fileSubClass) => fileSubClass.listingPosition, unimportant: true },
    { header: 'Symbol', cell: (fileSubClass) => <FileSubClassSymbol fileSubClass={fileSubClass} /> },
    { header: 'Key', cell: (fileSubClass) => fileSubClass.key },
    { header: 'Name', cell: (fileSubClass) => translate(fileSubClass.displayName), unimportant: true },
    {
      header: 'Endungen',
      cell: (fileSubClass) =>
        fileSubClass.extensions && fileSubClass.extensions.length > 0 ? fileSubClass.extensions.join(', ') : 'keine',
      unimportant: true,
    },
  ]

  return (
    <Table
      columns={columns}
      rows={fileSubClasses}
      onReorder={onReorder}
      rowKey={(fileSubClass) => fileSubClass.key}
      detailTitle={(fileSubClass) => fileSubClass.key}
      emptyText="keine Dateiarten"
      actions={(fileSubClass) => (
        <>
          <EditButton label="bearbeiten" onClick={() => onEdit(fileSubClass)} />
          <DeleteButton label="löschen" onClick={() => onDelete(fileSubClass)} />
        </>
      )}
    />
  )
}
