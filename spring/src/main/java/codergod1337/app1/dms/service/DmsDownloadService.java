package codergod1337.app1.dms.service;

import codergod1337.app1.solr.document.service.SolrExactFieldValueResolveService;
import codergod1337.app1.system.sftp.service.SftpService;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Der einzige Weg zu den Bytes einer Datei, egal von wo der Klick kommt (Trefferliste, Dokumentseite, später
 * Artikelmappe und Viewer). Der Client nennt nur die DMS-id. Das Backend lädt das Dokument noch einmal mit der
 * Leseprüfung des current users: readAcl enthält eine seiner ARs oder seine guid steht in userGrantedAccess. Kein
 * Treffer heißt 404, gleich ob es die Datei nicht gibt oder er sie nicht sehen darf. Erst dann kommt sftpPath aus genau
 * diesem Dokument, nie vom Client, und die Bytes gehen vom SFTP in die Antwort.
 *
 * sftpPath muss dafür kein lesbares Feld des Users sein: Das Backend holt es für sich selbst, der Client sieht es nie.
 * Ein Download-Zähler fehlt noch: Ob Buchhaltung des Backends unter die Schreibregel fällt, ist nicht entschieden.
 */
@Service
public class DmsDownloadService {

	private static final Logger log = LoggerFactory.getLogger(DmsDownloadService.class);

	private static final String SFTP_PATH_FIELD_NAME = "sftpPath";
	private static final String FILE_NAME_FIELD_NAME = "fileName";
	private static final String MIME_TYPE_FIELD_NAME = "mimeType";
	private static final String FILE_SIZE_FIELD_NAME = DmsUploadService.FILE_SIZE_FIELD_NAME;

	/** Was der Controller für die Antwort braucht. Entsteht nur nach der Leseprüfung. */
	public record DmsDownloadableFile(String id, String fileName, String mimeType, Long sizeBytes, String sftpPath) {
	}

	private final SolrExactFieldValueResolveService solrExactFieldValueResolveService;
	private final SftpService sftpService;

	public DmsDownloadService(SolrExactFieldValueResolveService solrExactFieldValueResolveService,
			SftpService sftpService) {
		this.solrExactFieldValueResolveService = solrExactFieldValueResolveService;
		this.sftpService = sftpService;
	}

	/** Prüft, dass der current user die Datei sehen darf, und liefert, was die Antwort braucht. 404 sonst. */
	@PreAuthorize("hasRole('DMS')")
	public DmsDownloadableFile loadDownloadableFileWithReadAclAndGrantedAccessCheck(String id) {
		Map<String, Object> document = solrExactFieldValueResolveService
				.loadDocumentForBackendWithReadAclAndGrantedAccessCheck(DmsUploadService.CORE_KEY, id,
						List.of(SFTP_PATH_FIELD_NAME, FILE_NAME_FIELD_NAME, MIME_TYPE_FIELD_NAME, FILE_SIZE_FIELD_NAME));
		if (document == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Datei mit dieser id");
		}
		String sftpPath = text(document, SFTP_PATH_FIELD_NAME);
		if (sftpPath == null) {
			log.error("Download: Dokument {} hat keinen sftpPath", id);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "zu dieser Datei fehlt der Ablageort");
		}
		String fileName = text(document, FILE_NAME_FIELD_NAME);
		String mimeType = text(document, MIME_TYPE_FIELD_NAME);
		Object size = document.get(FILE_SIZE_FIELD_NAME);
		return new DmsDownloadableFile(id, fileName != null ? fileName : id,
				mimeType != null ? mimeType : MediaType.APPLICATION_OCTET_STREAM_VALUE,
				size instanceof Number number ? number.longValue() : null, sftpPath);
	}

	/**
	 * Die Bytes vom SFTP in den Zielstrom, meist den der HTTP-Antwort. Keine eigene Prüfung: Ein DmsDownloadableFile gibt
	 * es nur aus loadDownloadableFileWithReadAclAndGrantedAccessCheck.
	 */
	@PreAuthorize("hasRole('DMS')")
	public void streamFileBytesFromSftp(DmsDownloadableFile file, OutputStream target) {
		sftpService.readFileData(file.sftpPath(), target);
		log.info("Download: {} ({})", file.id(), file.fileName());
	}

	private static String text(Map<String, Object> document, String name) {
		Object value = document.get(name);
		return value == null ? null : value.toString();
	}

}
