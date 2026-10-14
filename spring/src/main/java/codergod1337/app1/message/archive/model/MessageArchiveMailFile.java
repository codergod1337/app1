package codergod1337.app1.message.archive.model;

/**
 * Eine Datei einer Mail, wie Aufbereiten und Archivieren sie sehen (User 2026-10-14): ein Anhang von Graph oder ein Bild,
 * das als Base64 direkt im HTML steckt. Nur im Backend, die Bytes gehen nie an den Client.
 *
 * @param name           der Dateiname; ein Bild aus dem Text heißt bild-im-text-1.png usw.
 * @param contentType    der Typ laut Mail, bei einem Bild aus dem Text image/…
 * @param bytes          die Bytes; null bei einem Anhang ohne Bytes (angehängte Mail, Verweis)
 * @param sha256         der Hash der Bytes = die DMS-id; null ohne Bytes
 * @param size           die Größe in Bytes
 * @param inline         im Mailtext eingebettet (cid:) oder ein Bild aus dem Text
 * @param contentId      die contentId, über die der Mailtext ihn als cid: nennt, ohne spitze Klammern; null ohne
 * @param embeddedInText ein Bild, das als data: direkt im HTML steht, kein Anhang bei Graph
 */
public record MessageArchiveMailFile(String name, String contentType, byte[] bytes, String sha256, long size,
		boolean inline, String contentId, boolean embeddedInText) {
}
