package codergod1337.app1.solr.nachrichten.model;

/**
 * Warum eine Nachricht ein Treffer ist: das Feld, immer der ursprüngliche Name (bei einem Hook sein Key, nie der
 * Zwilling, damit der Client daraus einen Filterchip machen kann), und der Wert mit dem getroffenen Teil zwischen STX
 * (U+0002) und ETX (U+0003). Beim Volltext searchableWords nur der Feldname, snippet null: Der Text geht nie zum Client.
 * Dieselbe Form wie bei DMS, ARTIKEL und KONTAKTE.
 */
public record NachrichtenSearchHitReason(String fieldName, String snippet) {
}
