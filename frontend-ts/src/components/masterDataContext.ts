import { createContext, useContext } from 'react'
import type { AccessRole } from '../modul/accessrole/AccessRole.ts'
import type { AccessRoleCollection } from '../modul/accessrolecollection/AccessRoleCollection.ts'
import type { FileExtension } from '../modul/fileextension/FileExtension.ts'
import type { FileExtensionCollection } from '../modul/fileextension/FileExtensionCollection.ts'
import type { FileSubClass } from '../modul/filesubclass/FileSubClass.ts'
import type { SolrCore } from '../modul/solrmanager/SolrCore.ts'
import type { SolrField } from '../modul/solrmanager/SolrField.ts'
import type { SolrHook } from '../modul/solrmanager/SolrHook.ts'
import type { SolrHookGroup } from '../modul/solrmanager/SolrHookGroup.ts'

/** Die Stammdaten für alle Module: useMasterData() */
export interface MasterDataState {
  /** null, solange sie beim Start laden */
  accessRoles: AccessRole[] | null
  /** null, solange sie beim Start laden */
  accessRoleCollections: AccessRoleCollection[] | null
  /** null, solange sie beim Start laden */
  fileSubClasses: FileSubClass[] | null
  /** null, solange sie beim Start laden */
  fileExtensions: FileExtension[] | null
  /** null, solange sie beim Start laden */
  fileExtensionCollections: FileExtensionCollection[] | null
  /** null, solange sie beim Start laden */
  solrHookGroups: SolrHookGroup[] | null
  /** null, solange sie beim Start laden */
  solrHooks: SolrHook[] | null
  /** null, solange sie beim Start laden */
  solrCores: SolrCore[] | null
  /** null, solange sie beim Start laden. Alle Kerne, sortiert nach Kern, listingPosition und name */
  solrFields: SolrField[] | null
  errorMessage: string | null
  /** neu laden, z. B. nachdem ein Admin eine AR, ARC, Dateiart oder einen Hook angelegt, geändert, verschoben oder gelöscht hat */
  reloadMasterData: () => void
}

export const MasterDataContext = createContext<MasterDataState | null>(null)

/** Die Stammdaten für alle Module. Nur innerhalb des MasterDataProvider. */
export function useMasterData(): MasterDataState {
  const masterDataState = useContext(MasterDataContext)
  if (masterDataState === null) {
    throw new Error('useMasterData nur innerhalb des MasterDataProvider')
  }
  return masterDataState
}
