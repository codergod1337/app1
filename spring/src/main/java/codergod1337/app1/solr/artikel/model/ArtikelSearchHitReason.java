package codergod1337.app1.solr.artikel.model;

/**
 * Warum ein Artikel ein Treffer ist: das Feld, immer der ursprüngliche Name (bei einem Hook sein Key, nie der
 * Zwilling, damit der Client daraus einen Filterchip machen kann), und der Wert mit dem getroffenen Teil zwischen STX
 * (U+0002) und ETX (U+0003). Dieselbe Form wie im DMS, damit die eine Suchmaske beide Kerne gleich darstellt.
 */
public record ArtikelSearchHitReason(String fieldName, String snippet) {
}
