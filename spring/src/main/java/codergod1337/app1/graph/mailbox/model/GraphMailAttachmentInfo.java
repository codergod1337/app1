package codergod1337.app1.graph.mailbox.model;

/**
 * Ein Anhang einer Mail für die Anzeige, ohne Bytes (User 2026-10-14): Die Bytes bleiben im Backend.
 *
 * @param name        der Dateiname
 * @param contentType der Typ laut Mail, z. B. application/pdf
 * @param size        die Größe in Bytes laut Graph
 * @param sha256      der Hash der Bytes = die DMS-id; null bei einem Anhang ohne Bytes (angehängte Mail, Verweis)
 * @param inline      im Mailtext eingebettet, z. B. ein Bild der Signatur
 */
public record GraphMailAttachmentInfo(String name, String contentType, long size, String sha256, boolean inline) {
}
