import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeleteButton } from '../../components/DeleteButton.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { EditButton } from '../../components/EditButton.tsx'
import { useLanguage } from '../../components/languageContext.ts'
import { translate } from '../../components/multilingual.ts'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { Table, type TableColumn } from '../../components/Table.tsx'
import { type AbasInfosys, abasInfosysPlaceholderNamesOf, deleteAbasInfosys, useAbasInfosysList } from './AbasInfosys.ts'
import { INFOSYS_MANAGER_PATH } from './InfosysManager.tsx'
import type { EditAbasInfosysState } from './EditAbasInfosysTab.tsx'

export const ABAS_INFOSYS_PATH = `${INFOSYS_MANAGER_PATH}/calls`

/** Beim Verlassen dieses Tabs mitgeben, damit der header „zurück zu Infosysteme“ anbietet. Das label ist ein Schlüssel des Katalogs. */
const ORIGIN_STATE: OriginState = { origin: { path: ABAS_INFOSYS_PATH, label: 'infosys.tabs.calls' } }

/**
 * Tab „Aufrufe“ des Moduls Infosysteme (User 2026-10-10, eigenes Modul seit 2026-10-11): die hinterlegten Aufrufe, je
 * Zeile Key, Name, die Endung der URL und die Platzhalter, die ein Aufruf eingeben muss. Anlegen und Bearbeiten auf
 * eigenen Seiten, Löschen nach Sicherheitsabfrage. Ein Mapping-Eintrag, der auf einen gelöschten Key zeigt, meldet das
 * beim Lauf.
 */
export function AbasInfosysCallsTab() {
  const { t } = useTranslation()
  const { language } = useLanguage()
  const navigate = useNavigate()
  const { abasInfosysList, errorMessage, reload } = useAbasInfosysList()
  const [abasInfosysToDelete, setAbasInfosysToDelete] = useState<AbasInfosys | null>(null)

  const columns: TableColumn<AbasInfosys>[] = [
    { header: '#', cell: (abasInfosys) => abasInfosys.listingPosition, unimportant: true },
    { header: t('common.key'), cell: (abasInfosys) => <strong>{abasInfosys.key}</strong> },
    { header: t('common.displayName'), cell: (abasInfosys) => translate(abasInfosys.displayName, language), unimportant: true },
    {
      header: t('infosys.calls.columns.call'),
      cell: (abasInfosys) => (
        <code>
          {abasInfosys.infosysGroup}/{abasInfosys.infosysName}
        </code>
      ),
    },
    {
      header: t('infosys.calls.columns.placeholders'),
      cell: (abasInfosys) => {
        const names = abasInfosysPlaceholderNamesOf(abasInfosys.requestBodyTemplate)
        return names.length === 0 ? <span className="text-body-secondary">{t('common.none')}</span> : names.map((name) => <code key={name} className="me-1">{name}</code>)
      },
      unimportant: true,
    },
    {
      // die Anzeigeorte: ARTIKELMAPPE heißt, der Aufruf ist ein Tab der Mappe
      header: t('infosys.calls.columns.displayContextKeys'),
      cell: (abasInfosys) => {
        const keys = abasInfosys.displayContextKeys ?? []
        return keys.length === 0 ? <span className="text-body-secondary">{t('common.none')}</span> : keys.map((key) => <code key={key} className="me-1">{key}</code>)
      },
      unimportant: true,
    },
  ]

  function editAbasInfosys(abasInfosys: AbasInfosys) {
    const state: EditAbasInfosysState = { ...ORIGIN_STATE, abasInfosysKey: abasInfosys.key }
    navigate(`${ABAS_INFOSYS_PATH}/edit`, { state })
  }

  let footer
  if (errorMessage !== null) {
    footer = <span className="text-danger">{errorMessage}</span>
  } else if (abasInfosysList === null) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else {
    footer = <span>{t('infosys.calls.count', { count: abasInfosysList.length })}</span>
  }

  return (
    <>
      <ContentBox
        title={t('infosys.calls.title')}
        actions={<NewEntityButton entity="AbasInfosys" onClick={() => navigate(`${ABAS_INFOSYS_PATH}/new`, { state: ORIGIN_STATE })} />}
        footer={footer}
      >
        <p>{t('infosys.calls.intro')}</p>
        {abasInfosysList !== null && (
          <Table
            columns={columns}
            rows={abasInfosysList}
            rowKey={(abasInfosys) => abasInfosys.key}
            detailTitle={(abasInfosys) => abasInfosys.key}
            emptyText={t('infosys.calls.empty')}
            actions={(abasInfosys) => (
              <>
                <EditButton label={t('common.edit')} onClick={() => editAbasInfosys(abasInfosys)} />
                <DeleteButton label={t('common.deleteAction')} onClick={() => setAbasInfosysToDelete(abasInfosys)} />
              </>
            )}
          />
        )}
      </ContentBox>
      <DeletePopup
        open={abasInfosysToDelete !== null}
        title={t('infosys.calls.deleteTitle')}
        onConfirm={() => deleteAbasInfosys(abasInfosysToDelete?.key ?? '')}
        onDeleted={reload}
        onClose={() => setAbasInfosysToDelete(null)}
      >
        <Trans i18nKey="infosys.calls.deleteQuestion" values={{ key: abasInfosysToDelete?.key ?? '' }} components={{ strong: <strong /> }} />
      </DeletePopup>
    </>
  )
}
