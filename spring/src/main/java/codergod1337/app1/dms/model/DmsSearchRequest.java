package codergod1337.app1.dms.model;

import java.util.List;
import java.util.Map;

/**
 * Eine Suche im Kern DMS, wie der Client sie schickt (POST /dms/search): Freitext plus eingeloggte Filter, Seite und
 * Größe, nach der Suchmaske der Vorlage. Der Freitext wird im Backend zerlegt und escaped, nur {@code +} und {@code -}
 * sind Operatoren. Filter nur aus der festen Liste des Kerns, gleiches Feld ODER, verschiedene Felder UND; ein Feld
 * außerhalb der Liste wird überlesen, denn die Maske schickt ihre Chips an jeden Kern.
 *
 * @param searchText                   was der User getippt hat, darf leer sein
 * @param exactFilterValuesByFieldName je Feld die exakten Werte als Text, z. B. {"artikelNummer": ["4711"]}
 * @param dateRangeFilterByFieldName   je Datumsfeld ein Zeitraum, z. B. {"documentCreatedAt": {"from": "2026-01-01", "to": null}}
 * @param page                         Seite ab 0, null heißt 0
 * @param size                         Treffer je Seite, 1 bis DmsSearchService.MAX_PAGE_SIZE, null heißt der Standard
 */
public record DmsSearchRequest(String searchText, Map<String, List<String>> exactFilterValuesByFieldName,
		Map<String, DmsSearchDateRange> dateRangeFilterByFieldName, Integer page, Integer size) {
}
