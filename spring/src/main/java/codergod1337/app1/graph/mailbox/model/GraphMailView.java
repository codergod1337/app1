package codergod1337.app1.graph.mailbox.model;

import java.time.Instant;
import java.util.List;

/**
 * Eine Mail zum Anzeigen vor dem Archivieren (User 2026-10-14): Kopf, Text und die Anhänge ohne Bytes. Die
 * eingebetteten Bilder (cid:) sind im Text schon gegen data:-Bilder getauscht, so braucht der Client die Anhänge nicht.
 *
 * @param exchangeMailId   die feste id der Mail im Postfach (ImmutableId)
 * @param subject          der Betreff
 * @param from             der Absender; null ohne
 * @param toRecipients     An
 * @param ccRecipients     Cc
 * @param bccRecipients    Bcc, nur bei gesendeten Mails gefüllt
 * @param sentDateTime     wann gesendet
 * @param receivedDateTime wann im Postfach angekommen
 * @param importance       low, normal oder high
 * @param bodyHtml         die ganze Mail samt Zitat oder Weiterleitung als HTML
 * @param uniqueBodyHtml   nur der neue Teil als HTML; das wird ohne die Wahl „ganze Mail“ archiviert
 * @param attachments      alle Anhänge, auch die eingebetteten
 */
public record GraphMailView(String exchangeMailId, String subject, GraphMailRecipient from,
		List<GraphMailRecipient> toRecipients, List<GraphMailRecipient> ccRecipients,
		List<GraphMailRecipient> bccRecipients, Instant sentDateTime, Instant receivedDateTime, String importance,
		String bodyHtml, String uniqueBodyHtml, List<GraphMailAttachmentInfo> attachments) {
}
