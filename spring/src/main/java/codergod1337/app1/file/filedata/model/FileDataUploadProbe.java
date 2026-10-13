package codergod1337.app1.file.filedata.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Das Ergebnis der Upload-Probe und der erste Teil jedes Uploads: die Datei, wie der Browser sie schickte, ihre Endung
 * nach unserer Regel, ihr SHA-256 (die id des Dokuments), die geprüften Felder in der Reihenfolge des Kerns, alles von
 * Tika und das Vorschaubild. stored sagt, ob die Datei danach gespeichert wurde: false bei der Probe, true im Upload.
 * Dieselbe Form wie DmsUploadProbe im Frontend.
 */
public record FileDataUploadProbe(String fileName, String fileExtension, String contentType, long sizeBytes,
		String sha256, Map<String, Object> document, TikaOutcome tika, PreviewOutcome preview, boolean stored) {

	/** Was Tika fand, oder warum nicht: ok false mit error, dann sind die anderen Angaben leer */
	public record TikaOutcome(boolean ok, String error, String mimeType, Instant createdAt, Instant modifiedAt,
			Integer orientation, int textLength, String text, Map<String, List<String>> metadata, long durationMs) {

		public static TikaOutcome failed(String error) {
			return new TikaOutcome(false, error, null, null, null, null, 0, "", Map.of(), 0);
		}

	}

	/**
	 * Das Vorschaubild, oder warum keins: attempted false, wenn die Endung im Katalog kein Vorschaubild hat; ok false
	 * mit error, wenn die Bytes kein lesbares Bild sind. base64Png ist rohes Base64 ohne Präfix.
	 */
	public record PreviewOutcome(boolean attempted, boolean ok, String error, String base64Png, int sourceWidth,
			int sourceHeight, int orientation, int bytes, long durationMs) {

		public static PreviewOutcome skipped() {
			return new PreviewOutcome(false, false, null, null, 0, 0, 0, 0, 0);
		}

		public static PreviewOutcome failed(String error, long durationMs) {
			return new PreviewOutcome(true, false, error, null, 0, 0, 0, 0, durationMs);
		}

	}

	public FileDataUploadProbe asStored() {
		return new FileDataUploadProbe(fileName, fileExtension, contentType, sizeBytes, sha256, document, tika, preview,
				true);
	}

}
