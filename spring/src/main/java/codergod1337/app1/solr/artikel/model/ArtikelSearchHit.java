package codergod1337.app1.solr.artikel.model;

import java.util.List;
import java.util.Map;

/**
 * Ein Treffer der Suche im Kern ARTIKEL: die Felder der Trefferbox als Map Feldname -> Wert (Nummer, Name, Suchbegriff,
 * Artikelart, Master, Vorschaubild und die durchsuchten Felder) und warum er getroffen hat, in der Reihenfolge der
 * Felder im Kern. Kein Schreibflag: Im Spiegelkern schreibt kein Mensch.
 */
public record ArtikelSearchHit(Map<String, Object> fieldValues, List<ArtikelSearchHitReason> reasons) {
}
