import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { RequestError } from '../../api/client.ts'
import { ContentBox } from '../../components/ContentBox.tsx'
import { DeletePopup } from '../../components/DeletePopup.tsx'
import { NewEntityButton } from '../../components/NewEntityButton.tsx'
import type { OriginState } from '../../components/Origin.ts'
import { positionsByKey } from '../../components/positions.ts'
import { AllGraphMailTemplatesAdminList } from './AllGraphMailTemplatesAdminList.tsx'
import type { EditGraphMailTemplateState } from './EditGraphMailTemplateTab.tsx'
import { changeGraphMailTemplatePositions, deleteGraphMailTemplate, type GraphMailTemplate } from './GraphMailTemplate.ts'
import { useAllGraphMailTemplates } from './useGraphMailTemplates.ts'

/** Beim Verlassen dieses Tabs mitgeben, damit der header „zurück zu Mailvorlagen“ anbietet. Das label ist ein Schlüssel des Katalogs. */
const ORIGIN_STATE: OriginState = { origin: { path: '/graph/mailtemplates', label: 'graph.tabs.mailTemplates' } }

/** Tab „Mailvorlagen“ des GraphManagers: die Vorlagen der Mails, die das System über Graph verschickt. */
export function GraphMailTemplatesTab() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { graphMailTemplates, errorMessage, reload } = useAllGraphMailTemplates()
  // Nach Drag and Drop sofort die neue Reihenfolge zeigen, ohne auf das Neuladen zu warten
  const [reorderedGraphMailTemplates, setReorderedGraphMailTemplates] = useState<GraphMailTemplate[] | null>(null)
  const [positionErrorMessage, setPositionErrorMessage] = useState<string | null>(null)
  // Sicherheitsabfrage vor dem Löschen: null, solange keine offen ist
  const [graphMailTemplateToDelete, setGraphMailTemplateToDelete] = useState<GraphMailTemplate | null>(null)

  const shownGraphMailTemplates = reorderedGraphMailTemplates ?? graphMailTemplates

  /** Positionen neu durchzählen (1, 2, 3 …) und alle auf einmal speichern, danach neu laden. */
  function reorderGraphMailTemplates(newOrder: GraphMailTemplate[]) {
    const positions = positionsByKey(newOrder, (graphMailTemplate) => graphMailTemplate.key)
    setReorderedGraphMailTemplates(
      newOrder.map((graphMailTemplate) => ({ ...graphMailTemplate, listingPosition: positions[graphMailTemplate.key] })),
    )
    setPositionErrorMessage(null)
    changeGraphMailTemplatePositions(positions)
      .then((result) => setReorderedGraphMailTemplates(result.data))
      .catch((error: unknown) => {
        setPositionErrorMessage(error instanceof RequestError ? error.message : t('common.unknownError'))
        setReorderedGraphMailTemplates(null)
      })
      .finally(reload)
  }

  let footer
  if (errorMessage !== null || positionErrorMessage !== null) {
    footer = <span className="text-danger">{errorMessage ?? positionErrorMessage}</span>
  } else if (shownGraphMailTemplates === null) {
    footer = <span className="spinner-border spinner-border-sm" role="status" aria-label={t('common.loading')} />
  } else {
    footer = <span>{t('graph.mailTemplates.count', { count: shownGraphMailTemplates.length })}</span>
  }

  const newGraphMailTemplateButton = (
    <NewEntityButton
      entity="GraphMailTemplate"
      onClick={() => navigate('/graph/mailtemplates/new', { state: ORIGIN_STATE })}
    />
  )

  function editGraphMailTemplate(graphMailTemplate: GraphMailTemplate) {
    const state: EditGraphMailTemplateState = { ...ORIGIN_STATE, graphMailTemplateKey: graphMailTemplate.key }
    navigate('/graph/mailtemplates/edit', { state })
  }

  return (
    <>
      <ContentBox title={t('graph.mailTemplates.title')} actions={newGraphMailTemplateButton} footer={footer}>
        <p>{t('graph.mailTemplates.intro')}</p>
        {shownGraphMailTemplates !== null && (
          <AllGraphMailTemplatesAdminList
            graphMailTemplates={shownGraphMailTemplates}
            onEdit={editGraphMailTemplate}
            onDelete={setGraphMailTemplateToDelete}
            onReorder={reorderGraphMailTemplates}
          />
        )}
      </ContentBox>
      <DeletePopup
        open={graphMailTemplateToDelete !== null}
        title={t('graph.mailTemplates.deleteTitle')}
        onConfirm={() => deleteGraphMailTemplate(graphMailTemplateToDelete?.key ?? '')}
        onDeleted={() => {
          setReorderedGraphMailTemplates(null)
          reload()
        }}
        onClose={() => setGraphMailTemplateToDelete(null)}
      >
        <Trans
          i18nKey="graph.mailTemplates.deleteQuestion"
          values={{ key: graphMailTemplateToDelete?.key ?? '' }}
          components={{ strong: <strong /> }}
        />
      </DeletePopup>
    </>
  )
}
