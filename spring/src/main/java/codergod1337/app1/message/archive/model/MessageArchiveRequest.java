package codergod1337.app1.message.archive.model;

import codergod1337.app1.graph.mailbox.model.GraphMailFolder;
import java.util.List;
import java.util.Map;

/**
 * Was die Aufbereiten-Seite zum Archivieren schickt (User 2026-10-14): nur die Entscheidungen des Users. Die Mail selbst
 * kommt nicht mit, die holt das Backend frisch von Graph, und den Stand jedes Anhangs bestimmt es selbst neu.
 *
 * @param mailbox            das Postfach, aus dem archiviert wird
 * @param exchangeMailId     die feste id der Mail im Postfach
 * @param folder             der Ordner, Posteingang oder Gesendet
 * @param messageSubClassKey die Nachrichtenart, aus ihr kommen die Leserechte
 * @param wholeMail          die ganze Mail samt Verlauf archivieren (emailBody), sonst nur den neuen Teil
 * @param hooks              der eine Hook-Satz für Nachricht und Anhänge: je Hook-Key seine Werte
 * @param attachments        je Datei (sha256) die Entscheidung; bekannte Signaturen brauchen keine
 */
public record MessageArchiveRequest(String mailbox, String exchangeMailId, GraphMailFolder folder,
		String messageSubClassKey, boolean wholeMail, Map<String, List<String>> hooks, List<Attachment> attachments) {

	/**
	 * Die Entscheidung zu einer Datei.
	 *
	 * @param sha256          die Datei, über ihren Hash
	 * @param action          was mit ihr geschieht
	 * @param fileSubClassKey die Dateiart, nur bei einer neuen Datei mit ARCHIVE
	 */
	public record Attachment(String sha256, MessageArchiveAttachmentAction action, String fileSubClassKey) {
	}

}
