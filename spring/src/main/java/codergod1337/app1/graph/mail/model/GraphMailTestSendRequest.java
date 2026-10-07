package codergod1337.app1.graph.mail.model;

/**
 * Eine Test-Mail aus dem GraphManager: Absender und Empfänger gibt der Admin selbst an, ohne Vorlage und ohne
 * Platzhalter. Der Absender muss ein Postfach im Tenant sein, sonst lehnt Graph ab.
 *
 * @param senderAddress  das Postfach, in dessen Namen verschickt wird
 * @param recipientEmail der Empfänger
 * @param subject        der Betreff, reiner Text
 * @param htmlBody       der Text als HTML
 */
public record GraphMailTestSendRequest(String senderAddress, String recipientEmail, String subject, String htmlBody) {
}
