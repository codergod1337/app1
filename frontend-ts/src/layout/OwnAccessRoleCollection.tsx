import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMasterData } from '../components/masterDataContext.ts'
import { Popup } from '../components/Popup.tsx'
import { AccessRoleBadge } from '../modul/accessrole/AccessRoleBadge.tsx'
import { useSession } from '../modul/login/sessionContext.ts'

/**
 * Die eigene Position im header: die ARC aus dem Token als Badge, auf dem Handy nur kompakt. Eine ARC ist optional,
 * ohne steht dort „keine Position“ (auf dem Handy gar nichts). Ein Klick öffnet ein Popup mit der Position und allen
 * AR aus dem Token. Namen und Farben kommen aus den Stammdaten. Ohne Anmeldung zeigt es nichts.
 */
export function OwnAccessRoleCollection() {
  const { t } = useTranslation()
  const { sessionInfo } = useSession()
  const { accessRoles, accessRoleCollections } = useMasterData()
  const [open, setOpen] = useState(false)

  if (sessionInfo === null || !sessionInfo.loggedIn) {
    return null
  }

  const accessRoleCollectionKey = sessionInfo.accessRoleCollectionKey
  const accessRoleCollection =
    accessRoleCollectionKey !== null
      ? (accessRoleCollections?.find((candidate) => candidate.key === accessRoleCollectionKey) ?? null)
      : null

  /** Die ARC als Badge. Steht sie (noch) nicht in den Stammdaten, ihr key. */
  function position(size: 'full' | 'compact') {
    if (accessRoleCollection !== null) {
      return <AccessRoleBadge badge={accessRoleCollection} size={size} shape="pill" />
    }
    return accessRoleCollectionKey !== null ? <code>{accessRoleCollectionKey}</code> : t('layout.noPosition')
  }

  return (
    <>
      <button
        type="button"
        // ohne ARC auf dem Handy gar nicht, dort ist der Platz knapp
        className={accessRoleCollectionKey !== null ? 'header-position' : 'header-position d-none d-md-inline-flex'}
        aria-label={t('layout.showMyPosition')}
        onClick={() => setOpen(true)}
      >
        <span className="d-none d-md-inline-flex">{position('full')}</span>
        {accessRoleCollectionKey !== null && <span className="d-inline-flex d-md-none">{position('compact')}</span>}
      </button>
      <Popup open={open} title={t('layout.myPosition')} footer={t('layout.tokenNote')} onClose={() => setOpen(false)}>
        <dl className="details-list">
          <div>
            <dt>{t('layout.positionArc')}</dt>
            <dd>{position('full')}</dd>
          </div>
          <div>
            <dt>{t('layout.rightsAr')}</dt>
            <dd>
              {sessionInfo.accessRoleKeys.length === 0 ? (
                t('common.none')
              ) : (
                <span className="badge-list">
                  {sessionInfo.accessRoleKeys.map((accessRoleKey) => {
                    const accessRole = accessRoles?.find((candidate) => candidate.key === accessRoleKey)
                    return accessRole ? (
                      <AccessRoleBadge key={accessRoleKey} badge={accessRole} size="full" shape="rect" />
                    ) : (
                      <code key={accessRoleKey}>{accessRoleKey}</code>
                    )
                  })}
                </span>
              )}
            </dd>
          </div>
        </dl>
      </Popup>
    </>
  )
}
