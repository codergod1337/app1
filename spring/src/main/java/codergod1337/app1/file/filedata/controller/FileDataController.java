package codergod1337.app1.file.filedata.controller;

import codergod1337.app1.file.filedata.model.FileDataUploadProbe;
import codergod1337.app1.file.filedata.model.FileDataUploadResult;
import codergod1337.app1.file.filedata.service.FileDataService;
import codergod1337.app1.solr.dms.model.DmsExistence;
import codergod1337.app1.solr.dms.service.SolrDocumentDmsService;
import codergod1337.app1.solr.share.model.ShareToken;
import codergod1337.app1.solr.share.service.DmsShareService;
import codergod1337.app1.solr.share.service.ShareTokenService;
import codergod1337.app1.system.HelperInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Die Bytes einer Datei hinein und heraus. Upload als Multipart: file ist die Datei, data ein JSON-Objekt mit den
 * Feldern des Kerns DMS unter ihrem Solr-Namen (multiValued als Liste), previousId die Fassung, zu der die Datei die
 * neue Fassung wird (optional). Download angemeldet über readAcl oder guid-Freigabe, öffentlich über isPublic oder
 * einen Abholschein; die öffentlichen Pfade liegen unter /api/rest/v1/public/**.
 */
@RestController
public class FileDataController {

	private final FileDataService fileDataService;
	private final SolrDocumentDmsService solrDocumentDmsService;
	private final ShareTokenService shareTokenService;
	private final DmsShareService dmsShareService;
	private final ObjectMapper objectMapper;

	public FileDataController(FileDataService fileDataService, SolrDocumentDmsService solrDocumentDmsService,
			ShareTokenService shareTokenService, DmsShareService dmsShareService, ObjectMapper objectMapper) {
		this.fileDataService = fileDataService;
		this.solrDocumentDmsService = solrDocumentDmsService;
		this.shareTokenService = shareTokenService;
		this.dmsShareService = dmsShareService;
		this.objectMapper = objectMapper;
	}

	// ===== Upload =====

	/** Die Probe: prüft die Felder, liest die Datei mit Tika, rechnet die Vorschau, speichert nichts. */
	@PostMapping(value = "/api/rest/v1/filedata/upload-probe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public FileDataUploadProbe probeUpload(@RequestPart("file") MultipartFile file,
			@RequestPart(value = "data", required = false) String data) {
		requireFile(file);
		return fileDataService.probeUpload(file, readData(data));
	}

	/**
	 * Der Upload: wie die Probe, dann speichern. Antwort mit status CREATED, MERGED oder EXISTS, der id und dem Dokument.
	 * Steht versionChain in data, ist es ein Versionsupdate dieser Kette, sonst eine neue Datei mit neuer Kette.
	 */
	@PostMapping(value = "/api/rest/v1/filedata/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public FileDataUploadResult upload(@RequestPart("file") MultipartFile file,
			@RequestPart(value = "data", required = false) String data) {
		requireFile(file);
		return fileDataService.upload(file, readData(data));
	}

	/** Gibt es diese Bytes schon? Nur id, Dateiart und Status, ohne Rechteprüfung: die Kollisionserkennung der Uploadliste. */
	@GetMapping("/api/rest/v1/filedata/exists/{sha256}")
	public DmsExistence exists(@PathVariable String sha256) {
		requireId(sha256);
		return solrDocumentDmsService.getExistence(sha256);
	}

	// ===== Download =====

	/** Angemeldet: readAcl ODER die guid des Users in userGrantedAccess. 404, wenn es die Datei nicht gibt oder er sie nicht sehen darf. */
	@GetMapping("/api/rest/v1/filedata/download/{id}")
	public void downloadById(@PathVariable String id, HttpServletResponse response) throws IOException {
		requireId(id);
		fileDataService.streamDownload(solrDocumentDmsService.getDownloadById(id), response);
	}

	/** Öffentlich, ohne Anmeldung: nur mit isPublic true und Status ACTIVE. Sonst 404. */
	@GetMapping("/api/rest/v1/public/filedata/download/{id}")
	public void downloadPublicById(@PathVariable String id, HttpServletResponse response) throws IOException {
		requireId(id);
		fileDataService.streamDownload(solrDocumentDmsService.getPublicDownloadById(id), response);
	}

	/**
	 * Abholschein, ohne Anmeldung: der Klartext aus dem Link führt zur Zeile des Scheins (ACTIVE, nicht abgelaufen,
	 * Nutzungen übrig) und der Hash zum Dokument. Nur Scheine mit Download. Der vollständige Download zählt als Einlösung.
	 */
	@GetMapping("/api/rest/v1/public/filedata/token/{token}")
	public void downloadByToken(@PathVariable String token, HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		if (token == null || token.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "token fehlt");
		}
		ShareToken shareToken = shareTokenService.findRedeemableShareToken(token);
		if (shareToken == null || !shareToken.isBinaryIncluded()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Abholschein");
		}
		fileDataService.streamDownload(solrDocumentDmsService.getDownloadByToken(shareToken), response);
		// die letzte erlaubte Einlösung nimmt den Hash vom Dokument
		if (shareTokenService.redeemShareToken(shareToken, "DOWNLOAD", request.getRemoteAddr())) {
			dmsShareService.removePickupToken(shareToken.getDocumentId(), shareToken.getTokenHash());
		}
	}

	private static void requireFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file fehlt oder ist leer");
		}
	}

	private static void requireId(String id) {
		if (id == null || !HelperInputs.isValidSha256Hex(id)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id ist kein SHA-256 in Hex (64 Zeichen, klein)");
		}
	}

	/** data als JSON-Objekt, ohne data ein leeres. 400, wenn es kein JSON oder kein Objekt ist. */
	private Map<String, Object> readData(String data) {
		if (data == null || data.isBlank()) {
			return Map.of();
		}
		try {
			Map<String, Object> values = objectMapper.readValue(data, new tools.jackson.core.type.TypeReference<Map<String, Object>>() {
			});
			return values == null ? Map.of() : values;
		} catch (JacksonException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "data ist kein JSON-Objekt: " + e.getOriginalMessage());
		}
	}

}
