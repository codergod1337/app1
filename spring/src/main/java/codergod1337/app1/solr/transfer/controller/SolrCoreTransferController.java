package codergod1337.app1.solr.transfer.controller;

import codergod1337.app1.solr.transfer.model.SolrCoreImportAnalysis;
import codergod1337.app1.solr.transfer.model.SolrCoreImportMode;
import codergod1337.app1.solr.transfer.model.SolrCoreImportStatus;
import codergod1337.app1.solr.transfer.service.SolrCoreExportService;
import codergod1337.app1.solr.transfer.service.SolrCoreImportService;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.ObjectMapper;

/**
 * Export und Import je Solr-Kern als Sicherung (User 2026-10-14), nur über Admin-Endpunkte: Der Pfad
 * /api/rest/v1/admin/** und @PreAuthorize in den Services sichern ab. Im Body, nicht in der Adresse, steht der Kern; in
 * der Adresse nur die Long-Nummer des Downloads, damit ein normaler Link die Datei direkt auf die Platte schreibt.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/solr/transfer")
public class SolrCoreTransferController {

	private static final String NDJSON = "application/x-ndjson";

	private final SolrCoreExportService solrCoreExportService;
	private final SolrCoreImportService solrCoreImportService;
	private final ObjectMapper objectMapper;

	public SolrCoreTransferController(SolrCoreExportService solrCoreExportService,
			SolrCoreImportService solrCoreImportService, ObjectMapper objectMapper) {
		this.solrCoreExportService = solrCoreExportService;
		this.solrCoreImportService = solrCoreImportService;
		this.objectMapper = objectMapper;
	}

	// ===== Export =====

	/** Bereitet den Export eines Kerns vor: {"coreKey": "ARTIKEL"} → {"exportId": 7}, fünf Minuten gültig, einmal einlösbar */
	@PostMapping("/export/prepare")
	public Map<String, Long> prepareExport(@RequestBody Map<String, Object> body) {
		return Map.of("exportId", solrCoreExportService.prepareExport(requireCoreKey(body.get("coreKey"))));
	}

	/** Der Download: <kern>-<datum>.ndjson.gz als Strom; 404 bei unbekannter, abgelaufener oder benutzter Nummer */
	@GetMapping("/export/download/{exportId}")
	public ResponseEntity<StreamingResponseBody> downloadExport(@PathVariable long exportId) {
		String coreKey = solrCoreExportService.takeExport(exportId);
		StreamingResponseBody body = output -> solrCoreExportService.writeExport(coreKey, output);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/gzip"))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(coreKey.toLowerCase(Locale.ROOT) + "-" + LocalDate.now() + ".ndjson.gz").build().toString())
				.body(body);
	}

	// ===== Import =====

	/**
	 * Prüfen: Multipart mit der Datei file und coreKey. Die Antwort ist ein Strom von Zeilen, alle zwei Sekunden
	 * {"kind":"progress","documents":n}, am Ende {"kind":"analysis","analysis":…} oder {"kind":"error","message":…}. Die
	 * Datei bleibt auf dem Server, bis die nächste Prüfung sie ersetzt.
	 */
	@PostMapping(value = "/import/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = NDJSON)
	public ResponseEntity<StreamingResponseBody> analyzeImport(@RequestPart("file") MultipartFile file,
			@RequestPart("coreKey") String coreKey) {
		if (file == null || file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file fehlt oder ist leer");
		}
		String checkedCoreKey = requireCoreKey(coreKey);
		StreamingResponseBody body = output -> {
			Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
			try {
				SolrCoreImportAnalysis analysis = solrCoreImportService.analyze(checkedCoreKey, file,
						documents -> writeLine(writer, "progress", "documents", documents));
				writeLine(writer, "analysis", "analysis", analysis);
			} catch (RuntimeException | IOException e) {
				writeLine(writer, "error", "message", messageOf(e));
			}
		};
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_NDJSON).body(body);
	}

	/**
	 * Startet den Import der geprüften Datei: {"importId": 3, "mode": "SKIP_EXISTING", "mapping": {"artNr":
	 * "artikelNummer"}, "ignored": ["alt"], "fromLine": 1}. mapping und ignored nur für Felder, die der Kern nicht kennt.
	 */
	@PostMapping("/import/start")
	public SolrCoreImportStatus startImport(@RequestBody Map<String, Object> body) {
		if (!(body.get("importId") instanceof Number importId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "importId fehlt");
		}
		SolrCoreImportMode mode;
		try {
			mode = SolrCoreImportMode.valueOf(String.valueOf(body.get("mode")));
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode ist SKIP_EXISTING oder REPLACE");
		}
		Map<String, String> mapping = new LinkedHashMap<>();
		if (body.get("mapping") instanceof Map<?, ?> rawMapping) {
			rawMapping.forEach((source, target) -> {
				if (source != null && target != null && !String.valueOf(target).isBlank()) {
					mapping.put(String.valueOf(source), String.valueOf(target));
				}
			});
		}
		Set<String> ignored = new LinkedHashSet<>();
		if (body.get("ignored") instanceof List<?> rawIgnored) {
			rawIgnored.forEach(name -> ignored.add(String.valueOf(name)));
		}
		long fromLine = body.get("fromLine") instanceof Number number ? number.longValue() : 1;
		return solrCoreImportService.start(importId.longValue(), mode, mapping, ignored, fromLine);
	}

	/** Der Stand des letzten Imports, 204, solange seit dem Start des Backends keiner lief */
	@GetMapping("/import/status")
	public ResponseEntity<SolrCoreImportStatus> getImportStatus() {
		SolrCoreImportStatus status = solrCoreImportService.getStatus();
		return status == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(status);
	}

	/** Hält den laufenden Import nach dem aktuellen Dokument an */
	@PostMapping("/import/cancel")
	public ResponseEntity<SolrCoreImportStatus> cancelImport() {
		SolrCoreImportStatus status = solrCoreImportService.cancel();
		return status == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(status);
	}

	private static String requireCoreKey(Object rawCoreKey) {
		String coreKey = rawCoreKey instanceof String text ? text.trim() : null;
		if (coreKey == null || coreKey.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "coreKey fehlt");
		}
		return coreKey;
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

}
