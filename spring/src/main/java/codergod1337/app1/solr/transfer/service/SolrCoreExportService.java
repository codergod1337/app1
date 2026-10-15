package codergod1337.app1.solr.transfer.service;

import codergod1337.app1.solr.client.SolrClientRegistry;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.core.service.SolrCoreService;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.CursorMarkParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * Der Export eines Kerns als Sicherung (User 2026-10-14): Mit der Datei kommt ein leerer Kern wieder auf den Iststand.
 * Je Kern einzeln, gepackt (gzip), eine Zeile JSON je Dokument (NDJSON) und am Ende die Schlusszeile
 * {"ende": Anzahl, "kern": key}: Fehlt sie, ist der Download abgebrochen, der Import sieht das.
 *
 * Gelesen wird blockweise über den Solr-Cursor nach id (app1.solr.transfer.batch-size), jeder Block geht sofort in den
 * Strom und wird vergessen: Im Speicher liegt nie mehr als ein Block, auch bei 10^8 Dokumenten. Mit fl=* kommt jedes
 * gespeicherte Feld samt docValues, die Suggest-Zwillinge nicht (nicht gespeichert, sie entstehen beim Import über
 * copyField neu), Solrs _version_ wird weggelassen. Zeitpunkte stehen als ISO 8601 in UTC in der Datei.
 *
 * Der Download geht über eine kurzlebige Export-Nummer, nicht über den Kern-Key in der Adresse: In URLs stehen nur guid
 * und Long-ID, und ein normaler Link schreibt die Datei direkt auf die Platte statt in den Speicher des Browsers.
 */
@Service
public class SolrCoreExportService {

	private static final Logger log = LoggerFactory.getLogger(SolrCoreExportService.class);

	/** Die Schlüssel der Schlusszeile */
	public static final String END_LINE_COUNT_KEY = "ende";
	public static final String END_LINE_CORE_KEY = "kern";

	/** So lange gilt eine Export-Nummer: Der Link wird gleich nach dem Vorbereiten geöffnet */
	private static final Duration EXPORT_VALIDITY = Duration.ofMinutes(5);

	/** Solrs eigene Felder, die beim Schreiben neu entstehen */
	private static final Set<String> SKIPPED_FIELD_NAMES = Set.of("_version_", "_root_");

	private final SolrClientRegistry solrClientRegistry;
	private final SolrCoreService solrCoreService;
	private final ObjectMapper objectMapper;

	/** So viele Dokumente je gelesenem Block, app1.solr.transfer.batch-size */
	private final int batchSize;

	private final Map<Long, PreparedExport> preparedExports = new ConcurrentHashMap<>();
	private final AtomicLong nextExportId = new AtomicLong(1);

	/** Ein vorbereiteter Export: welcher Kern, bis wann die Nummer gilt */
	private record PreparedExport(String coreKey, Instant validUntil) {
	}

	public SolrCoreExportService(SolrClientRegistry solrClientRegistry, SolrCoreService solrCoreService,
			ObjectMapper objectMapper, @Value("${app1.solr.transfer.batch-size}") int batchSize) {
		this.solrClientRegistry = solrClientRegistry;
		this.solrCoreService = solrCoreService;
		this.objectMapper = objectMapper;
		this.batchSize = batchSize;
	}

	/** Die Nummer für den Download eines Kerns, fünf Minuten gültig und einmal einlösbar. 404 ohne den Kern. */
	@PreAuthorize("hasRole('ADMIN')")
	public long prepareExport(String coreKey) {
		solrCoreService.getSolrCoreByKey(coreKey);
		Instant now = Instant.now();
		preparedExports.values().removeIf(prepared -> prepared.validUntil().isBefore(now));
		long exportId = nextExportId.getAndIncrement();
		preparedExports.put(exportId, new PreparedExport(coreKey, now.plus(EXPORT_VALIDITY)));
		return exportId;
	}

	/** Löst die Nummer ein und nennt ihren Kern. 404, wenn es sie nicht gibt, sie abgelaufen oder schon eingelöst ist. */
	@PreAuthorize("hasRole('ADMIN')")
	public String takeExport(long exportId) {
		PreparedExport prepared = preparedExports.remove(exportId);
		if (prepared == null || prepared.validUntil().isBefore(Instant.now())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Export-Nummer unbekannt, abgelaufen oder schon benutzt");
		}
		return prepared.coreKey();
	}

	/**
	 * Schreibt alle Dokumente des Kerns gepackt in den Strom, danach die Schlusszeile. Gibt die Anzahl zurück. Bricht
	 * der Browser ab, endet der Lauf mit der IOException des Stroms.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public long writeExport(String coreKey, OutputStream target) throws IOException {
		long started = System.nanoTime();
		GZIPOutputStream gzip = new GZIPOutputStream(target, 1 << 16);
		Writer writer = new BufferedWriter(new OutputStreamWriter(gzip, StandardCharsets.UTF_8), 1 << 16);
		SolrClient client = solrClientRegistry.clientFor(coreKey);
		long count = 0;
		String cursorMark = CursorMarkParams.CURSOR_MARK_START;
		while (true) {
			ModifiableSolrParams params = new ModifiableSolrParams();
			params.set("q", "*:*");
			params.set("sort", SolrCore.ID_FIELD_NAME + " asc");
			params.set("rows", batchSize);
			params.set("fl", "*");
			params.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
			QueryResponse response;
			try {
				response = client.query(params);
			} catch (SolrServerException e) {
				throw new IOException("Solr nicht erreichbar (Kern " + coreKey + "): " + e.getMessage(), e);
			}
			for (SolrDocument document : response.getResults()) {
				Map<String, Object> line = new LinkedHashMap<>();
				for (Map.Entry<String, Object> field : document.entrySet()) {
					if (!SKIPPED_FIELD_NAMES.contains(field.getKey())) {
						line.put(field.getKey(), jsonValue(field.getValue()));
					}
				}
				writer.write(objectMapper.writeValueAsString(line));
				writer.write('\n');
				count++;
			}
			String nextCursorMark = response.getNextCursorMark();
			if (cursorMark.equals(nextCursorMark)) {
				break;
			}
			cursorMark = nextCursorMark;
		}
		Map<String, Object> endLine = new LinkedHashMap<>();
		endLine.put(END_LINE_COUNT_KEY, count);
		endLine.put(END_LINE_CORE_KEY, coreKey);
		writer.write(objectMapper.writeValueAsString(endLine));
		writer.write('\n');
		writer.flush();
		gzip.finish();
		log.info("Export {}: {} Dokumente in {} s", coreKey, count, (System.nanoTime() - started) / 1_000_000_000);
		return count;
	}

	/** Zeitpunkte als ISO 8601 in UTC, Listen elementweise, alles andere schreibt Jackson, wie es ist */
	private static Object jsonValue(Object value) {
		if (value instanceof Date date) {
			return date.toInstant().toString();
		}
		if (value instanceof Collection<?> values) {
			List<Object> converted = new ArrayList<>(values.size());
			for (Object element : values) {
				converted.add(jsonValue(element));
			}
			return converted;
		}
		return value;
	}

}
