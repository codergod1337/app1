package codergod1337.app1.message.archive.model;

import java.util.Map;

/**
 * Ein Anhang auf der Aufbereiten-Seite (User 2026-10-14), ohne Bytes.
 *
 * @param name                    der Dateiname
 * @param contentType             der Typ laut Mail
 * @param size                    die Größe in Bytes laut Graph
 * @param sha256                  der Hash der Bytes = die DMS-id; null bei NO_FILE
 * @param inline                  im Mailtext eingebettet (cid:) oder ein Bild aus dem Text
 * @param embeddedInText          ein Bild, das als data: direkt im HTML steht, kein Anhang bei Graph
 * @param state                   wie er dasteht
 * @param existingFileSubClassKey die Dateiart des vorhandenen DMS-Dokuments bei MERGE, SIGNATURE und IGNORED, sonst null
 * @param fileSubClassBaseScores  nur bei NEW: der feste Teil des Vorschlags je Dateiart, in die der User schreiben darf,
 *                                jede beschreibbare auch mit 0: Treffer der Triggerwörter in Betreff und Mailtext einfach,
 *                                in Dateiname und Text des Anhangs doppelt. Die Trigger-Hooks rechnet die Seite dazu,
 *                                gewählt wird nur unter diesen Dateiarten.
 */
public record MessageArchiveAttachment(String name, String contentType, long size, String sha256, boolean inline,
		boolean embeddedInText, MessageArchiveAttachmentState state, String existingFileSubClassKey,
		Map<String, Integer> fileSubClassBaseScores) {
}
