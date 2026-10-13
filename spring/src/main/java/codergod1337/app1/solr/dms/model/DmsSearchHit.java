package codergod1337.app1.solr.dms.model;

import java.util.List;
import java.util.Map;

/**
 * Ein Treffer der Suche: die Felder der Trefferbox als Map Feldname -> Wert (Kopf, Dateiart, Status, Vorschau als
 * Base64 und die durchsuchten Felder), ob der User das Dokument schreiben darf (Stift in der Box), und warum es
 * getroffen hat, in der Reihenfolge der Felder im Kern.
 */
public record DmsSearchHit(Map<String, Object> fieldValues, boolean writable, List<DmsSearchHitReason> reasons) {
}
