package codergod1337.app1.solr.reindex.service;

import codergod1337.app1.solr.client.SolrClientRegistry;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.reindex.model.SolrReindexCheck;
import codergod1337.app1.solr.reindex.model.SolrReindexProgress;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CursorMarkParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Die Schleife des Nachziehens, für jeden Kern dieselbe (User 2026-10-14): Was je Dokument geprüft und geschrieben
 * wird, bringen die Prüfungen mit (SolrReindexCheck), gebaut vom Service des Kerns, der auch schreibt.
 *
 * Gelesen wird blockweise über den Solr-Cursor, sortiert nach id (app1.solr.reindex.batch-size je Block), und nur die id
 * plus die Felder der Prüfungen, nie ein Vorschaubild oder der Volltext, wenn keine Prüfung sie braucht. Der Cursor ist
 * der Sortierwert des zuletzt geladenen Dokuments, der nächste Block beginnt dahinter. Weil der Lauf die id nie ändert,
 * bleibt ein geschriebenes Dokument hinter dem Cursor: Die 10 s bis Solr es zeigt, stören nicht, nichts kommt doppelt,
 * nichts fehlt. Deshalb nie nach einem Feld sortieren, das der Lauf oder andere gerade ändern, etwa cursorDate.
 *
 * Geschrieben wird jedes geänderte Dokument einzeln (User 2026-10-14), mit cursorDate: wann ein Durchlauf es zuletzt
 * angefasst hat. Kein Commit, das macht autoSoftCommit. Ein Fehler an einem Dokument zählt und der Lauf geht weiter;
 * erst MAX_FAILURES_IN_A_ROW hintereinander brechen ab, dann ist Solr oder der SFTP weg, nicht ein Dokument kaputt.
 */
@Service
public class SolrReindexIterator {

	private static final Logger log = LoggerFactory.getLogger(SolrReindexIterator.class);

	private static final int MAX_FAILURES_IN_A_ROW = 50;

	private final SolrClientRegistry solrClientRegistry;

	/** So viele Dokumente je gelesenem Block, app1.solr.reindex.batch-size */
	private final int batchSize;

	public SolrReindexIterator(SolrClientRegistry solrClientRegistry,
			@Value("${app1.solr.reindex.batch-size}") int batchSize) {
		this.solrClientRegistry = solrClientRegistry;
		this.batchSize = batchSize;
	}

	/**
	 * Läuft einmal über alle Dokumente des Kerns: je Dokument jede Prüfung, und hat eine etwas ins Update gelegt, geht
	 * es mit cursorDate an den writer, den Schreibweg des Kerns. Hört nach dem aktuellen Dokument auf, sobald der Stand
	 * einen Abbruch wünscht. Wirft, wenn Solr beim Lesen nicht antwortet oder zu viele Dokumente hintereinander scheitern.
	 */
	public void iterate(String coreKey, List<SolrReindexCheck> checks, Consumer<SolrInputDocument> writer,
			SolrReindexProgress progress) {
		if (checks.isEmpty()) {
			throw new IllegalArgumentException("keine Prüfung gewählt");
		}
		Set<String> fieldNames = new LinkedHashSet<>();
		fieldNames.add(SolrCore.ID_FIELD_NAME);
		for (SolrReindexCheck check : checks) {
			fieldNames.addAll(check.loadedFieldNames());
		}
		SolrClient client = solrClientRegistry.clientFor(coreKey);
		String cursorMark = CursorMarkParams.CURSOR_MARK_START;
		int failuresInARow = 0;
		while (!progress.isCancelRequested()) {
			ModifiableSolrParams params = new ModifiableSolrParams();
			params.set("q", "*:*");
			params.set("sort", SolrCore.ID_FIELD_NAME + " asc");
			params.set("rows", batchSize);
			params.set("fl", String.join(",", fieldNames));
			params.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
			QueryResponse response;
			try {
				response = client.query(params);
			} catch (SolrServerException | IOException e) {
				throw new IllegalStateException("Solr nicht erreichbar (Kern " + coreKey + "): " + e.getMessage(), e);
			}
			if (progress.getTotal() < 0) {
				progress.setTotal(response.getResults().getNumFound());
			}
			for (SolrDocument found : response.getResults()) {
				if (progress.isCancelRequested()) {
					return;
				}
				Map<String, Object> document = new LinkedHashMap<>(found);
				String id = String.valueOf(document.get(SolrCore.ID_FIELD_NAME));
				try {
					SolrInputDocument update = new SolrInputDocument();
					update.addField(SolrCore.ID_FIELD_NAME, id);
					boolean changed = false;
					// jede Prüfung kommt dran, auch wenn eine frühere schon geändert hat
					for (SolrReindexCheck check : checks) {
						if (check.addChanges(document, update)) {
							changed = true;
						}
					}
					if (changed) {
						update.addField(SolrCore.CURSOR_DATE_FIELD_NAME, Map.of("set", Date.from(Instant.now())));
						writer.accept(update);
						progress.changed(id);
					}
					progress.checked();
					failuresInARow = 0;
				} catch (RuntimeException e) {
					progress.failed(id, e.getMessage());
					log.warn("Nachziehen {} {}: {}", coreKey, id, e.getMessage());
					if (++failuresInARow >= MAX_FAILURES_IN_A_ROW) {
						throw new IllegalStateException(MAX_FAILURES_IN_A_ROW + " Fehler hintereinander, zuletzt " + id + ": "
								+ e.getMessage(), e);
					}
				}
			}
			// dieselbe Marke zurück heißt: keine weitere Seite
			String nextCursorMark = response.getNextCursorMark();
			if (cursorMark.equals(nextCursorMark)) {
				return;
			}
			cursorMark = nextCursorMark;
		}
	}

}
