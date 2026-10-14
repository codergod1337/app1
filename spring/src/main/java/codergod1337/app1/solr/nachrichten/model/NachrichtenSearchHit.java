package codergod1337.app1.solr.nachrichten.model;

import java.util.List;
import java.util.Map;

/**
 * Ein Treffer der Suche im Kern NACHRICHTEN: die Felder der Trefferzeile als Map Feldname -> Wert (Nachrichtenart,
 * Betreff, Vorschautext, Absender, Empfänger, Datum und die durchsuchten Felder ohne Volltext) und warum er getroffen
 * hat, in der Reihenfolge der Felder im Kern. Dazu writable: ob writeAcl eine Rolle des Users enthält, also ob er die
 * Hooks pflegen darf; writeAcl selbst geht nicht zum Client.
 */
public record NachrichtenSearchHit(Map<String, Object> fieldValues, boolean writable,
		List<NachrichtenSearchHitReason> reasons) {
}
