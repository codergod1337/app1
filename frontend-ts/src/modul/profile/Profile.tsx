import { Outlet } from 'react-router'
import { Icon } from '../../components/Icon.tsx'
import { Subheader } from '../../components/Subheader.tsx'

/** Profilmodul: subheader, darunter die Übersicht oder eine der Bearbeiten-Seiten. */
export function Profile() {
  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="user" /> Profil
          </>
        }
      />
      <div className="main-content">
        <Outlet />
      </div>
    </>
  )
}
