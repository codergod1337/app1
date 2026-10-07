package codergod1337.app1.dms.model;

import tools.jackson.databind.JsonNode;

/**
 * Eine Auflösung im Kern DMS, wie der Client sie schickt (POST /dms/resolve): je Feld ein Wert oder eine Liste von
 * Werten, mehrere Werte ODER, mehrere Felder UND. Mindestens eine Bedingung mit Wert ist Pflicht, Auflösen gibt nie
 * alles aus.
 *
 * @param fieldValuesByFieldName JSON-Objekt, jedes Feld unter seinem Solr-Namen
 * @param page                   Seite ab 0, null heißt 0
 * @param size                   Treffer je Seite, 1 bis DmsExactFieldValueResolveService.MAX_PAGE_SIZE, null heißt der Standard
 * @param sortFieldName          Feld, nach dem sortiert wird, null heißt Reihenfolge von Solr
 * @param sortDescending         absteigend, null heißt aufsteigend
 */
public record DmsExactFieldValueResolveRequest(JsonNode fieldValuesByFieldName, Integer page, Integer size,
		String sortFieldName, Boolean sortDescending) {
}
