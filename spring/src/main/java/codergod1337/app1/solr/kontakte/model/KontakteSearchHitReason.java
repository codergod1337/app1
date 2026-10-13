package codergod1337.app1.solr.kontakte.model;

/**
 * Warum ein Kontakt ein Treffer ist: das Feld, immer der ursprüngliche Name (bei einem Hook sein Key, nie der Zwilling,
 * damit der Client daraus einen Filterchip machen kann), und der Wert mit dem getroffenen Teil zwischen STX (U+0002)
 * und ETX (U+0003). Dieselbe Form wie bei DMS und ARTIKEL.
 */
public record KontakteSearchHitReason(String fieldName, String snippet) {
}
