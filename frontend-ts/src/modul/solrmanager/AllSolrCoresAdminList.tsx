import { useTranslation } from 'react-i18next'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { Table, type TableColumn } from '../../components/Table.tsx'
import type { SolrCore } from './SolrCore.ts'
import { SolrCoreEmblem } from './SolrCoreEmblem.tsx'

interface AllSolrCoresAdminListProps {
  solrCores: SolrCore[]
  onEdit: (solrCore: SolrCore) => void
  onDelete: (solrCore: SolrCore) => void
  /** nach Drag and Drop: die Kerne in neuer Reihenfolge */
  onReorder: (solrCores: SolrCore[]) => void
}

/**
 * Tabelle aller Kerne für den Admin, ganz rechts die Aktionen Bearbeiten und Löschen. Zeigt nur an, lädt nichts: die
 * Daten kommen als Parameter, was die Aktionen tun, bestimmt der Aufrufer.
 */
export function AllSolrCoresAdminList({ solrCores, onEdit, onDelete, onReorder }: AllSolrCoresAdminListProps) {
  const { t } = useTranslation()

  const columns: TableColumn<SolrCore>[] = [
    { header: '#', cell: (solrCore) => solrCore.listingPosition, unimportant: true },
    { header: t('solrmanager.cores.columns.core'), cell: (solrCore) => <SolrCoreEmblem solrCore={solrCore} /> },
    { header: t('solrmanager.cores.columns.nameInSolr'), cell: (solrCore) => solrCore.key },
    {
      header: t('solrmanager.cores.columns.gradient'),
      cell: (solrCore) =>
        solrCore.look.gradient !== null ? t(`common.gradient.${solrCore.look.gradient}`) : t('common.default'),
      unimportant: true,
    },
  ]

  return (
    <Table
      columns={columns}
      rows={solrCores}
      onReorder={onReorder}
      rowKey={(solrCore) => solrCore.key}
      detailTitle={(solrCore) => solrCore.key}
      emptyText={t('solrmanager.cores.empty')}
      actions={(solrCore) => (
        <>
          <EditButton label={t('common.edit')} onClick={() => onEdit(solrCore)} />
          <DeleteButton label={t('common.deleteAction')} onClick={() => onDelete(solrCore)} />
        </>
      )}
    />
  )
}
