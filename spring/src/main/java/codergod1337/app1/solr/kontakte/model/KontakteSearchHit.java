package codergod1337.app1.solr.kontakte.model;

import java.util.List;
import java.util.Map;

/**
 * Ein Treffer der Suche im Kern KONTAKTE: die Felder der Trefferzeile als Map Feldname -> Wert (Sternzeit, Firma oder
 * Ansprechpartner, Name, Nummern, Ort, Funktion, Telefon, E-Mail und die durchsuchten Felder) und warum er getroffen
 * hat, in der Reihenfolge der Felder im Kern. Kein Schreibflag: Im Spiegelkern schreibt kein Mensch.
 */
public record KontakteSearchHit(Map<String, Object> fieldValues, List<KontakteSearchHitReason> reasons) {
}
