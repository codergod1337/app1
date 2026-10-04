import type { LanguageCode } from '../../branding/languages.ts'
import type { FileExtension } from '../fileextension/FileExtension.ts'
import type { FileExtensionCollection } from '../fileextension/FileExtensionCollection.ts'

/** Eine Sprache, die ein DMS-Dokument haben kann: ISO-639-1-Code und der Ländercode der Flagge für flag-icons */
export interface DmsDocumentLanguage {
  code: string
  flag: string
}

/**
 * Die 18 gängigsten Sprachen (Vorgabe 2026-10-03), dieselbe Liste wie DmsDocumentLanguages.java, das Backend prüft
 * dagegen. Die Reihenfolge der Anzeige macht documentLanguagesInUiOrder: de, en, bg zuerst, der Rest alphabetisch nach
 * dem Namen in der Oberflächensprache.
 */
export const DMS_DOCUMENT_LANGUAGES: readonly DmsDocumentLanguage[] = [
  { code: 'de', flag: 'de' },
  { code: 'en', flag: 'us' },
  { code: 'bg', flag: 'bg' },
  { code: 'ar', flag: 'sa' },
  { code: 'cs', flag: 'cz' },
  { code: 'el', flag: 'gr' },
  { code: 'es', flag: 'es' },
  { code: 'fr', flag: 'fr' },
  { code: 'hu', flag: 'hu' },
  { code: 'it', flag: 'it' },
  { code: 'ja', flag: 'jp' },
  { code: 'nl', flag: 'nl' },
  { code: 'pl', flag: 'pl' },
  { code: 'pt', flag: 'pt' },
  { code: 'ro', flag: 'ro' },
  { code: 'ru', flag: 'ru' },
  { code: 'tr', flag: 'tr' },
  { code: 'zh', flag: 'cn' },
]

/** Diese drei stehen immer vorn, in dieser Reihenfolge */
const FIRST_CODES: readonly string[] = ['de', 'en', 'bg']

/** Der Name der Sprache in der Oberflächensprache, vom Browser (Intl.DisplayNames), zur Not der Code selbst */
export function documentLanguageName(code: string, uiLanguage: LanguageCode): string {
  try {
    return new Intl.DisplayNames([uiLanguage], { type: 'language' }).of(code) ?? code
  } catch {
    return code
  }
}

/** Die Liste in der Reihenfolge der Anzeige: de, en, bg, dann der Rest alphabetisch nach dem Namen in der Oberflächensprache */
export function documentLanguagesInUiOrder(uiLanguage: LanguageCode): DmsDocumentLanguage[] {
  const first = DMS_DOCUMENT_LANGUAGES.filter((candidate) => FIRST_CODES.includes(candidate.code)).sort(
    (a, b) => FIRST_CODES.indexOf(a.code) - FIRST_CODES.indexOf(b.code),
  )
  const rest = DMS_DOCUMENT_LANGUAGES.filter((candidate) => !FIRST_CODES.includes(candidate.code)).sort((a, b) =>
    documentLanguageName(a.code, uiLanguage).localeCompare(documentLanguageName(b.code, uiLanguage), uiLanguage),
  )
  return [...first, ...rest]
}

/** Der Ländercode der Flagge zu einem Sprachcode, null für einen Code außerhalb der Liste */
export function documentLanguageFlagOf(code: string): string | null {
  return DMS_DOCUMENT_LANGUAGES.find((candidate) => candidate.code === code)?.flag ?? null
}

/**
 * Hat eine Datei mit dieser Endung eine Sprache? Das sagt ihre Endungsgruppe (FileExtensionCollection.
 * documentLanguageApplies): Dokumente, Videos, Anwendungsdateien ja, Bilder, Archive, CAD nein. Ohne Gruppe nein.
 * Dieselbe Regel wendet das Backend an.
 */
export function documentLanguageAppliesToFileExtension(
  fileExtension: string | null,
  fileExtensions: readonly FileExtension[],
  fileExtensionCollections: readonly FileExtensionCollection[],
): boolean {
  if (fileExtension === null || fileExtension === '') {
    return false
  }
  const collectionKey = fileExtensions.find((candidate) => candidate.extension === fileExtension)?.fileExtensionCollectionKey
  if (collectionKey === null || collectionKey === undefined) {
    return false
  }
  return fileExtensionCollections.find((candidate) => candidate.key === collectionKey)?.documentLanguageApplies ?? false
}
