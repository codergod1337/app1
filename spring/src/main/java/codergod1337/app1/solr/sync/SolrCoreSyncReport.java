package codergod1337.app1.solr.sync;

import java.time.Instant;
import java.util.List;

/**
 * Was der Abgleich eines Kerns mit Solr ergab. Nur ein Bericht, keine Entity: Er lebt im Speicher des
 * SolrSchemaSyncService und wird bei jedem Abgleich ersetzt.
 *
 * @param solrReachable false: Solr hat nicht geantwortet, error sagt warum
 * @param coreExists false: den Kern gibt es in Solr nicht, er fehlt in solr_cores der .env
 * @param fieldsInSolr wie viele Felder das Schema des Kerns hat, ohne die Systemfelder
 * @param added per add-field angelegt
 * @param replaced per replace-field angepasst (Schalter)
 * @param copyFieldsAdded per add-copy-field angelegt, „quelle → ziel“
 * @param onlyInSolr in Solr, aber nicht in Postgres: nur gemeldet, nie gelöscht
 * @param typeConflicts anderer Typ in Solr als in Postgres: nur gemeldet, eine Typänderung heißt Neuaufbau
 * @param error null, wenn der Abgleich durchlief
 */
public record SolrCoreSyncReport(String coreKey, boolean solrReachable, boolean coreExists, int fieldsInSolr,
		List<String> added, List<String> replaced, List<String> copyFieldsAdded, List<String> onlyInSolr,
		List<String> typeConflicts, String error, Instant syncedAt, long durationMs) {

	static SolrCoreSyncReport failed(String coreKey, String error, long durationMs) {
		return new SolrCoreSyncReport(coreKey, false, false, 0, List.of(), List.of(), List.of(), List.of(), List.of(),
				error, Instant.now(), durationMs);
	}

	static SolrCoreSyncReport missingCore(String coreKey, long durationMs) {
		return new SolrCoreSyncReport(coreKey, true, false, 0, List.of(), List.of(), List.of(), List.of(), List.of(),
				null, Instant.now(), durationMs);
	}

}
