import type { CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'
import { useMasterData } from '../../components/masterDataContext.ts'
import { translate } from '../../components/multilingual.ts'
import { TODOJOB_STATUS_SCOPE, todojobAusgangLabel, todojobStatusColor, todojobValueOf, type TodojobDocument } from './Todojob.ts'

/** Der Status eines Jobs als Pille in seiner Farbe, der Name aus entity_state (Bereich TODOJOB); abgeschlossen mit Ausgang */
export function TodojobStatusBadge({ job }: { job: TodojobDocument }) {
  const { t } = useTranslation()
  const { entityStates } = useMasterData()
  const status = todojobValueOf(job, 'status')
  const ausgang = todojobValueOf(job, 'ausgang')
  const entityState = (entityStates ?? []).find((candidate) => candidate.key === status && candidate.scope === TODOJOB_STATUS_SCOPE)
  const name = entityState ? translate(entityState.displayName) || status : status
  return (
    <span className="todojob-status" style={{ '--todojob-color': todojobStatusColor(status) } as CSSProperties}>
      {name}
      {status === 'ABGESCHLOSSEN' && ausgang !== '' && ` · ${todojobAusgangLabel(t, ausgang)}`}
    </span>
  )
}
