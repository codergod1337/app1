import { SOLR_CORE_BUILT_IN_FIELD_NAMES } from '../solrmanager/SolrCore.ts'
import { FILE_SUB_CLASS_FIELD_NAME } from './Dms.ts'

/**
 * Felder des Kerns DMS, die nur das Backend setzt (DmsUploadService.BACKEND_FIELD_NAMES): in der Maske gesperrt, vom
 * Client lehnt das Backend sie ab. Dazu die eingebauten id, cursorDate und userGrantedAccess (die Dateifreigabe).
 * fileName und fileExtension kommen aus dem Namen der hochgeladenen Datei, mimeType aus Tika (erkannt an den Bytes,
 * nicht an der Endung), documentUploadedAt und sftpPath entstehen beim Speichern, readAcl und writeAcl kommen aus der
 * Dateiart, ocr und ki sind vorerst pauschal false, fileIdRevisionNo zählt die Revisionen, bildVorschauBase64Png
 * rechnet das Backend aus der Datei, uploader ist die guid des current users, changingUsers und lastChangedAt schreibt
 * der Bearbeiten-Service bei jedem Atomic Update, version vergibt das Speichern in einer Versionskette, downloadCount
 * beginnt bei 0 und zählt nur der Download.
 */
export const DMS_BACKEND_FIELD_NAMES: readonly string[] = [
  ...SOLR_CORE_BUILT_IN_FIELD_NAMES,
  'fileName',
  'fileExtension',
  'mimeType',
  'documentUploadedAt',
  'sftpPath',
  'readAcl',
  'writeAcl',
  'ocr',
  'ki',
  'fileIdRevisionNo',
  'bildVorschauBase64Png',
  'uploader',
  'changingUsers',
  'lastChangedAt',
  'version',
  'downloadCount',
]

/**
 * versionChain: Nur im Sonderfall „neue Fassung“ schickt das Frontend die Kette der Originaldatei mit, nie der User
 * von Hand. Bei einer neuen Datei bleibt das Feld leer, das Backend vergibt beim Speichern eine zufällige UUID.
 */
export const VERSION_CHAIN_FIELD_NAME = 'versionChain'

/** fileSizeBytes liest das Frontend aus der gewählten Datei und schickt es mit, das Backend prüft es gegen die Datei */
export const FILE_SIZE_FIELD_NAME = 'fileSizeBytes'

/**
 * status: in Solr ein string, die Werte sind die EntityStates des Bereichs DMS aus den Stammdaten (DmsStateService
 * legt sie beim Start an). Später wählt ihn der User nicht mehr, die Masken je Fall setzen ihn.
 */
export const STATUS_FIELD_NAME = 'status'
export const DMS_STATE_SCOPE = 'DMS'

/** Wie die Upload-Maske ein Feld des Kerns DMS anbietet */
export type DmsFieldInput = 'backend' | 'chain' | 'fileSize' | 'fileSubClass' | 'status' | 'free'

export function dmsFieldInput(name: string): DmsFieldInput {
  if (DMS_BACKEND_FIELD_NAMES.includes(name)) {
    return 'backend'
  }
  switch (name) {
    case VERSION_CHAIN_FIELD_NAME:
      return 'chain'
    case FILE_SIZE_FIELD_NAME:
      return 'fileSize'
    case FILE_SUB_CLASS_FIELD_NAME:
      return 'fileSubClass'
    case STATUS_FIELD_NAME:
      return 'status'
    default:
      return 'free'
  }
}

/** Die Endung nach derselben Regel wie im Backend (DmsUploadService.extensionOf): nach dem letzten Punkt, klein. Ohne Punkt leer. */
export function fileExtensionOf(fileName: string): string {
  const dot = fileName.lastIndexOf('.')
  return dot < 0 || dot === fileName.length - 1 ? '' : fileName.slice(dot + 1).toLowerCase()
}
