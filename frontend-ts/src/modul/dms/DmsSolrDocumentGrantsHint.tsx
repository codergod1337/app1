import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Hoverlay } from '../../components/Hoverlay.tsx'
import { Icon } from '../../components/Icon.tsx'
import { ADMIN_ACCESS_ROLE_KEY } from '../accessrole/AccessRole.ts'
import { grantDateTimeOrUnlimited } from '../grant/grantDisplay.ts'
import { useSession } from '../login/sessionContext.ts'
import { exactResolveFieldValuesFromRegisterJson } from '../solrmanager/SolrExactResolve.ts'
import { getDmsSolrDocumentGrants, type DmsSolrDocumentGrantsForClient } from './Dms.ts'

/**
 * Der Kontrollblick für ADMIN (Vorgabe 2026-10-04, später weitere Rollen über denselben User-Endpunkt): Nach dem Laden
 * der Detailansicht fragt er für sich, ob dieses Dokument Freigaben trägt. Ja heißt ein kleines Symbol mit der Zahl
 * neben dem Dateinamen, im Hoverlay die User und ihre wirksamen Dateifreigaben im Kern DMS. Nein oder keine Berechtigung
 * heißt nichts, auch kein Fehler: Das Symbol ist eine Zugabe, kein Teil der Seite.
 */
export function DmsSolrDocumentGrantsHint({ id }: { id: string }) {
  const { t, i18n } = useTranslation()
  const { hasAccessRole } = useSession()
  const admin = hasAccessRole(ADMIN_ACCESS_ROLE_KEY)
  const [grants, setGrants] = useState<DmsSolrDocumentGrantsForClient | null>(null)

  useEffect(() => {
    if (!admin) {
      return
    }
    let stale = false
    getDmsSolrDocumentGrants(id)
      .then(({ data }) => {
        if (!stale) {
          setGrants(data)
        }
      })
      .catch(() => {
        if (!stale) {
          setGrants(null)
        }
      })
    return () => {
      stale = true
    }
  }, [id, admin])

  if (!admin || grants === null || grants.grantedUsersGuids.length === 0) {
    return null
  }

  const unlimited = t('grant.common.unlimited')
  const count = grants.grantedUsersGuids.length
  const text = (
    <span className="dms-identifiers">
      <span>{t('dms.document.grantsOpen', { count })}</span>
      {grants.grantedUsersGuids.map((guid) => (
        <span key={guid}>
          <strong>{grants.usersDisplayNameByGuid[guid] ?? guid}</strong>
          {grants.documentGrantsInForce
            .filter((grant) => grant.granteeUsersGuid === guid)
            .map((grant) => (
              <span key={grant.id} className="d-block">
                {Object.entries(exactResolveFieldValuesFromRegisterJson(grant.fieldValues))
                  .map(([name, values]) => `${name}: ${values.join(', ')}`)
                  .join(' · ')}{' '}
                · {grantDateTimeOrUnlimited(grant.validUntil, i18n.language, unlimited)}
              </span>
            ))}
        </span>
      ))}
      <span className="text-muted">{t('dms.document.grantsCheckHint')}</span>
    </span>
  )

  return (
    <Hoverlay text={text}>
      <span className="dms-grants-symbol text-warning" tabIndex={0} aria-label={t('dms.document.grantsSymbolLabel')}>
        <Icon name="documentGrants" />
        <span className="dms-grants-count">{count}</span>
      </span>
    </Hoverlay>
  )
}
