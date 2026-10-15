package codergod1337.app1.solr.reindex.controller;

import codergod1337.app1.solr.reindex.model.SolrReindexStatus;
import codergod1337.app1.solr.reindex.service.SolrReindexService;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Das Nachziehen eines Kerns im SolrManager (User 2026-10-14). Nur ADMIN, das prüfen der Pfad und der Service. Ein
 * Lauf zur Zeit im Hintergrund, die Maske fragt den Stand ab; keine Parameter in der URL, alles im Body.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/solr/reindex")
public class SolrReindexController {

	private final SolrReindexService solrReindexService;

	public SolrReindexController(SolrReindexService solrReindexService) {
		this.solrReindexService = solrReindexService;
	}

	/**
	 * Startet einen Lauf: {"coreKey": "DMS", "options": ["ACLS_FROM_FILE_SUB_CLASS"]}. 400 bei einem Kern ohne
	 * Nachziehen oder einer unbekannten Option, 409, wenn schon einer läuft.
	 */
	@PostMapping("/start")
	public SolrReindexStatus start(@RequestBody Map<String, Object> body) {
		String coreKey = body.get("coreKey") instanceof String text ? text : null;
		if (coreKey == null || coreKey.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "coreKey fehlt");
		}
		List<String> options = body.get("options") instanceof List<?> rawOptions
				? rawOptions.stream().map(String::valueOf).toList()
				: List.of();
		return solrReindexService.start(coreKey, options);
	}

	/** Der Stand des letzten Laufs, 204, solange seit dem Start des Backends keiner lief */
	@GetMapping("/status")
	public ResponseEntity<SolrReindexStatus> getStatus() {
		SolrReindexStatus status = solrReindexService.getStatus();
		return status == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(status);
	}

	/** Bricht den laufenden Lauf nach dem aktuellen Dokument ab */
	@PostMapping("/cancel")
	public ResponseEntity<SolrReindexStatus> cancel() {
		SolrReindexStatus status = solrReindexService.cancel();
		return status == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(status);
	}

	/** Alle geänderten ids des letzten Laufs als Textdatei, eine je Zeile; ein normaler Download, das Token kommt aus dem Cookie */
	@GetMapping("/changed-ids")
	public ResponseEntity<Resource> getChangedIds() {
		Path file = solrReindexService.getChangedIdsFile();
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_PLAIN)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename("nachziehen-geaenderte-ids-" + LocalDate.now() + ".txt").build().toString())
				.body(new FileSystemResource(file));
	}

}
