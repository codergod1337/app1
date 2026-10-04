package codergod1337.app1.solr.sync;

import codergod1337.app1.solr.SolrFieldName;
import codergod1337.app1.solr.client.SolrClientRegistry;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.core.service.SolrCoreService;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.model.SolrFieldType;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.service.SolrHookService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.solr.client.api.model.CoreStatusResponse;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.schema.SchemaResponse;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Der Abgleich der Felder: Postgres ist die Wahrheit, Solr wird nachgezogen. Je Kern liest der Abgleich das Schema
 * über die Schema-API, vergleicht es mit den Feldern des Kerns und den Hooks und schickt die Differenz in einer
 * Anfrage: add-field für fehlende Felder, replace-field für abweichende Schalter, für jedes Feld mit suggest den
 * Zwilling {@code <name>Suggested} und sein copyField. Solr schreibt dann die managed-schema.xml des Kerns neu und
 * lädt ihn einmal nach.
 *
 * Was der Abgleich nie tut: löschen (ein Feld, das es nur in Solr gibt, wird gemeldet) und einen Typ ändern (gemeldet,
 * eine Typänderung heißt Neuaufbau des Kerns, docs/backend/solr.md). Die Kerne selbst legt der Container an
 * (solr_cores in der .env), ein fehlender Kern wird gemeldet.
 *
 * Läuft beim Start für alle Kerne und nach jeder Änderung im SolrManager (SolrDefinitionChangedEvent), auf einem
 * eigenen Thread, damit kein Request auf Solr wartet. Ist Solr nicht erreichbar, steht das im Bericht, das Backend
 * läuft trotzdem.
 */
@Service
public class SolrSchemaSyncService {

	private static final Logger log = LoggerFactory.getLogger(SolrSchemaSyncService.class);

	/** Felder des Basis-Configsets, die der Abgleich nie anfasst und nicht als fremd meldet */
	private static final Set<String> SYSTEM_FIELD_NAMES = Set.of("_version_", "_root_");

	/** Die Schalter, die der Abgleich vergleicht und per replace-field nachzieht */
	private static final List<String> COMPARED_FLAGS = List.of("indexed", "stored", "multiValued", "docValues",
			"required");

	private final SolrClientRegistry solrClientRegistry;
	private final SolrCoreService solrCoreService;
	private final SolrFieldService solrFieldService;
	private final SolrHookService solrHookService;

	/** Ein Thread, damit zwei Abgleiche desselben Kerns nie gleichzeitig laufen */
	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "solr-sync");
		thread.setDaemon(true);
		return thread;
	});

	/** Der letzte Bericht je Kern */
	private final Map<String, SolrCoreSyncReport> reportsByCoreKey = new ConcurrentHashMap<>();

	public SolrSchemaSyncService(SolrClientRegistry solrClientRegistry, SolrCoreService solrCoreService,
			SolrFieldService solrFieldService, SolrHookService solrHookService) {
		this.solrClientRegistry = solrClientRegistry;
		this.solrCoreService = solrCoreService;
		this.solrFieldService = solrFieldService;
		this.solrHookService = solrHookService;
	}

	/** Beim Start alle Kerne, im Hintergrund: Der Start wartet nicht auf Solr. */
	@EventListener(ApplicationReadyEvent.class)
	void syncAllAtStartup() {
		executor.submit(this::syncAllCores);
	}

	/** Nach einer Änderung im SolrManager: der betroffene Kern, bei einem Hook alle. */
	@EventListener
	void onDefinitionChanged(SolrDefinitionChangedEvent event) {
		if (event.coreKey() == null) {
			executor.submit(this::syncAllCores);
		} else {
			executor.submit(() -> syncCore(event.coreKey()));
		}
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}

	/** Die letzten Berichte in der Reihenfolge der Kerne. Ein Kern ohne Bericht fehlt, bis der erste Abgleich lief. */
	@PreAuthorize("hasRole('ADMIN')")
	public List<SolrCoreSyncReport> getReports() {
		List<SolrCoreSyncReport> reports = new ArrayList<>();
		for (SolrCore solrCore : solrCoreService.getAllSolrCores()) {
			SolrCoreSyncReport report = reportsByCoreKey.get(solrCore.getKey());
			if (report != null) {
				reports.add(report);
			}
		}
		return reports;
	}

	/** Jetzt abgleichen, im Request-Thread, und die Berichte liefern. coreKey null: alle Kerne. */
	@PreAuthorize("hasRole('ADMIN')")
	public List<SolrCoreSyncReport> syncNow(String coreKey) {
		if (coreKey == null) {
			syncAllCores();
		} else {
			solrCoreService.getSolrCoreByKey(coreKey); // 404, wenn es ihn nicht gibt
			syncCore(coreKey);
		}
		return getReports();
	}

	private void syncAllCores() {
		for (SolrCore solrCore : solrCoreService.getAllSolrCores()) {
			syncCore(solrCore.getKey());
		}
	}

	/** Ein Kern. Wirft nie: Jeder Fehler steht im Bericht. synchronized, damit Hintergrund und Admin sich nicht kreuzen. */
	public synchronized SolrCoreSyncReport syncCore(String coreKey) {
		long start = System.nanoTime();
		SolrCoreSyncReport report;
		try {
			report = doSync(coreKey, start);
			log.info("Solr-Abgleich {}: {} Felder in Solr, {} neu, {} angepasst, {} copyFields neu, {} nur in Solr, {} Typkonflikte, {} ms",
					coreKey, report.fieldsInSolr(), report.added().size(), report.replaced().size(),
					report.copyFieldsAdded().size(), report.onlyInSolr().size(), report.typeConflicts().size(),
					report.durationMs());
		} catch (Exception e) {
			String error = e.getClass().getSimpleName() + ": " + e.getMessage();
			log.warn("Solr-Abgleich {} fehlgeschlagen: {}", coreKey, error);
			report = SolrCoreSyncReport.failed(coreKey, error, millisSince(start));
		}
		reportsByCoreKey.put(coreKey, report);
		return report;
	}

	private SolrCoreSyncReport doSync(String coreKey, long start) throws SolrServerException, IOException {
		CoreAdminResponse status = CoreAdminRequest.getStatus(coreKey, solrClientRegistry.adminClient());
		// Einen fehlenden Kern meldet Solr nicht als Fehler, sondern als leeren Eintrag ohne Namen
		CoreStatusResponse.SingleCoreData coreStatus = status.getCoreStatus(coreKey);
		if (coreStatus == null || coreStatus.name == null) {
			return SolrCoreSyncReport.missingCore(coreKey, millisSince(start));
		}

		SolrClient client = solrClientRegistry.clientFor(coreKey);
		Map<String, Map<String, Object>> fieldsInSolr = readFields(client);
		Set<String> copyFieldsInSolr = readCopyFields(client);

		List<SchemaRequest.Update> updates = new ArrayList<>();
		List<String> added = new ArrayList<>();
		List<String> replaced = new ArrayList<>();
		List<String> copyFieldsAdded = new ArrayList<>();
		List<String> typeConflicts = new ArrayList<>();
		Set<String> expectedNames = new HashSet<>();

		for (ExpectedField expected : expectedFields(coreKey)) {
			expectedNames.add(expected.name());
			planField(expected.attributes(), fieldsInSolr, updates, added, replaced, typeConflicts);
			if (expected.suggest() && expected.type().isSuggestable()) {
				String twin = expected.name() + SolrFieldName.SUGGEST_SUFFIX;
				expectedNames.add(twin);
				planField(attributes(twin, expected.type().getSuggestSolrFieldType(), true, false, true, false, false),
						fieldsInSolr, updates, added, replaced, typeConflicts);
				if (!copyFieldsInSolr.contains(expected.name() + "→" + twin)) {
					updates.add(new SchemaRequest.AddCopyField(expected.name(), List.of(twin)));
					copyFieldsAdded.add(expected.name() + " → " + twin);
				}
			}
		}

		List<String> onlyInSolr = fieldsInSolr.keySet().stream()
				.filter(name -> !expectedNames.contains(name) && !SYSTEM_FIELD_NAMES.contains(name))
				.sorted()
				.toList();

		if (!updates.isEmpty()) {
			SchemaResponse.UpdateResponse response = new SchemaRequest.MultiUpdate(updates).process(client);
			Object errors = response.getResponse().get("errors");
			if (errors != null) {
				throw new IllegalStateException("Schema-API lehnt ab: " + errors);
			}
		}

		int fieldsCount = (int) fieldsInSolr.keySet().stream().filter(name -> !SYSTEM_FIELD_NAMES.contains(name)).count()
				+ added.size();
		return new SolrCoreSyncReport(coreKey, true, true, fieldsCount, added, replaced, copyFieldsAdded, onlyInSolr,
				typeConflicts, null, Instant.now(), millisSince(start));
	}

	/** Ein erwartetes Feld gegen Solr: fehlt es, add-field; anderer Typ, nur melden; andere Schalter, replace-field. */
	private static void planField(Map<String, Object> attributes, Map<String, Map<String, Object>> fieldsInSolr,
			List<SchemaRequest.Update> updates, List<String> added, List<String> replaced, List<String> typeConflicts) {
		String name = (String) attributes.get("name");
		Map<String, Object> inSolr = fieldsInSolr.get(name);
		if (inSolr == null) {
			updates.add(new SchemaRequest.AddField(attributes));
			added.add(name);
			return;
		}
		if (!Objects.equals(inSolr.get("type"), attributes.get("type"))) {
			typeConflicts.add(name + ": Solr " + inSolr.get("type") + ", Postgres " + attributes.get("type"));
			return;
		}
		for (String flag : COMPARED_FLAGS) {
			if (!Objects.equals(Boolean.TRUE.equals(inSolr.get(flag)), attributes.get(flag))) {
				updates.add(new SchemaRequest.ReplaceField(attributes));
				replaced.add(name);
				return;
			}
		}
	}

	/** Die Felder des Kerns aus Postgres und die Hooks, die jeder Kern hat */
	private List<ExpectedField> expectedFields(String coreKey) {
		List<ExpectedField> expected = new ArrayList<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(coreKey)) {
			expected.add(new ExpectedField(solrField.getName(), solrField.getType(), solrField.isIndexed(),
					solrField.isStored(), solrField.isMultiValued(), solrField.isDocValues(), solrField.isRequired(),
					solrField.isSuggest()));
		}
		for (SolrHook solrHook : solrHookService.getAllSolrHooks()) {
			expected.add(new ExpectedField(solrHook.getKey(), SolrHook.FIELD_TYPE, SolrHook.FIELD_INDEXED,
					SolrHook.FIELD_STORED, SolrHook.FIELD_MULTI_VALUED, SolrHook.FIELD_DOC_VALUES,
					SolrHook.FIELD_REQUIRED, SolrHook.FIELD_SUGGEST));
		}
		return expected;
	}

	private record ExpectedField(String name, SolrFieldType type, boolean indexed, boolean stored,
			boolean multiValued, boolean docValues, boolean required, boolean suggest) {

		Map<String, Object> attributes() {
			return SolrSchemaSyncService.attributes(name, type.getSolrFieldType(), indexed, stored, multiValued,
					docValues, required);
		}

	}

	/** Die Attribute eines Feldes, wie die Schema-API sie nimmt */
	private static Map<String, Object> attributes(String name, String solrType, boolean indexed, boolean stored,
			boolean multiValued, boolean docValues, boolean required) {
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("name", name);
		attributes.put("type", solrType);
		attributes.put("indexed", indexed);
		attributes.put("stored", stored);
		attributes.put("multiValued", multiValued);
		attributes.put("docValues", docValues);
		attributes.put("required", required);
		return attributes;
	}

	/** Alle Felder des Kerns nach Name, mit den Vorgaben des Feldtyps, damit jeder Schalter einen Wert hat */
	private static Map<String, Map<String, Object>> readFields(SolrClient client)
			throws SolrServerException, IOException {
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.set("showDefaults", true);
		SchemaResponse.FieldsResponse response = new SchemaRequest.Fields(params).process(client);
		Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
		for (Map<String, Object> field : response.getFields()) {
			byName.put((String) field.get("name"), field);
		}
		return byName;
	}

	/** Alle copyFields des Kerns als „quelle→ziel“ */
	private static Set<String> readCopyFields(SolrClient client) throws SolrServerException, IOException {
		SchemaResponse.CopyFieldsResponse response = new SchemaRequest.CopyFields().process(client);
		Set<String> pairs = new HashSet<>();
		for (Map<String, Object> copyField : response.getCopyFields()) {
			pairs.add(copyField.get("source") + "→" + copyField.get("dest"));
		}
		return pairs;
	}

	private static long millisSince(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}

}
