import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, Navigate } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import './styles/basic.css'
// die festen Texte der Oberfläche, vor allem anderen, damit jede Komponente übersetzen kann
import './branding/i18n.ts'
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
import { EditSolrCoreTab } from './modul/solrmanager/EditSolrCoreTab.tsx'
import { EditSolrHookGroupTab } from './modul/solrmanager/EditSolrHookGroupTab.tsx'
import { EditSolrHookTab } from './modul/solrmanager/EditSolrHookTab.tsx'
import { NewSolrCoreTab } from './modul/solrmanager/NewSolrCoreTab.tsx'
import { NewSolrHookGroupTab } from './modul/solrmanager/NewSolrHookGroupTab.tsx'
import { NewSolrHookTab } from './modul/solrmanager/NewSolrHookTab.tsx'
import { SolrCoresTab } from './modul/solrmanager/SolrCoresTab.tsx'
import { SolrCoreTab } from './modul/solrmanager/SolrCoreTab.tsx'
import { SolrHookGroupsTab } from './modul/solrmanager/SolrHookGroupsTab.tsx'
import { SolrHooksTab } from './modul/solrmanager/SolrHooksTab.tsx'
import { SolrManager } from './modul/solrmanager/SolrManager.tsx'

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
      {
        path: 'solrmanager',
        element: (
          <RequireLogin accessRoleKey={ADMIN_ACCESS_ROLE_KEY}>
            <SolrManager />
          </RequireLogin>
        ),
        children: [
          { index: true, element: <Navigate to="hooks" replace /> },
          { path: 'hooks', element: <SolrHooksTab /> },
          { path: 'hooks/new', element: <NewSolrHookTab /> },
          { path: 'hooks/edit', element: <EditSolrHookTab /> },
          { path: 'hookgroups', element: <SolrHookGroupsTab /> },
          { path: 'hookgroups/new', element: <NewSolrHookGroupTab /> },
          { path: 'hookgroups/edit', element: <EditSolrHookGroupTab /> },
          { path: 'cores', element: <SolrCoresTab /> },
          { path: 'cores/new', element: <NewSolrCoreTab /> },
          { path: 'cores/edit', element: <EditSolrCoreTab /> },
          // je Kern ein Tab unter seinem key, die Felder werden direkt darin bearbeitet. HOOKS, HOOKGROUPS und CORES
          // sind als key reserviert, das prüft das Backend.
          { path: ':coreKey', element: <SolrCoreTab /> },
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
