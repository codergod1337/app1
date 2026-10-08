package codergod1337.app1.dms.service;

import codergod1337.app1.dms.model.DmsSolrDocumentFieldNames;
import codergod1337.app1.system.sftp.service.SftpService;
import java.io.OutputStream;
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
 * Artikelmappe und Viewer). Der Client nennt nur die DMS-id. Das Backend lädt das Dokument über die Lese-Tür des DMS
 * mit der Leseprüfung des current users: readAcl enthält eine seiner ARs oder seine guid steht in userGrantedAccess.
 * Kein Treffer heißt 404, gleich ob es die Datei nicht gibt oder er sie nicht sehen darf. Erst dann kommt sftpPath aus
 * genau diesem Dokument, nie vom Client, und die Bytes gehen vom SFTP in die Antwort.
 *
 * sftpPath muss dafür kein lesbares Feld des Users sein: Das Backend holt es für sich selbst, der Client sieht es nie.
 * Nach jedem vollständig gestreamten Download zählt downloadCount um eins hoch (User 2026-10-08), über den
 * Schreibservice, ohne Revision: Ein Download ist Buchhaltung, keine Änderung. Der Viewer holt die Datei über denselben
 * Weg und zählt damit genauso.
 */
@Service
public class DmsDownloadService {

	private static final Logger log = LoggerFactory.getLogger(DmsDownloadService.class);

	/** Was der Controller für die Antwort braucht. Entsteht nur nach der Leseprüfung. */
	public record DmsDownloadableFile(String id, String fileName, String mimeType, Long sizeBytes, String sftpPath) {
	}

	private final DmsExactFieldValueResolveService dmsExactFieldValueResolveService;
	private final DmsSolrDocumentWriteService dmsSolrDocumentWriteService;
	private final SftpService sftpService;

	public DmsDownloadService(DmsExactFieldValueResolveService dmsExactFieldValueResolveService,
			DmsSolrDocumentWriteService dmsSolrDocumentWriteService, SftpService sftpService) {
		this.dmsExactFieldValueResolveService = dmsExactFieldValueResolveService;
		this.dmsSolrDocumentWriteService = dmsSolrDocumentWriteService;
		this.sftpService = sftpService;
	}

	/** Prüft, dass der current user die Datei sehen darf, und liefert, was die Antwort braucht. 404 sonst. */
	@PreAuthorize("hasRole('DMS')")
	public DmsDownloadableFile loadDownloadableFileWithReadAclAndGrantedAccessCheck(String id) {
		Map<String, Object> document = dmsExactFieldValueResolveService.loadDmsSolrDocumentFieldsForDownload(id);
		if (document == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Datei mit dieser id");
		}
		String sftpPath = text(document, DmsSolrDocumentFieldNames.SFTP_PATH);
		if (sftpPath == null) {
			log.error("Download: Dokument {} hat keinen sftpPath", id);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "zu dieser Datei fehlt der Ablageort");
		}
		String fileName = text(document, DmsSolrDocumentFieldNames.FILE_NAME);
		String mimeType = text(document, DmsSolrDocumentFieldNames.MIME_TYPE);
		Object size = document.get(DmsSolrDocumentFieldNames.FILE_SIZE_BYTES);
		return new DmsDownloadableFile(id, fileName != null ? fileName : id,
				mimeType != null ? mimeType : MediaType.APPLICATION_OCTET_STREAM_VALUE,
				size instanceof Number number ? number.longValue() : null, sftpPath);
	}

	/**
	 * Die Bytes vom SFTP in den Zielstrom, meist den der HTTP-Antwort. Keine eigene Prüfung: Ein DmsDownloadableFile gibt
	 * es nur aus loadDownloadableFileWithReadAclAndGrantedAccessCheck. Danach der Zähltick; bricht der Empfänger ab,
	 * wirft das Streamen und es wird nicht gezählt. Scheitert der Zähltick selbst, steht das nur im Log: Die Bytes sind
	 * schon beim Client, die Antwort lässt sich nicht mehr zurücknehmen.
	 */
	@PreAuthorize("hasRole('DMS')")
	public void streamFileBytesFromSftp(DmsDownloadableFile file, OutputStream target) {
		sftpService.readFileData(file.sftpPath(), target);
		log.info("Download: {} ({})", file.id(), file.fileName());
		try {
			dmsSolrDocumentWriteService.countDownloadOfDmsSolrDocument(file.id());
		} catch (RuntimeException e) {
			log.warn("Download: downloadCount von {} ließ sich nicht hochzählen: {}", file.id(), e.getMessage());
		}
	}

	private static String text(Map<String, Object> document, String name) {
		Object value = document.get(name);
		return value == null ? null : value.toString();
	}

}
