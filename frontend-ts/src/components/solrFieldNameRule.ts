/*
 * Die Regel für Solr-Feldnamen, gleich wie im Backend (SolrFieldName.isValid):
 * Buchstabe am Anfang, dann Buchstaben, Ziffern und _, nicht auf _ oder Suggested endend, nicht id, höchstens
 * 100 Zeichen. Beispiele: artikelNummer, kundenNummer, ean
 */

export const SOLR_FIELD_NAME_MAX_LENGTH = 100

/** Hängt Solr an jedes Feld, das ein Suggest-Gegenstück bekommt, deshalb darf kein Hook so enden */
const SUGGEST_SUFFIX = 'Suggested'

/** Felder, die Solr selbst anlegt. Der Text der Regel für den User steht im Sprachkatalog (common.solrFieldNameRule). */
const RESERVED = ['id', '_version_', '_root_']

const VALID_SOLR_FIELD_NAME = /^[A-Za-z][A-Za-z0-9_]*$/

export function isValidSolrFieldName(fieldName: string): boolean {
  return (
    fieldName.length <= SOLR_FIELD_NAME_MAX_LENGTH &&
    VALID_SOLR_FIELD_NAME.test(fieldName) &&
    !fieldName.endsWith('_') &&
    !fieldName.endsWith(SUGGEST_SUFFIX) &&
    !RESERVED.includes(fieldName)
  )
}

/**
 * Macht aus einer Eingabe, was ein Feldname enthalten darf, schon während des Tippens: Umlaute ausgeschrieben
 * (ä → ae), Leerzeichen und - werden zu _, alles andere fällt weg, vorne keine Ziffer und kein _.
 * Am Ende darf beim Tippen noch ein _ stehen (artikel_ → artikel_nr), das fängt erst isValidSolrFieldName ab.
 */
export function toSolrFieldNameCharacters(text: string): string {
  return text
    .replace(/ä/g, 'ae')
    .replace(/ö/g, 'oe')
    .replace(/ü/g, 'ue')
    .replace(/Ä/g, 'Ae')
    .replace(/Ö/g, 'Oe')
    .replace(/Ü/g, 'Ue')
    .replace(/ß/g, 'ss')
    .replace(/[\s-]/g, '_')
    .replace(/[^A-Za-z0-9_]/g, '')
    .replace(/^[0-9_]+/, '')
    .slice(0, SOLR_FIELD_NAME_MAX_LENGTH)
}
