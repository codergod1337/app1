package codergod1337.app1.dms.model;

import java.util.List;
import java.util.Map;

/**
 * Ein Treffer der Suche im Kern DMS: das Dokument mit allen Feldern außer dem Volltext, Solrs _version_ als Text, dazu
 * der Treffergrund nach der Vorlage, je Feld ein Eintrag mit dem markierten Wert (User 2026-10-07: jeder Filter wird
 * gezeigt, nicht nur die Eingabe). Treffer am Anfang eines Werts stehen zuerst, dann die mitten drin, dann die Filter,
 * zuletzt der Volltext ohne Ausschnitt. Ein Feld ohne Treffer steht nicht drin.
 *
 * @param document das Dokument als Map
 * @param reasons  der Treffergrund, leer wenn nichts zu begründen ist
 */
public record DmsSearchHit(Map<String, Object> document, List<DmsSearchHitReason> reasons) {
}
