package codergod1337.app1.solr.document.model;

import tools.jackson.databind.JsonNode;

/**
 * Eine Auflösung nach exakten Feldwerten, wie der Client sie schickt (z. B. POST /dms/resolve): je Feld ein Wert oder
 * eine Liste von Werten, mehrere Werte ODER, mehrere Felder UND. Ohne Felder kommt alles, was der User sehen darf,
 * Seite für Seite.
 *
 * @param fieldValuesByFieldName JSON-Objekt, jedes Feld unter seinem Solr-Namen. null oder leer heißt keine Bedingung.
 * @param page                   Seite ab 0, null heißt 0
 * @param size                   Treffer je Seite, 1 bis SolrExactFieldValueResolveService.MAX_PAGE_SIZE, null heißt der Standard
 * @param sortFieldName          Feld, nach dem sortiert wird, null heißt Reihenfolge von Solr
 * @param sortDescending         absteigend, null heißt aufsteigend
 */
public record SolrExactFieldValueResolveRequest(JsonNode fieldValuesByFieldName, Integer page, Integer size,
		String sortFieldName, Boolean sortDescending) {
}
