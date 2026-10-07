package codergod1337.app1.dms.model;

import java.time.Instant;

/**
 * Was der Upload aus der Datei selbst gewonnen hat und der Schreibservice als Felder des Backends ins neue Dokument
 * setzt: der Hash als id, der Dateiname nach der Normalisierung, die Endung, der MIME-Typ aus Tika (an den Bytes
 * erkannt, nicht an der Endung), die Größe, der Pfad auf dem SFTP, das Erstelldatum aus der Datei, der Volltext, das
 * Vorschaubild und die Sprache (vom Client oder aus dem Text erkannt). Nichts davon kommt vom Client.
 *
 * @param sha256               die id des Dokuments
 * @param fileName             normalisiert nach DmsUploadService.normalizeFileName
 * @param documentCreatedAt    aus der Datei, sonst der Zeitpunkt des Uploads, nie null
 * @param searchableWords      der rohe Text von Tika, null ohne Text
 * @param imagePreviewBase64Png null ohne Vorschaubild
 * @param documentLanguage     ISO 639-1, null ohne Sprache
 */
public record DmsSolrDocumentUploadFacts(String sha256, String fileName, String fileExtension, String mimeType,
		long sizeBytes, String sftpPath, Instant documentCreatedAt, String searchableWords, String imagePreviewBase64Png,
		String documentLanguage) {
}
