package codergod1337.app1.dms.model;

import java.util.List;
import java.util.Map;

/**
 * Ein Treffer der Suche im Kern DMS: das Dokument mit allen Feldern außer dem Volltext, Solrs _version_ als Text, dazu
 * der Treffergrund: je Feld, in dem etwas traf, die Wörter oder Filterwerte, die trafen (User 2026-10-07: jeder
 * Filter wird markiert, nicht nur die Eingabe). Beim Volltext (searchableWords) nur die Wörter, nie ein Ausschnitt,
 * denn der Volltext geht nie zum Client; die Maske markiert dann das Feld. Ein Feld ohne Treffer steht nicht drin.
 *
 * @param document                das Dokument als Map
 * @param matchedWordsByFieldName je Feldname die Wörter, die darin trafen, klein geschrieben wie die Maske sie sucht
 */
public record DmsSearchHit(Map<String, Object> document, Map<String, List<String>> matchedWordsByFieldName) {
}
