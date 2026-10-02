import { useEffect, useState, type FormEvent } from 'react'
import type { LanguageCode } from '../../branding/languages.ts'
import { KeyInput } from '../../components/KeyInput.tsx'
import { MultilingualInput } from '../../components/MultilingualInput.tsx'
import type { AccessRole, NewAccessRoleData } from './AccessRole.ts'
import { BadgeFields } from './BadgeFields.tsx'
import { randomBadgeStyle, randomSymbol } from './badgeStyle.ts'

/** Startwerte einer neuen AccessRole: zufällige Farben und Verlaufsart, damit man zum Testen schnell etwas anlegen kann. */
function newAccessRole(): NewAccessRoleData {
  return {
    key: '',
    displayName: '',
    description: null,
    system: false,
    listingPosition: 0,
    ...randomBadgeStyle(),
  }
}

interface AccessRoleFormProps {
  /** id des form-Elements, damit ein Knopf außerhalb (z. B. im contentbox-footer) es abschicken kann */
  formId: string
  /** Startwerte beim Bearbeiten. Fehlen sie, startet das Formular mit zufälligem Aussehen (neue AccessRole). */
  initialAccessRole?: AccessRole
  /** Sprache der mehrsprachigen Felder, gewählt über die Flaggen der ContentBox */
  language: LanguageCode
  /** sperrt alle Felder, z. B. während gespeichert wird */
  disabled: boolean
  onSubmit: (accessRoleData: NewAccessRoleData) => void
}

/**
 * Eingabefelder einer AccessRole mit Live-Vorschau der Badge, zum Anlegen und zum Bearbeiten.
 * Beim Bearbeiten ist der key fest, und system lässt sich nicht mehr zurücknehmen.
 * Speichert nichts selbst: die Daten gehen an onSubmit.
 */
export function AccessRoleForm({ formId, initialAccessRole, language, disabled, onSubmit }: AccessRoleFormProps) {
  const editing = initialAccessRole !== undefined
  const [accessRole, setAccessRole] = useState<NewAccessRoleData>(() => initialAccessRole ?? newAccessRole())

  // Neue AccessRole: dazu ein zufälliges Symbol
  useEffect(() => {
    if (editing) {
      return
    }
    let active = true
    randomSymbol().then((symbol) => {
      // nur, wenn inzwischen niemand selbst ein Symbol gewählt hat
      if (active) {
        setAccessRole((current) => (current.symbol === null ? { ...current, symbol } : current))
      }
    })
    return () => {
      active = false
    }
  }, [editing])

  function change<K extends keyof NewAccessRoleData>(field: K, value: NewAccessRoleData[K]) {
    setAccessRole((current) => ({ ...current, [field]: value }))
  }

  function submitAccessRole(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit(accessRole)
  }

  // Vorschau auch schon, solange Key und Name noch leer sind
  const previewBadge = {
    ...accessRole,
    key: accessRole.key || 'KEY',
    displayName: accessRole.displayName || JSON.stringify({ de: 'Anzeigename' }),
  }

  return (
    <form id={formId} onSubmit={submitAccessRole}>
      <fieldset className="row g-3" disabled={disabled}>
        <div className="col-md-6">
          <label className="form-label" htmlFor="access-role-key">
            Key
          </label>
          <KeyInput
            id="access-role-key"
            required
            // der key ändert sich nie, sonst zeigten alle Verweise ins Leere
            disabled={disabled || editing}
            value={accessRole.key}
            onChange={(key) => change('key', key)}
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor="access-role-display-name">
            Anzeigename
          </label>
          <MultilingualInput
            id="access-role-display-name"
            required
            disabled={disabled}
            language={language}
            value={accessRole.displayName || null}
            onChange={(displayName) => change('displayName', displayName ?? '')}
          />
        </div>
        <div className="col-12">
          <label className="form-label" htmlFor="access-role-description">
            Beschreibung
          </label>
          <MultilingualInput
            id="access-role-description"
            disabled={disabled}
            language={language}
            value={accessRole.description}
            onChange={(description) => change('description', description)}
          />
        </div>
        <div className="col-md-3">
          <label className="form-label" htmlFor="access-role-position">
            Position
          </label>
          <input
            id="access-role-position"
            className="form-control"
            type="number"
            value={accessRole.listingPosition}
            onChange={(event) => change('listingPosition', Number(event.target.value))}
          />
        </div>
        <div className="col-md-9 d-flex align-items-end">
          <div className="form-check">
            <input
              id="access-role-system"
              className="form-check-input"
              type="checkbox"
              // system darf nur von false auf true wechseln, nie zurück
              disabled={initialAccessRole?.system === true}
              checked={accessRole.system}
              onChange={(event) => change('system', event.target.checked)}
            />
            <label className="form-check-label" htmlFor="access-role-system">
              System (key wird im Code verwendet)
            </label>
          </div>
        </div>

        <BadgeFields
          idPrefix="access-role"
          badge={previewBadge}
          shape="rect"
          disabled={disabled}
          onChange={(changes) => setAccessRole((current) => ({ ...current, ...changes }))}
        />
      </fieldset>
    </form>
  )
}
