import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, Navigate } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import './styles/basic.css'
// die festen Texte der Oberfläche, vor allem anderen, damit jede Komponente übersetzen kann
import './branding/i18n.ts'
import { Layout } from './layout/Layout.tsx'
import { AbasManager } from './modul/abasmanager/AbasManager.tsx'
import { AbasSternzeitOverviewTab } from './modul/abasmanager/AbasSternzeitOverviewTab.tsx'
import { ADMIN_ACCESS_ROLE_KEY } from './modul/accessrole/AccessRole.ts'
import { AccessRoleCollectionAccessRoleMatrixTab } from './modul/admin/AccessRoleCollectionAccessRoleMatrixTab.tsx'
import { AccessRoleCollectionsTab } from './modul/admin/AccessRoleCollectionsTab.tsx'
import { AccessRolesTab } from './modul/admin/AccessRolesTab.tsx'
import { EditAccessRoleCollectionTab } from './modul/admin/EditAccessRoleCollectionTab.tsx'
import { NewAccessRoleCollectionTab } from './modul/admin/NewAccessRoleCollectionTab.tsx'
import { Admin } from './modul/admin/Admin.tsx'
import { EditAccessRoleTab } from './modul/admin/EditAccessRoleTab.tsx'
import { EditEntityStateTab } from './modul/admin/EditEntityStateTab.tsx'
import { EntityStatesTab } from './modul/admin/EntityStatesTab.tsx'
import { NewEntityStateTab } from './modul/admin/NewEntityStateTab.tsx'
import { EditFileExtensionCollectionTab } from './modul/admin/EditFileExtensionCollectionTab.tsx'
import { EditFileExtensionTab } from './modul/admin/EditFileExtensionTab.tsx'
import { EditFileSubClassDestinationTab } from './modul/admin/EditFileSubClassDestinationTab.tsx'
import { EditFileSubClassTab } from './modul/admin/EditFileSubClassTab.tsx'
import { FileSubClassDestinationsTab } from './modul/admin/FileSubClassDestinationsTab.tsx'
import { NewFileSubClassDestinationTab } from './modul/admin/NewFileSubClassDestinationTab.tsx'
import { FileExtensionsTab } from './modul/admin/FileExtensionsTab.tsx'
import { FileSubClassMatrixTab } from './modul/admin/FileSubClassMatrixTab.tsx'
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
import { ARTIKEL_ACCESS_ROLE_KEY } from './modul/artikel/Artikel.ts'
import { ArtikelEntryPage } from './modul/artikel/ArtikelEntryPage.tsx'
import { ArtikelmappePage } from './modul/artikel/ArtikelmappePage.tsx'
import { BelegnummerPage } from './modul/belegnummer/BelegnummerPage.tsx'
import { Dms } from './modul/dms/Dms.tsx'
import { DMS_ACCESS_ROLE_KEY, DMS_SEARCH_ACCESS_ROLE_KEY } from './modul/dms/Dms.ts'
import { DmsExactResolveTab } from './modul/dms/DmsExactResolveTab.tsx'
import { DmsMyGrantsTab } from './modul/dms/DmsMyGrantsTab.tsx'
import { DmsSharedDocumentPage } from './modul/dms/DmsSharedDocumentPage.tsx'
import { DmsShareOverviewPage } from './modul/dms/DmsShareOverviewPage.tsx'
import { DmsSolrDocumentPage } from './modul/dms/DmsSolrDocumentPage.tsx'
import { DmsSolrDocumentViewerPage } from './modul/dms/DmsSolrDocumentViewerPage.tsx'
import { DmsUploadBatchTab } from './modul/dms/DmsUploadBatchTab.tsx'
import { DmsUploadTab } from './modul/dms/DmsUploadTab.tsx'
import { EditGraphMailTemplateTab } from './modul/graph/EditGraphMailTemplateTab.tsx'
import { GraphMailTemplatesTab } from './modul/graph/GraphMailTemplatesTab.tsx'
import { GraphMailTestSendTab } from './modul/graph/GraphMailTestSendTab.tsx'
import { GraphManager } from './modul/graph/GraphManager.tsx'
import { NewGraphMailTemplateTab } from './modul/graph/NewGraphMailTemplateTab.tsx'
import { Home } from './modul/home/Home.tsx'
import { AbasInfosysCallsTab } from './modul/infosys/AbasInfosysCallsTab.tsx'
import { EditAbasInfosysTab } from './modul/infosys/EditAbasInfosysTab.tsx'
import { InfosysManager } from './modul/infosys/InfosysManager.tsx'
import { InfosysRunTab } from './modul/infosys/InfosysRunTab.tsx'
import { NewAbasInfosysTab } from './modul/infosys/NewAbasInfosysTab.tsx'
import { KONTAKTE_ACCESS_ROLE_KEY } from './modul/kontakte/Kontakte.ts'
import { KontakteMappePage } from './modul/kontakte/KontakteMappePage.tsx'
import { ConfirmLoginPage } from './modul/login/ConfirmLoginPage.tsx'
import { LoginPage } from './modul/login/LoginPage.tsx'
import { RequireLogin } from './modul/login/RequireLogin.tsx'
import { Mapping } from './modul/mapping/Mapping.tsx'
import { EditResponseMappingTab } from './modul/mapping/EditResponseMappingTab.tsx'
import { MappingAbasFetchTab } from './modul/mapping/MappingAbasFetchTab.tsx'
import { MappingExecuteTab } from './modul/mapping/MappingExecuteTab.tsx'
import { MappingJsonViewTab } from './modul/mapping/MappingJsonViewTab.tsx'
import { MappingOverviewTab } from './modul/mapping/MappingOverviewTab.tsx'
import { EditProfileUsers } from './modul/profile/EditProfileUsers.tsx'
import { EditProfileUsersDetails } from './modul/profile/EditProfileUsersDetails.tsx'
import { Profile } from './modul/profile/Profile.tsx'
import { ProfileOverview } from './modul/profile/ProfileOverview.tsx'
import { UsersProfilePage } from './modul/profile/UsersProfilePage.tsx'
import { Search } from './modul/search/Search.tsx'
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
          // das Profil eines anderen Users: Ziel jedes angezeigten Namens (UsersGuidName)
          { path: 'user/:guid', element: <UsersProfilePage /> },
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
          { path: 'filesubclassdestinations', element: <FileSubClassDestinationsTab /> },
          { path: 'filesubclassdestinations/new', element: <NewFileSubClassDestinationTab /> },
          { path: 'filesubclassdestinations/edit', element: <EditFileSubClassDestinationTab /> },
          { path: 'fileextensions', element: <FileExtensionsTab /> },
          { path: 'fileextensions/new', element: <NewFileExtensionTab /> },
          { path: 'fileextensions/edit', element: <EditFileExtensionTab /> },
          { path: 'fileextensions/collections/new', element: <NewFileExtensionCollectionTab /> },
          { path: 'fileextensions/collections/edit', element: <EditFileExtensionCollectionTab /> },
          { path: 'fsc-matrix', element: <FileSubClassMatrixTab /> },
          { path: 'fsc-acl', element: <FileSubClassAclMatrixTab /> },
          { path: 'entitystates', element: <EntityStatesTab /> },
          { path: 'entitystates/new', element: <NewEntityStateTab /> },
          { path: 'entitystates/edit', element: <EditEntityStateTab /> },
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
      {
        path: 'abasmanager',
        element: (
          <RequireLogin accessRoleKey={ADMIN_ACCESS_ROLE_KEY}>
            <AbasManager />
          </RequireLogin>
        ),
        children: [
          { index: true, element: <Navigate to="overview" replace /> },
          { path: 'overview', element: <AbasSternzeitOverviewTab /> },
        ],
      },
      {
        // die hinterlegten Infosystem-Aufrufe, ein eigenes Modul neben AbasManager und Mapping (User 2026-10-11)
        path: 'infosys',
        element: (
          <RequireLogin accessRoleKey={ADMIN_ACCESS_ROLE_KEY}>
            <InfosysManager />
          </RequireLogin>
        ),
        children: [
          { index: true, element: <Navigate to="calls" replace /> },
          { path: 'calls', element: <AbasInfosysCallsTab /> },
          { path: 'calls/new', element: <NewAbasInfosysTab /> },
          { path: 'calls/edit', element: <EditAbasInfosysTab /> },
          { path: 'run', element: <InfosysRunTab /> },
        ],
      },
      {
        path: 'mapping',
        element: (
          <RequireLogin accessRoleKey={ADMIN_ACCESS_ROLE_KEY}>
            <Mapping />
          </RequireLogin>
        ),
        children: [
          { index: true, element: <Navigate to="overview" replace /> },
          { path: 'overview', element: <MappingOverviewTab /> },
          { path: 'edit', element: <EditResponseMappingTab /> },
          { path: 'json', element: <MappingJsonViewTab /> },
          { path: 'abas', element: <MappingAbasFetchTab /> },
          { path: 'execute', element: <MappingExecuteTab /> },
        ],
      },
      {
        path: 'graph',
        element: (
          <RequireLogin accessRoleKey={ADMIN_ACCESS_ROLE_KEY}>
            <GraphManager />
          </RequireLogin>
        ),
        children: [
          { index: true, element: <Navigate to="mailtemplates" replace /> },
          { path: 'mailtemplates', element: <GraphMailTemplatesTab /> },
          { path: 'mailtemplates/new', element: <NewGraphMailTemplateTab /> },
          { path: 'mailtemplates/edit', element: <EditGraphMailTemplateTab /> },
          { path: 'testmail', element: <GraphMailTestSendTab /> },
        ],
      },
      {
        path: 'dms',
        element: (
          <RequireLogin accessRoleKey={DMS_ACCESS_ROLE_KEY}>
            <Dms />
          </RequireLogin>
        ),
        children: [
          { index: true, element: <Navigate to="upload" replace /> },
          { path: 'upload', element: <DmsUploadBatchTab /> },
          { path: 'probe', element: <DmsUploadTab /> },
          { path: 'resolve', element: <DmsExactResolveTab /> },
          { path: 'my-grants', element: <DmsMyGrantsTab /> },
          // ein einzelnes Dokument: in der URL steht nur die id, das Backend prüft die Leseprüfung des Users
          { path: 'solr-document/:id', element: <DmsSolrDocumentPage mode="details" /> },
          { path: 'solr-document/:id/edit', element: <DmsSolrDocumentPage mode="edit" /> },
          // der feste Verweis über die Versionskette: die Seite löst den Kopf auf und zeigt die geltende Fassung
          { path: 'version-chain/:versionChain', element: <DmsSolrDocumentPage mode="details" /> },
        ],
      },
      {
        // Artikel: der Einstieg ohne Nummer, dann die Mappe mit ihren Tabs. Jede Seite trägt ihren eigenen subheader,
        // denn die Tabs der Mappe hängen am geladenen Artikel (Tabs ohne Daten gibt es nicht)
        path: 'artikel',
        children: [
          {
            index: true,
            element: (
              <RequireLogin accessRoleKey={ARTIKEL_ACCESS_ROLE_KEY}>
                <ArtikelEntryPage />
              </RequireLogin>
            ),
          },
          {
            path: ':artikelNummer/:tab?',
            element: (
              <RequireLogin accessRoleKey={ARTIKEL_ACCESS_ROLE_KEY}>
                <ArtikelmappePage />
              </RequireLogin>
            ),
          },
        ],
      },
      {
        // die Kunden- und die Lieferantenmappe (User 2026-10-13): keine Kachel, der Weg hinein sind die Badges der
        // Nummern und die Suche; eine Seite, die Rolle kommt aus der Route. key je Rolle, damit ein Wechsel neu aufbaut
        path: 'kunde/:nummer/:tab?',
        element: (
          <RequireLogin accessRoleKey={KONTAKTE_ACCESS_ROLE_KEY}>
            <KontakteMappePage key="KUNDE" partnerRole="KUNDE" />
          </RequireLogin>
        ),
      },
      {
        path: 'lieferant/:nummer/:tab?',
        element: (
          <RequireLogin accessRoleKey={KONTAKTE_ACCESS_ROLE_KEY}>
            <KontakteMappePage key="LIEFERANT" partnerRole="LIEFERANT" />
          </RequireLogin>
        ),
      },
      {
        // der Viewer außerhalb des DMS-Moduls, wie in der Vorlage: eigener subheader mit den Angaben zur Datei, keine
        // Tabs, keine ContentBox, die Datei bekommt die ganze Fläche. In der URL steht nur die id.
        path: 'dms/viewer/:id',
        element: (
          <RequireLogin accessRoleKey={DMS_ACCESS_ROLE_KEY}>
            <DmsSolrDocumentViewerPage />
          </RequireLogin>
        ),
      },
      {
        // die Station „Detailansicht“ eines Abholscheins: ohne Anmeldung, der Link aus dem Freigabe-Popup führt hierher
        path: 'share/dms/document/:token',
        element: <DmsSharedDocumentPage />,
      },
      {
        // die Suche über alle Kerne. Die Route verlangt die Such-Rolle des ersten Kerns, welche Kerne der User
        // durchsucht, entscheidet die Maske je Kern aus seinen Rollen
        path: 'search',
        element: (
          <RequireLogin accessRoleKey={DMS_SEARCH_ACCESS_ROLE_KEY}>
            <Search />
          </RequireLogin>
        ),
      },
      {
        // Belegnummer: eine Nummer bei ABAS nachschlagen, für jeden Angemeldeten, keine AR (User 2026-10-11)
        path: 'belegnummer',
        element: (
          <RequireLogin>
            <BelegnummerPage />
          </RequireLogin>
        ),
      },
      {
        // Freigaben: alle Abholscheine des DMS. Die Tür (ADMIN oder DMS_PUBLIC) prüft das Backend, die Seite zeigt sonst
        // seine Absage
        path: 'share',
        element: (
          <RequireLogin>
            <DmsShareOverviewPage />
          </RequireLogin>
        ),
      },
    ],
  },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
)
