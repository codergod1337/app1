import { request } from '../api/client.ts'
import type { AccessRole } from '../modul/accessrole/AccessRole.ts'
import type { AccessRoleCollection } from '../modul/accessrolecollection/AccessRoleCollection.ts'
import type { FileExtension } from '../modul/fileextension/FileExtension.ts'
import type { FileExtensionCollection } from '../modul/fileextension/FileExtensionCollection.ts'
import type { FileSubClass } from '../modul/filesubclass/FileSubClass.ts'
import type { SolrCore } from '../modul/solrmanager/SolrCore.ts'
import type { SolrField } from '../modul/solrmanager/SolrField.ts'
import type { SolrHook } from '../modul/solrmanager/SolrHook.ts'
import type { SolrHookGroup } from '../modul/solrmanager/SolrHookGroup.ts'

/** Die Stammdaten, die alle Module kennen (MasterDataPublicController). Später kommen MSC dazu. */
export interface MasterData {
  /** sortiert nach listingPosition */
  accessRoles: AccessRole[]
  /** sortiert nach listingPosition */
  accessRoleCollections: AccessRoleCollection[]
  /** sortiert nach listingPosition */
  fileSubClasses: FileSubClass[]
  /** sortiert nach listingPosition, dann nach Endung */
  fileExtensions: FileExtension[]
  /** sortiert nach listingPosition */
  fileExtensionCollections: FileExtensionCollection[]
  /** sortiert nach listingPosition */
  solrHookGroups: SolrHookGroup[]
  /** sortiert nach listingPosition */
  solrHooks: SolrHook[]
  /** sortiert nach listingPosition */
  solrCores: SolrCore[]
  /** alle Kerne, sortiert nach Kern, listingPosition und name */
  solrFields: SolrField[]
}

/** Alle Stammdaten in einem Request, auch ohne Anmeldung */
export function getMasterData() {
  return request<MasterData>('GET', '/api/rest/v1/public/masterdata')
}
