package codergod1337.app1.solr.todojob.model;

/**
 * Ein Beitrag zur Rückmeldung: die Art des Eintrags, der Text (bei TEXT und COMMENT Pflicht) und der Wert (bei ZAHL,
 * DATUM, BOOL, DMS_DATEI, ARTIKELNUMMER, STERNZEIT Pflicht)
 */
public record TodojobRueckmeldungEntryRequest(String id, TodojobRueckmeldungEntryType type, String text, String value) {
}
