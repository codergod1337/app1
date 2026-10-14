package codergod1337.app1.message.archive.model;

import codergod1337.app1.graph.mailbox.model.GraphMailView;
import java.util.List;
import java.util.Map;

/**
 * Alles für die Aufbereiten-Seite einer Mail in einer Antwort (User 2026-10-14). Nur Vorschläge, gespeichert wird nichts;
 * was archiviert wird, entscheidet der User, und das Backend prüft es beim Archivieren noch einmal.
 *
 * @param mail                      die Mail zum Anzeigen, Bilder eingesetzt, Anhänge ohne Bytes
 * @param attachments               je Anhang sein Stand im DMS und der feste Teil des Dateiart-Vorschlags
 * @param numbers                   die Nummern aus Betreff und neuem Teil, die ABAS kennt
 * @param participants              Von, Gesendet von, An, Cc, Bcc mit den Kontakten zu ihren Adressen
 * @param contactsAllowed           false: ohne die AR KONTAKTE, die Kontakte fehlen deshalb
 * @param messageSubClassBaseScores der feste Teil des Vorschlags je Nachrichtenart: Postfach an der Art 3 Punkte, je
 *                                  Treffer eines Triggerworts in Betreff oder Mailtext 1 Punkt; nur Arten mit Punkten.
 *                                  Die Trigger-Hooks rechnet die Seite dazu.
 * @param suggestedHooks            die vorgeschlagenen Hooks: Kunden- und Lieferantennummern der Kontakte und die Nummern
 *                                  mit erkanntem Hook
 */
public record MessageArchivePreparation(GraphMailView mail, List<MessageArchiveAttachment> attachments,
		List<MessageArchiveNumber> numbers, List<MessageArchiveParticipant> participants, boolean contactsAllowed,
		Map<String, Integer> messageSubClassBaseScores, Map<String, List<String>> suggestedHooks) {
}
