package codergod1337.app1.file.filedata.model;

import java.util.Map;

/**
 * Das Ergebnis des Uploads: was passiert ist, die id, das Dokument mit der Lesegrenze des Users (null bei EXISTS und
 * wenn er es nicht sehen darf, denn Anlegen braucht kein Lesen), dazu die Probe. Dieselbe Form wie DmsUploadResult im
 * Frontend.
 */
public record FileDataUploadResult(FileDataUploadStatus status, String id, Map<String, Object> document,
		FileDataUploadProbe probe) {

	public enum FileDataUploadStatus {
		/** neues Dokument, Bytes auf dem SFTP, Dokument in Solr, als neue Datei oder neue Fassung */
		CREATED,
		/** dieselben Bytes lagen schon in derselben Dateiart: Hooks, Index und Beschreibung ergänzt, nichts hochgeladen */
		MERGED,
		/** dieselben Bytes liegen unter einer anderen Dateiart oder mit einem Status ohne Merge, oder sollten eine neue Fassung werden: nichts geändert */
		EXISTS
	}

}
