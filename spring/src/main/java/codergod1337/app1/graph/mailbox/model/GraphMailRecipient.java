package codergod1337.app1.graph.mailbox.model;

/**
 * Ein Beteiligter einer Mail, wie Graph ihn liefert ({emailAddress:{name,address}}), Name und Adresse getrennt
 * (User 2026-10-14).
 *
 * @param name    der Name, wie ihn die Mail trägt; null ohne
 * @param address die Adresse, klein geschrieben
 */
public record GraphMailRecipient(String name, String address) {
}
