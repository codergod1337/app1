package codergod1337.app1.message.archive.model;

/**
 * Was beim Archivieren herauskam (User 2026-10-14).
 *
 * @param id                die laufende Nummer der Nachricht im Kern NACHRICHTEN
 * @param newAttachments    neu ins DMS gelegte Anhänge an der Nachricht
 * @param mergedAttachments schon vorhandene Anhänge, die die Hooks der Nachricht dazubekamen
 * @param signatures        Signaturen an der Nachricht, neue und bekannte
 * @param ignored           Dateien, die nicht archiviert wurden (ignoriert oder übersprungen)
 */
public record MessageArchiveResult(String id, int newAttachments, int mergedAttachments, int signatures, int ignored) {
}
