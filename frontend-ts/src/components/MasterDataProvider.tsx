import { useMemo, type ReactNode } from 'react'
import { useLoad } from '../api/useLoad.ts'
import { getMasterData } from './MasterData.ts'
import { MasterDataContext, type MasterDataState } from './masterDataContext.ts'

/**
 * Lädt die Stammdaten sofort beim Start, unabhängig von der Anmeldung, und hält sie für alle Module bereit. Beim
 * Neuladen bleiben die alten Daten stehen, bis die neuen da sind.
 */
export function MasterDataProvider({ children }: { children: ReactNode }) {
  const { data, errorMessage, reload } = useLoad(getMasterData)

  const masterDataState = useMemo<MasterDataState>(
    () => ({
      accessRoles: data?.accessRoles ?? null,
      accessRoleCollections: data?.accessRoleCollections ?? null,
      fileSubClasses: data?.fileSubClasses ?? null,
      fileExtensions: data?.fileExtensions ?? null,
      fileExtensionCollections: data?.fileExtensionCollections ?? null,
      solrHookGroups: data?.solrHookGroups ?? null,
      solrHooks: data?.solrHooks ?? null,
      solrCores: data?.solrCores ?? null,
      solrFields: data?.solrFields ?? null,
      errorMessage,
      reloadMasterData: reload,
    }),
    [data, errorMessage, reload],
  )

  return <MasterDataContext.Provider value={masterDataState}>{children}</MasterDataContext.Provider>
}
