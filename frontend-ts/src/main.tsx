import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, Navigate } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import './styles/basic.css'
import { Layout } from './layout/Layout.tsx'
import { ADMIN_ACCESS_ROLE_KEY } from './modul/accessrole/AccessRole.ts'
import { AccessRoleCollectionAccessRoleMatrixTab } from './modul/admin/AccessRoleCollectionAccessRoleMatrixTab.tsx'
import { AccessRoleCollectionsTab } from './modul/admin/AccessRoleCollectionsTab.tsx'
import { AccessRolesTab } from './modul/admin/AccessRolesTab.tsx'
import { EditAccessRoleCollectionTab } from './modul/admin/EditAccessRoleCollectionTab.tsx'
import { NewAccessRoleCollectionTab } from './modul/admin/NewAccessRoleCollectionTab.tsx'
import { Admin } from './modul/admin/Admin.tsx'
import { EditAccessRoleTab } from './modul/admin/EditAccessRoleTab.tsx'
import { EditFileExtensionCollectionTab } from './modul/admin/EditFileExtensionCollectionTab.tsx'
import { EditFileExtensionTab } from './modul/admin/EditFileExtensionTab.tsx'
import { EditFileSubClassTab } from './modul/admin/EditFileSubClassTab.tsx'
import { FileExtensionsTab } from './modul/admin/FileExtensionsTab.tsx'
import { FileMatrixTab } from './modul/admin/FileMatrixTab.tsx'
import { FileSubClassAclMatrixTab } from './modul/admin/FileSubClassAclMatrixTab.tsx'
import { MasterDataTab } from './modul/admin/MasterDataTab.tsx'
import { NewFileExtensionCollectionTab } from './modul/admin/NewFileExtensionCollectionTab.tsx'
import { NewFileExtensionTab } from './modul/admin/NewFileExtensionTab.tsx'
import { FileSubClassesTab } from './modul/admin/FileSubClassesTab.tsx'
import { NewFileSubClassTab } from './modul/admin/NewFileSubClassTab.tsx'
import { EditUsersTab } from './modul/admin/EditUsersTab.tsx'
import { NewAccessRoleTab } from './modul/admin/NewAccessRoleTab.tsx'
import { NewUsersTab } from './modul/admin/NewUsersTab.tsx'
import { UsersAccessRoleCollectionMatrixTab } from './modul/admin/UsersAccessRoleCollectionMatrixTab.tsx'
import { UsersTab } from './modul/admin/UsersTab.tsx'
import { Home } from './modul/home/Home.tsx'
import { ConfirmLoginPage } from './modul/login/ConfirmLoginPage.tsx'
import { LoginPage } from './modul/login/LoginPage.tsx'
import { RequireLogin } from './modul/login/RequireLogin.tsx'
import { EditProfileUsers } from './modul/profile/EditProfileUsers.tsx'
import { EditProfileUsersDetails } from './modul/profile/EditProfileUsersDetails.tsx'
import { Profile } from './modul/profile/Profile.tsx'
import { ProfileOverview } from './modul/profile/ProfileOverview.tsx'

const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <Home /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'login/confirm', element: <ConfirmLoginPage /> },
      {
        path: 'profile',
        element: (
          <RequireLogin>
            <Profile />
          </RequireLogin>
        ),
        children: [
          { index: true, element: <ProfileOverview /> },
          { path: 'edit/users', element: <EditProfileUsers /> },
          { path: 'edit/details', element: <EditProfileUsersDetails /> },
        ],
      },
      {
        path: 'admin',
        element: (
          <RequireLogin accessRoleKey={ADMIN_ACCESS_ROLE_KEY}>
            <Admin />
          </RequireLogin>
        ),
        children: [
          { index: true, element: <Navigate to="users" replace /> },
          { path: 'users', element: <UsersTab /> },
          { path: 'users/new', element: <NewUsersTab /> },
          { path: 'users/edit/:guid', element: <EditUsersTab /> },
          { path: 'accessroles', element: <AccessRolesTab /> },
          { path: 'accessroles/new', element: <NewAccessRoleTab /> },
          { path: 'accessroles/edit', element: <EditAccessRoleTab /> },
          { path: 'accessrolecollections', element: <AccessRoleCollectionsTab /> },
          { path: 'accessrolecollections/new', element: <NewAccessRoleCollectionTab /> },
          { path: 'accessrolecollections/edit', element: <EditAccessRoleCollectionTab /> },
          { path: 'users-arc', element: <UsersAccessRoleCollectionMatrixTab /> },
          { path: 'arc-ar', element: <AccessRoleCollectionAccessRoleMatrixTab /> },
          { path: 'filesubclasses', element: <FileSubClassesTab /> },
          { path: 'filesubclasses/new', element: <NewFileSubClassTab /> },
          { path: 'filesubclasses/edit', element: <EditFileSubClassTab /> },
          { path: 'fileextensions', element: <FileExtensionsTab /> },
          { path: 'fileextensions/new', element: <NewFileExtensionTab /> },
          { path: 'fileextensions/edit', element: <EditFileExtensionTab /> },
          { path: 'fileextensions/collections/new', element: <NewFileExtensionCollectionTab /> },
          { path: 'fileextensions/collections/edit', element: <EditFileExtensionCollectionTab /> },
          { path: 'filematrix', element: <FileMatrixTab /> },
          { path: 'fsc-acl', element: <FileSubClassAclMatrixTab /> },
          { path: 'masterdata', element: <MasterDataTab /> },
        ],
      },
    ],
  },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
)
