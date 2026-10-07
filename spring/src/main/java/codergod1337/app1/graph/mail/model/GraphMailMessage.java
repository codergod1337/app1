package codergod1337.app1.graph.mail.model;

/**
 * Eine fertig gerenderte Mail, so wie sie an Graph geht. Der Absender steht in der Nachricht, nicht im Sendeservice:
 * Heute setzt ihn die Vorlage aus der Konfiguration (Systempostfach), später kann ein User sein eigenes Postfach
 * eintragen, ohne dass sich am Versand etwas ändert.
 *
 * @param senderAddress  das Postfach, in dessen Namen verschickt wird
 * @param recipientEmail der Empfänger
 * @param subject        der Betreff, reiner Text
 * @param htmlBody       der Text als HTML
 */
public record GraphMailMessage(String senderAddress, String recipientEmail, String subject, String htmlBody) {
}
