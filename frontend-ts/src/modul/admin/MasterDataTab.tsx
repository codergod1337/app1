import { MasterDataExportBox } from '../masterdata/MasterDataExportBox.tsx'
import { MasterDataImportBox } from '../masterdata/MasterDataImportBox.tsx'

/** Tab „Stammdaten“ der Administration: Export als ZIP, Import mit Vorschau. */
export function MasterDataTab() {
  return (
    <>
      <MasterDataExportBox />
      <MasterDataImportBox />
    </>
  )
}
