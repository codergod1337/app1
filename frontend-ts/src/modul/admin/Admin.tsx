import { Outlet, useLocation } from 'react-router'
import { Icon } from '../../components/Icon.tsx'
import { Subheader } from '../../components/Subheader.tsx'

/** Tabs mit einer Matrix: Sie steht ohne ContentBox direkt unter dem subheader, bündig an beiden Rändern. */
const MATRIX_PATHS = ['/admin/users-arc', '/admin/arc-ar', '/admin/filematrix', '/admin/fsc-acl']

/** Administrationsmodul: subheader mit den Tabs, darunter der gewählte Tab. */
export function Admin() {
  const { pathname } = useLocation()
  const isMatrix = MATRIX_PATHS.some((matrixPath) => pathname.startsWith(matrixPath))

  return (
    <>
      <Subheader
        status={
          <>
            <Icon name="admin" /> Administration
          </>
        }
        tabs={[
          { label: 'User', icon: 'users', to: '/admin/users' },
          { label: 'AR', icon: 'accessRoles', to: '/admin/accessroles' },
          { label: 'ARC', icon: 'accessRoleCollections', to: '/admin/accessrolecollections' },
          { label: 'User-ARC', icon: 'accessRoleCollections', to: '/admin/users-arc' },
          { label: 'ARC-AR', icon: 'accessRoleCollectionAccessRoles', to: '/admin/arc-ar' },
          { label: 'FSC', icon: 'fileSubClasses', to: '/admin/filesubclasses' },
          { label: 'Endungen', icon: 'fileExtensions', to: '/admin/fileextensions' },
          { label: 'FileMatrix', icon: 'fileMatrix', to: '/admin/filematrix' },
          { label: 'FSC-ACL', icon: 'fileSubClassAcl', to: '/admin/fsc-acl' },
          { label: 'Stammdaten', icon: 'masterData', to: '/admin/masterdata' },
        ]}
        sticky
      />
      <div className={isMatrix ? 'main-matrix' : 'main-content'}>
        <Outlet />
      </div>
    </>
  )
}
