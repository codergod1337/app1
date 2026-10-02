import { useEffect, useState, type FormEvent } from 'react'
import type { LanguageCode } from '../../branding/languages.ts'
import { KeyInput } from '../../components/KeyInput.tsx'
import { MultilingualInput } from '../../components/MultilingualInput.tsx'
import { BadgeFields } from '../accessrole/BadgeFields.tsx'
import { BadgeToggleList } from '../accessrole/BadgeToggleList.tsx'
import { randomBadgeStyle, randomSymbol } from '../accessrole/badgeStyle.ts'
import { ARC_KEY_PREFIX, type AccessRoleCollection, type NewAccessRoleCollectionData } from './AccessRoleCollection.ts'

/** Startwerte einer neuen ARC: zufällige Farben und Verlaufsart, damit man zum Testen schnell etwas anlegen kann. */
function newAccessRoleCollection(): NewAccessRoleCollectionData {
  return {
    key: '',
    displayName: '',
    description: null,
    slaveArcKeys: null,
    listingPosition: 0,
    ...randomBadgeStyle(),
  }
}

interface AccessRoleCollectionFormProps {
  /** id des form-Elements, damit ein Knopf außerhalb (z. B. im contentbox-footer) es abschicken kann */
  formId: string
  /** Startwerte beim Bearbeiten. Fehlen sie, startet das Formular mit zufälligem Aussehen (neue ARC). */
  initialAccessRoleCollection?: AccessRoleCollection
  /** alle ARCs, die übrigen stehen als Slaves zur Auswahl */
  accessRoleCollections: AccessRoleCollection[]
  /** Sprache der mehrsprachigen Felder, gewählt über die Flaggen der ContentBox */
  language: LanguageCode
  /** sperrt alle Felder, z. B. während gespeichert wird */
  disabled: boolean
  onSubmit: (accessRoleCollectionData: NewAccessRoleCollectionData) => void
}

/**
 * Eingabefelder einer AccessRoleCollection mit Live-Vorschau der Pillen-Badge, zum Anlegen und zum Bearbeiten.
 * Der key beginnt immer mit ARC_ und ist beim Bearbeiten fest. Als Slaves stehen alle anderen ARCs zur Wahl (nicht
 * rekursiv, nie sie selbst). Welche AR die ARC verleiht, steht nicht hier, das setzt die ARC-AR-Matrix. Speichert
 * nichts selbst: die Daten gehen an onSubmit.
 */
export function AccessRoleCollectionForm({
  formId,
  initialAccessRoleCollection,
  accessRoleCollections,
  language,
  disabled,
  onSubmit,
}: AccessRoleCollectionFormProps) {
  const editing = initialAccessRoleCollection !== undefined
  const [arc, setArc] = useState<NewAccessRoleCollectionData>(
    () => initialAccessRoleCollection ?? newAccessRoleCollection(),
  )

  // Neue ARC: dazu ein zufälliges Symbol
  useEffect(() => {
    if (editing) {
      return
    }
    let active = true
    randomSymbol().then((symbol) => {
      // nur, wenn inzwischen niemand selbst ein Symbol gewählt hat
      if (active) {
        setArc((current) => (current.symbol === null ? { ...current, symbol } : current))
      }
    })
    return () => {
      active = false
    }
  }, [editing])

  function change<K extends keyof NewAccessRoleCollectionData>(field: K, value: NewAccessRoleCollectionData[K]) {
    setArc((current) => ({ ...current, [field]: value }))
  }

  function submitAccessRoleCollection(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit(arc)
  }

  // Vorschau auch schon, solange Key und Name noch leer sind
  const previewBadge = {
    ...arc,
    key: arc.key || `${ARC_KEY_PREFIX}KEY`,
    displayName: arc.displayName || JSON.stringify({ de: 'Anzeigename' }),
  }
  // Slave kann jede andere ARC sein, nur nicht sie selbst
  const slaveCandidates = accessRoleCollections.filter((candidate) => candidate.key !== arc.key)

  return (
    <form id={formId} onSubmit={submitAccessRoleCollection}>
      <fieldset className="row g-3" disabled={disabled}>
        <div className="col-md-6">
          <label className="form-label" htmlFor="access-role-collection-key">
            Key
          </label>
          <KeyInput
            id="access-role-collection-key"
            prefix={ARC_KEY_PREFIX}
            required
            // der key ändert sich nie, sonst zeigten alle Verweise ins Leere
            disabled={disabled || editing}
            value={arc.key}
            onChange={(key) => change('key', key)}
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor="access-role-collection-display-name">
            Anzeigename
          </label>
          <MultilingualInput
            id="access-role-collection-display-name"
            required
            disabled={disabled}
            language={language}
            value={arc.displayName || null}
            onChange={(displayName) => change('displayName', displayName ?? '')}
          />
        </div>
        <div className="col-12">
          <label className="form-label" htmlFor="access-role-collection-description">
            Beschreibung
          </label>
          <MultilingualInput
            id="access-role-collection-description"
            disabled={disabled}
            language={language}
            value={arc.description}
            onChange={(description) => change('description', description)}
          />
        </div>
        <div className="col-md-3">
          <label className="form-label" htmlFor="access-role-collection-position">
            Position
          </label>
          <input
            id="access-role-collection-position"
            className="form-control"
            type="number"
            value={arc.listingPosition}
            onChange={(event) => change('listingPosition', Number(event.target.value))}
          />
        </div>

        <div className="col-12">
          <label className="form-label">Slaves: überwacht und vertritt diese ARCs</label>
          <BadgeToggleList
            badges={slaveCandidates}
            selectedKeys={arc.slaveArcKeys ?? []}
            shape="pill"
            disabled={disabled}
            onChange={(keys) => change('slaveArcKeys', keys.length > 0 ? keys : null)}
          />
        </div>

        <BadgeFields
          idPrefix="access-role-collection"
          badge={previewBadge}
          shape="pill"
          disabled={disabled}
          onChange={(changes) => setArc((current) => ({ ...current, ...changes }))}
        />
      </fieldset>
    </form>
  )
}
