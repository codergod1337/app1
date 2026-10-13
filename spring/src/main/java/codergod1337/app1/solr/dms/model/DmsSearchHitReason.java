package codergod1337.app1.solr.dms.model;

/**
 * Warum ein Dokument ein Treffer ist: das Feld, immer der ursprüngliche Name (bei einem Hook sein Key, nie der
 * Zwilling, damit der Client daraus einen Filterchip machen kann), und der Wert mit dem getroffenen Teil zwischen STX
 * (U+0002) und ETX (U+0003). Beim Volltext (text_suche) ist snippet null: Der Text geht nie zum Client, der Client
 * markiert dann nur den Feldnamen (User 2026-10-09).
 */
public record DmsSearchHitReason(String fieldName, String snippet) {
}
