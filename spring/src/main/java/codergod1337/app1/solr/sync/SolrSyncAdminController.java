package codergod1337.app1.solr.sync;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Der Abgleich mit Solr für den Admin: die letzten Berichte je Kern lesen, oder jetzt abgleichen. Keine Admin-Prüfung
 * hier: Der Pfad /api/rest/v1/admin/** und @PreAuthorize im Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/solr/sync")
public class SolrSyncAdminController {

	private final SolrSchemaSyncService solrSchemaSyncService;

	public SolrSyncAdminController(SolrSchemaSyncService solrSchemaSyncService) {
		this.solrSchemaSyncService = solrSchemaSyncService;
	}

	/** Die letzten Berichte in der Reihenfolge der Kerne. Fehlt ein Kern, lief sein Abgleich noch nicht. */
	@GetMapping
	public List<SolrCoreSyncReport> getReports() {
		return solrSchemaSyncService.getReports();
	}

	/** Jetzt abgleichen: {"coreKey": "DMS"} für einen Kern, {} für alle. Zurück kommen alle Berichte. */
	@PostMapping
	public List<SolrCoreSyncReport> syncNow(@RequestBody(required = false) Map<String, Object> body) {
		String coreKey = body != null && body.get("coreKey") instanceof String key && !key.isBlank() ? key : null;
		return solrSchemaSyncService.syncNow(coreKey);
	}

}
