package codergod1337.app1.graph.mailbox.model;

import java.time.Instant;
import java.util.List;

/**
 * Eine Zeile der Postfachliste (User 2026-10-14): genug, um eine Mail wiederzuerkennen, ohne sie ganz zu laden.
 *
 * @param exchangeMailId       die id der Mail im Postfach, als feste id (ImmutableId)
 * @param subject              der Betreff
 * @param fromName             der Name des Absenders, wie ihn die Mail trägt
 * @param fromAddress          die Adresse des Absenders, klein geschrieben
 * @param toRecipientAddresses die Adressen der Empfänger, klein geschrieben, für die gesendeten Mails
 * @param receivedDateTime     wann die Mail im Postfach ankam
 * @param sentDateTime         wann sie gesendet wurde
 * @param hasAttachments       ob Graph Anhänge meldet; eingebettete Bilder zählen bei Graph nicht immer mit
 * @param bodyPreview          die ersten rund 255 Zeichen als reiner Text, für die Vorschau ohne Nachladen
 * @param internetMessageId    die Message-ID aus dem Kopf der Mail: Über sie fragt die Liste, ob die Mail schon im
 *                             Kern NACHRICHTEN liegt; null, wenn Graph keine liefert
 */
public record GraphMailHeader(String exchangeMailId, String subject, String fromName, String fromAddress,
		List<String> toRecipientAddresses, Instant receivedDateTime, Instant sentDateTime, boolean hasAttachments,
		String bodyPreview, String internetMessageId) {
}
