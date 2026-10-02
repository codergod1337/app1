import type { FileExtension } from './FileExtension.ts'
import type { FileExtensionCollection } from './FileExtensionCollection.ts'

/** Eine Gruppe mit ihren Endungen. Ohne Gruppe: fileExtensionCollection ist null. */
export interface FileExtensionBlock {
  fileExtensionCollection: FileExtensionCollection | null
  fileExtensions: FileExtension[]
}

/**
 * Die Endungen je Gruppe, in der Reihenfolge der Gruppen und darin in der Reihenfolge der Endungen. Am Ende die
 * Endungen ohne Gruppe oder mit einer unbekannten. Leere Gruppen bleiben drin, eine neue Gruppe soll man sehen.
 * Für Katalog und Freischalt-Matrix.
 */
export function fileExtensionBlocks(
  fileExtensionCollections: FileExtensionCollection[],
  fileExtensions: FileExtension[],
): FileExtensionBlock[] {
  const knownKeys = new Set(fileExtensionCollections.map((fileExtensionCollection) => fileExtensionCollection.key))
  const blocks: FileExtensionBlock[] = fileExtensionCollections.map((fileExtensionCollection) => ({
    fileExtensionCollection,
    fileExtensions: fileExtensions.filter(
      (fileExtension) => fileExtension.fileExtensionCollectionKey === fileExtensionCollection.key,
    ),
  }))
  const withoutCollection = fileExtensions.filter(
    (fileExtension) =>
      fileExtension.fileExtensionCollectionKey === null || !knownKeys.has(fileExtension.fileExtensionCollectionKey),
  )
  if (withoutCollection.length > 0) {
    blocks.push({ fileExtensionCollection: null, fileExtensions: withoutCollection })
  }
  return blocks
}
