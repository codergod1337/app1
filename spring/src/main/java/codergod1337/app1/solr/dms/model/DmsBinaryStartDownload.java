package codergod1337.app1.solr.dms.model;

/**
 * Was der Download braucht, um die Bytes einer Datei zu streamen: ihre DMS-id, der Name und MIME-Typ für die
 * Kopfzeilen, die Größe für Content-Length (null, wenn das Dokument keine kennt) und der Ablageort auf dem SFTP. Entsteht
 * nur nach einer der Download-Rechteprüfungen des Service und geht nie zum Client, nur in den Stream.
 */
public record DmsBinaryStartDownload(String id, String fileName, String mimeType, Long fileSizeBytes, String sftpPath) {
}
