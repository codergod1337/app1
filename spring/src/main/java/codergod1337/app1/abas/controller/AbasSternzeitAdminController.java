package codergod1337.app1.abas.controller;

import codergod1337.app1.abas.model.AbasSternzeitForClient;
import codergod1337.app1.abas.model.AbasSternzeitImportCheck;
import codergod1337.app1.abas.model.AbasSternzeitImportResult;
import codergod1337.app1.abas.model.AbasSternzeitOverviewCounts;
import codergod1337.app1.abas.model.AbasSternzeitPage;
import codergod1337.app1.abas.model.AbasSternzeitQueueAction;
import codergod1337.app1.abas.model.AbasSternzeitQueueEnqueueResult;
import codergod1337.app1.abas.model.AbasSternzeitQueueSource;
import codergod1337.app1.abas.model.AbasSternzeitRawRequest;
import codergod1337.app1.abas.service.AbasSternzeitImportService;
import codergod1337.app1.abas.service.AbasSternzeitQueueService;
import codergod1337.app1.abas.service.AbasSternzeitResponseFetchService;
import codergod1337.app1.abas.service.AbasSternzeitService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Die Übersicht des AbasManagers: Kopfzahlen, die Liste der Stammsätze, der Treffer per Kennung, Einreihen von Hand,
 * die rohe Antwort von ABAS zum Hineinschauen, dazu Export und Import der Sternzeiten als Strom. Keine Parameter in
 * der URL, alles im Body.
 *
 * Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** und @PreAuthorize in den Services sichern ab. Das
 * Einreihen hat kein @PreAuthorize, weil der ERP-Eingang denselben Weg nimmt; hier reicht der Pfad.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/abas/sternzeit")
public class AbasSternzeitAdminController {

	/** Der Medientyp der gestreamten Antworten: eine JSON-Zeile je Eintrag */
	private static final String NDJSON = "application/x-ndjson";

	private final AbasSternzeitService abasSternzeitService;
	private final AbasSternzeitQueueService abasSternzeitQueueService;
	private final AbasSternzeitResponseFetchService abasSternzeitResponseFetchService;
	private final AbasSternzeitImportService abasSternzeitImportService;

	/** Liest data des Imports und schreibt die Zeilen der Antwortströme */
	private final ObjectMapper objectMapper;

	public AbasSternzeitAdminController(AbasSternzeitService abasSternzeitService,
			AbasSternzeitQueueService abasSternzeitQueueService,
			AbasSternzeitResponseFetchService abasSternzeitResponseFetchService,
			AbasSternzeitImportService abasSternzeitImportService, ObjectMapper objectMapper) {
		this.abasSternzeitService = abasSternzeitService;
		this.abasSternzeitQueueService = abasSternzeitQueueService;
		this.abasSternzeitResponseFetchService = abasSternzeitResponseFetchService;
		this.abasSternzeitImportService = abasSternzeitImportService;
		this.objectMapper = objectMapper;
	}

	/** Die Kopfzahlen der Übersicht */
	@GetMapping("/counts")
	public AbasSternzeitOverviewCounts getAbasSternzeitOverviewCounts() {
		return abasSternzeitService.getAbasSternzeitOverviewCounts();
	}

	/** Eine Seite der Liste. Body {"afterSternzeit": "(87448,19,0)"}, ohne oder leer heißt von vorn. */
	@PostMapping("/page")
	public AbasSternzeitPage getAbasSternzeitPage(@RequestBody(required = false) Map<String, Object> pageRequest) {
		Object afterSternzeit = pageRequest == null ? null : pageRequest.get("afterSternzeit");
		if (afterSternzeit != null && !(afterSternzeit instanceof String)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "afterSternzeit ist kein Text");
		}
		return abasSternzeitService.getAbasSternzeitPage((String) afterSternzeit);
	}

	/** Der Treffer per Kennung. Body {"sternzeit": "(87448,19,0)"}. 404, wenn es keinen Stammsatz gibt. */
	@PostMapping("/search")
	public AbasSternzeitForClient findAbasSternzeit(@RequestBody Map<String, Object> searchRequest) {
		return abasSternzeitService.findAbasSternzeit(sternzeitOf(searchRequest));
	}

	/**
	 * Von Hand einreihen. Body {"sternzeit": "(87448,19,0)", "action": "FETCH"} oder "DELETE". 403, wenn die Kennung
	 * gesperrt ist; alreadyQueued in der Antwort sagt, ob sie schon so drin stand.
	 */
	@PostMapping("/queue")
	public AbasSternzeitQueueEnqueueResult enqueueAbasSternzeit(@RequestBody Map<String, Object> enqueueRequest) {
		String sternzeit = sternzeitOf(enqueueRequest);
		AbasSternzeitQueueAction action;
		try {
			action = AbasSternzeitQueueAction.valueOf(String.valueOf(enqueueRequest.get("action")));
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action muss FETCH oder DELETE sein");
		}
		return abasSternzeitQueueService.enqueueAbasSternzeit(sternzeit, action, AbasSternzeitQueueSource.ADMIN);
	}

	/**
	 * Die rohe Antwort von ABAS zu einer Kennung, live geholt, zum Hineinschauen und zum Auswählen der Adressen fürs
	 * Mapping. Body {@link AbasSternzeitRawRequest}: die Kennung, dazu Kopf- und Tabellenfelder, leer heißt alle
	 * Kopffelder. 404, wenn ABAS die Kennung nicht kennt, 503, wenn ABAS nicht antwortet.
	 */
	@PostMapping("/raw")
	public JsonNode fetchAbasSternzeitRaw(@RequestBody AbasSternzeitRawRequest rawRequest) {
		String sternzeit = rawRequest.sternzeit() == null ? null : rawRequest.sternzeit().trim();
		return abasSternzeitResponseFetchService.fetchAbasResponseBySternzeit(sternzeit, rawRequest.headFieldNames(),
				rawRequest.tableFieldNames());
	}

	// ===== Export und Import (User 2026-10-13): gestreamt, für Dateien mit Millionen Zeilen =====

	/**
	 * Der Export: jeder Stammsatz eine Zeile {"sternzeit":…,"objectType":…}, als Datei abas-sternzeiten-<datum>.ndjson.
	 * Gestreamt, nichts liegt ganz im Speicher. Ein GET ohne Parameter, der Browser lädt über einen normalen Link.
	 */
	@GetMapping(value = "/export", produces = NDJSON)
	public ResponseEntity<StreamingResponseBody> exportAbasSternzeiten() {
		StreamingResponseBody body = abasSternzeitService::exportAbasSternzeiten;
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename("abas-sternzeiten-" + LocalDate.now() + ".ndjson").build().toString())
				.body(body);
	}

	/**
	 * Prüfen: Multipart mit der Datei file. Die Antwort ist ein Strom von Zeilen, alle zwei Sekunden
	 * {"kind":"progress","check":…}, am Ende {"kind":"check","check":…} oder {"kind":"error","message":…}. Gespeichert
	 * wird nichts. Die Datei legt Spring als Temp-Datei ab, gelesen wird sie als Strom.
	 */
	@PostMapping(value = "/import/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = NDJSON)
	public ResponseEntity<StreamingResponseBody> checkAbasSternzeitImportFile(@RequestPart("file") MultipartFile file) {
		requireFile(file);
		StreamingResponseBody body = output -> {
			Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
			try (InputStream input = file.getInputStream()) {
				AbasSternzeitImportCheck check = abasSternzeitImportService.checkAbasSternzeitImportFile(input,
						snapshot -> writeLine(writer, "progress", "check", snapshot));
				writeLine(writer, "check", "check", check);
			} catch (RuntimeException | IOException e) {
				writeLine(writer, "error", "message", messageOf(e));
			}
		};
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_NDJSON).body(body);
	}

	/**
	 * Importieren: Multipart mit der Datei file und data als {"objectTypes": ["obj:data:19:0"], "abasDatabaseNumbers":
	 * [19]}. Antwort wie beim Prüfen, mit result statt check. 400 vor dem Strom bei ungültiger Auswahl.
	 */
	@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = NDJSON)
	public ResponseEntity<StreamingResponseBody> importAbasSternzeitenFromFile(@RequestPart("file") MultipartFile file,
			@RequestPart(value = "data", required = false) String data) {
		requireFile(file);
		Map<String, Object> selection = readSelection(data);
		Set<String> objectTypes = readObjectTypes(selection.get("objectTypes"));
		Set<Integer> abasDatabaseNumbers = readAbasDatabaseNumbers(selection.get("abasDatabaseNumbers"));
		if (objectTypes.isEmpty() && abasDatabaseNumbers.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kein Objekttyp und keine Datenbank gewählt");
		}
		StreamingResponseBody body = output -> {
			Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
			try (InputStream input = file.getInputStream()) {
				AbasSternzeitImportResult result = abasSternzeitImportService.importAbasSternzeitenFromFile(input,
						objectTypes, abasDatabaseNumbers, snapshot -> writeLine(writer, "progress", "result", snapshot));
				writeLine(writer, "result", "result", result);
			} catch (RuntimeException | IOException e) {
				writeLine(writer, "error", "message", messageOf(e));
			}
		};
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_NDJSON).body(body);
	}

	/** Eine Zeile des Antwortstroms, sofort raus: Ohne flush käme der Zwischenstand erst, wenn der Puffer voll ist */
	private void writeLine(Writer writer, String kind, String key, Object value) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("kind", kind);
		line.put(key, value);
		try {
			writer.write(objectMapper.writeValueAsString(line));
			writer.write('\n');
			writer.flush();
		} catch (IOException e) {
			// der Browser hat abgebrochen: das beendet den Lauf
			throw new UncheckedIOException(e);
		}
	}

	/** Die Meldung eines Fehlers im Strom: die Begründung einer Ablehnung, sonst die Meldung, sonst der Typ */
	private static String messageOf(Exception e) {
		if (e instanceof ResponseStatusException rejection && rejection.getReason() != null) {
			return rejection.getReason();
		}
		return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
	}

	private static void requireFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file fehlt oder ist leer");
		}
	}

	/** data als JSON-Objekt; ohne data ein leeres, dann ist nichts gewählt. 400, wenn es kein JSON-Objekt ist. */
	private Map<String, Object> readSelection(String data) {
		if (data == null || data.isBlank()) {
			return Map.of();
		}
		try {
			Map<String, Object> selection = objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {
			});
			return selection == null ? Map.of() : selection;
		} catch (JacksonException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "data ist kein JSON-Objekt: " + e.getOriginalMessage());
		}
	}

	/** Die gewählten Objekttypen: Texte bis 50 Zeichen, das Spaltenmaß in abas_object_type */
	private static Set<String> readObjectTypes(Object raw) {
		Set<String> objectTypes = new LinkedHashSet<>();
		if (raw == null) {
			return objectTypes;
		}
		if (!(raw instanceof List<?> values)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "objectTypes ist keine Liste");
		}
		for (Object value : values) {
			if (!(value instanceof String objectType) || objectType.isBlank() || objectType.length() > 50) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "objectTypes enthält keinen gültigen Objekttyp");
			}
			objectTypes.add(objectType.trim());
		}
		return objectTypes;
	}

	/** Die gewählten Datenbanken für Sternzeiten ohne Typ: ganze Zahlen ab 0 */
	private static Set<Integer> readAbasDatabaseNumbers(Object raw) {
		Set<Integer> abasDatabaseNumbers = new LinkedHashSet<>();
		if (raw == null) {
			return abasDatabaseNumbers;
		}
		if (!(raw instanceof List<?> values)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "abasDatabaseNumbers ist keine Liste");
		}
		for (Object value : values) {
			if (!(value instanceof Integer number) || number < 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "abasDatabaseNumbers enthält keine ganze Zahl ab 0");
			}
			abasDatabaseNumbers.add(number);
		}
		return abasDatabaseNumbers;
	}

	/** Die Kennung aus dem Body, getrimmt; fehlt sie oder ist sie kein Text, 400. Die Klammerform prüft der Service. */
	private static String sternzeitOf(Map<String, Object> body) {
		if (body == null || !(body.get("sternzeit") instanceof String sternzeit) || sternzeit.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sternzeit fehlt");
		}
		return sternzeit.trim();
	}

}
