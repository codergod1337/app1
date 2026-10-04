package codergod1337.app1.dms.controller;

import codergod1337.app1.dms.service.DmsDownloadService;
import codergod1337.app1.dms.service.DmsDownloadService.DmsDownloadableFile;
import codergod1337.app1.system.HelperInputs;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Der Download einer Datei über ihre DMS-id, der einzige Weg zu den Bytes. GET mit der id in der URL, damit ein
 * normaler Link im Browser reicht, das Token kommt aus dem Cookie. Der Pfad liegt unter /api/rest/v1/**, also
 * angemeldet, die AR DMS und die Dokumentgrenze prüft der Service.
 *
 * Gestreamt wird im Thread der Anfrage, nicht später über einen StreamingResponseBody: So kennt der SFTP-Pool noch den
 * current user und seine Verbindungsgrenze.
 */
@RestController
@RequestMapping("/api/rest/v1/dms")
public class DmsDownloadController {

	private final DmsDownloadService dmsDownloadService;

	public DmsDownloadController(DmsDownloadService dmsDownloadService) {
		this.dmsDownloadService = dmsDownloadService;
	}

	/** Die Datei als Anhang mit ihrem Namen, MIME-Typ und ihrer Größe. 404, wenn es sie nicht gibt oder der User sie nicht sehen darf. */
	@GetMapping("/download/{id}")
	public void downloadFile(@PathVariable String id, HttpServletResponse response) throws IOException {
		if (id == null || !HelperInputs.isValidSha256Hex(id)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id ist kein SHA-256 in Hex (64 Zeichen, klein)");
		}
		DmsDownloadableFile file = dmsDownloadService.loadDownloadableFileWithReadAclAndGrantedAccessCheck(id);
		response.setContentType(file.mimeType());
		if (file.sizeBytes() != null) {
			response.setContentLengthLong(file.sizeBytes());
		}
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString());
		dmsDownloadService.streamFileBytesFromSftp(file, response.getOutputStream());
		response.flushBuffer();
	}

}
