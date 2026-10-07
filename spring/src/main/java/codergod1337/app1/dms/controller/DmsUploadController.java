package codergod1337.app1.dms.controller;

import codergod1337.app1.dms.model.DmsSolrDocumentExistenceCheck;
import codergod1337.app1.dms.service.DmsSolrDocumentExistenceService;
import codergod1337.app1.dms.service.DmsUploadService;
import codergod1337.app1.dms.service.DmsUploadService.DmsUploadProbe;
import codergod1337.app1.dms.service.DmsUploadService.DmsUploadResult;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Der Upload ins DMS. Multipart mit zwei Teilen: file ist die Datei, data ein JSON-Objekt mit Feldern des Kerns DMS
 * und Hooks, jedes Feld unter seinem Solr-Namen, multiValued als Liste. data darf fehlen, dann kommt die Datei allein.
 * Der Pfad liegt unter /api/rest/v1/**, also angemeldet, die AR DMS prüft der Service.
 *
 * Zwei Wege, eine Verarbeitung: /upload-probe prüft, liest und verwirft (bleibt dauerhaft, z. B. um die Spracherkennung
 * zu testen), /upload tut dasselbe und speichert danach (SFTP, Solr). Kein Flag im Body: Die Absicht steht in der URL,
 * die Rechte unterscheiden sich, und ein falsch gesetzter Schalter kann nicht speichern.
 */
@RestController
@RequestMapping("/api/rest/v1/dms")
public class DmsUploadController {

	/** Die id eines Dokuments ist der SHA-256 der Bytes: 64 Hex-Zeichen, klein */
	private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

	private final DmsUploadService dmsUploadService;
	private final DmsSolrDocumentExistenceService dmsSolrDocumentExistenceService;
	private final ObjectMapper objectMapper;

	public DmsUploadController(DmsUploadService dmsUploadService,
			DmsSolrDocumentExistenceService dmsSolrDocumentExistenceService, ObjectMapper objectMapper) {
		this.dmsUploadService = dmsUploadService;
		this.dmsSolrDocumentExistenceService = dmsSolrDocumentExistenceService;
		this.objectMapper = objectMapper;
	}

	/** Die Probe: prüft die Felder, liest die Datei mit Tika, rechnet die Vorschau, speichert nichts und antwortet mit allem, was ankam. */
	@PostMapping(value = "/upload-probe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public DmsUploadProbe probeUpload(@RequestPart("file") MultipartFile file,
			@RequestPart(value = "data", required = false) String data) {
		requireFile(file);
		return dmsUploadService.probeUpload(readData(data), file);
	}

	/**
	 * Der Upload: wie die Probe, dann speichern. Antwort mit status CREATED (neu), MERGED (dieselben Bytes lagen schon
	 * in dieser Dateiart, Hooks ergänzt) oder EXISTS (liegen unter einer anderen Dateiart, nichts geändert), der id und
	 * dem Dokument, wie es jetzt in Solr steht.
	 */
	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public DmsUploadResult upload(@RequestPart("file") MultipartFile file,
			@RequestPart(value = "data", required = false) String data) {
		requireFile(file);
		return dmsUploadService.uploadFile(readData(data), file);
	}

	/**
	 * Der Existenzcheck: Gibt es diese Bytes schon im DMS? Nur id, Dateiart und Status, ohne Rechteprüfung. Für die
	 * Vorprüfung der Maske: Sie rechnet den Hash im Browser und fragt hier.
	 */
	@GetMapping("/exists/{sha256}")
	public DmsSolrDocumentExistenceCheck exists(@PathVariable String sha256) {
		if (sha256 == null || !SHA256_HEX.matcher(sha256).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sha256 ist kein SHA-256 in Hex (64 Zeichen, klein)");
		}
		return dmsSolrDocumentExistenceService.loadDmsSolrDocumentExistenceCheck(sha256);
	}

	private static void requireFile(MultipartFile file) {
		if (file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file fehlt oder ist leer");
		}
	}

	/** data als JSON-Objekt, ohne data ein leeres. 400, wenn es kein JSON oder kein Objekt ist. */
	private JsonNode readData(String data) {
		if (data == null || data.isBlank()) {
			return objectMapper.createObjectNode();
		}
		JsonNode node;
		try {
			node = objectMapper.readTree(data);
		} catch (JacksonException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "data ist kein gültiges JSON: " + e.getOriginalMessage());
		}
		if (!node.isObject()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "data ist kein JSON-Objekt");
		}
		return node;
	}

}
