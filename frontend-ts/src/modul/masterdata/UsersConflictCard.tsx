import { ImportStatusPill } from './ImportStatusPill.tsx'
import type { ConflictItem, UsersConflict, UsersConflictDecision } from './MasterDataTransfer.ts'

interface UsersConflictCardProps {
  usersConflict: UsersConflict
  /** die aktuelle Auswahl: was an unseren User geht */
  decision: UsersConflictDecision
  disabled: boolean
  onChange: (decision: UsersConflictDecision) => void
}

/**
 * Ein User aus der Datei, den es bei uns unter derselben E-Mail, aber anderer guid gibt. Unser User bleibt, wie er
 * ist. Was in der Datei an ihm hängt, lässt sich einzeln übernehmen, soweit unser User es noch nicht hat.
 */
export function UsersConflictCard({ usersConflict, decision, disabled, onChange }: UsersConflictCardProps) {
  const { fileGuid, existingGuid, label, details, settings, accessRoles, accessRoleCollection } = usersConflict

  /** Alles Neue übernehmen oder nichts */
  function takeAll(taken: boolean) {
    const isNew = (item: ConflictItem) => taken && item.status === 'NEW'
    onChange({
      details: details !== null && isNew(details),
      settingKeys: settings.filter(isNew).map((item) => item.key),
      accessRoleKeys: accessRoles.filter(isNew).map((item) => item.key),
      accessRoleCollection: accessRoleCollection !== null && isNew(accessRoleCollection),
    })
  }

  function toggleKey(keys: string[], key: string): string[] {
    return keys.includes(key) ? keys.filter((candidate) => candidate !== key) : [...keys, key]
  }

  /** Ein Teil zum An- und Abhaken. Übernehmen lässt sich nur, was unser User noch nicht hat. */
  function item(conflictItem: ConflictItem, itemLabel: string, checked: boolean, onToggle: () => void) {
    const id = `conflict-${fileGuid}-${itemLabel}`
    return (
      <div key={id} className="import-conflict-item">
        <input
          id={id}
          className="form-check-input mt-0"
          type="checkbox"
          checked={checked && conflictItem.status === 'NEW'}
          disabled={disabled || conflictItem.status !== 'NEW'}
          onChange={onToggle}
        />
        <label className="form-check-label" htmlFor={id}>
          {itemLabel}
        </label>
        <ImportStatusPill status={conflictItem.status} />
        {conflictItem.message !== null && conflictItem.status !== 'NEW' && (
          <span className="small">{conflictItem.message}</span>
        )}
      </div>
    )
  }

  return (
    <div className="import-conflict">
      <div className="d-flex flex-wrap align-items-center gap-2">
        <strong>{label}</strong>
        <span className="ms-auto d-flex gap-2">
          <button type="button" className="button-other" disabled={disabled} onClick={() => takeAll(true)}>
            alles Neue übernehmen
          </button>
          <button type="button" className="button-other" disabled={disabled} onClick={() => takeAll(false)}>
            nichts
          </button>
        </span>
      </div>
      <div className="small mt-1">
        in der Datei <code>{fileGuid}</code>, bei uns <code>{existingGuid}</code>. Unser User bleibt, Übernommenes geht
        an ihn.
      </div>
      <div className="import-conflict-items">
        {details !== null &&
          item(details, 'Details', decision.details, () => onChange({ ...decision, details: !decision.details }))}
        {settings.map((setting) =>
          item(setting, `Einstellung ${setting.key}`, decision.settingKeys.includes(setting.key), () =>
            onChange({ ...decision, settingKeys: toggleKey(decision.settingKeys, setting.key) }),
          ),
        )}
        {accessRoles.map((accessRole) =>
          item(accessRole, `AR ${accessRole.key}`, decision.accessRoleKeys.includes(accessRole.key), () =>
            onChange({ ...decision, accessRoleKeys: toggleKey(decision.accessRoleKeys, accessRole.key) }),
          ),
        )}
        {accessRoleCollection !== null &&
          item(accessRoleCollection, `ARC ${accessRoleCollection.key}`, decision.accessRoleCollection, () =>
            onChange({ ...decision, accessRoleCollection: !decision.accessRoleCollection }),
          )}
      </div>
    </div>
  )
}
