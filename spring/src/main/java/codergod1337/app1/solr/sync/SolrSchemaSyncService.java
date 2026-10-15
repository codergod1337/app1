package codergod1337.app1.solr.sync;

import codergod1337.app1.solr.SolrFieldName;
import codergod1337.app1.solr.client.SolrClientRegistry;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.core.service.SolrCoreService;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.model.SolrFieldType;
import codergod1337.app1.solr.field.service.SolrFieldService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.request.GenericSolrRequest;
import org.apache.solr.client.solrj.request.schema.AnalyzerDefinition;
import org.apache.solr.client.solrj.request.schema.FieldTypeDefinition;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.schema.FieldTypeRepresentation;
import org.apache.solr.client.solrj.response.schema.SchemaResponse;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Der Abgleich der Felder: Postgres ist die Wahrheit, Solr wird nachgezogen. Je Kern liest der Abgleich das Schema
 * über die Schema-API, vergleicht es mit den Zeilen des Kerns in solr_field (Felder und Hooks) und schickt die Differenz in einer
 * Anfrage: add-field für fehlende Felder, replace-field für abweichende Schalter, für jedes Feld mit suggest den
 * Zwilling {@code <name>Suggested} und sein copyField. Solr schreibt dann die managed-schema.xml des Kerns neu und
 * lädt ihn einmal nach.
 *
 * Was der Abgleich nie tut: löschen (ein Feld, das es nur in Solr gibt, wird gemeldet) und einen Typ ändern (gemeldet,
 * eine Typänderung heißt Neuaufbau des Kerns, docs/backend/solr.md). Die Kerne selbst legt der Container an
 * (solr_cores in der .env), ein fehlender Kern wird gemeldet.
 *
 * Dazu die Klauselgrenze des Kerns: {@code query.maxBooleanClauses} steht in der solrconfig.xml, einer Kopie je Kern,
 * die niemand mehr anfasst. Der Abgleich liest den Wert über die Config-API und setzt ihn auf {@link #MAX_BOOLEAN_CLAUSES},
 * wenn er abweicht (set-property, Solr schreibt eine configoverlay.json und lädt den Kern nach). Die Grenze des Knotens
 * ({@code solr.max.booleanClauses}, solr.xml) hat keine API, sie steht als SOLR_OPTS in der Compose, beide 3000.
 *
 * Läuft beim Start für alle Kerne und nach jeder Änderung im SolrManager (SolrDefinitionChangedEvent), auf einem
 * eigenen Thread, damit kein Request auf Solr wartet. Lädt ein Kern noch (Containerstart, Nachladen nach einer
 * Änderung), wartet der Abgleich kurz und versucht es noch einmal. Ist Solr nicht erreichbar, steht das im Bericht,
 * das Backend läuft trotzdem.
 */
@Service
public class SolrSchemaSyncService {

	private static final Logger log = LoggerFactory.getLogger(SolrSchemaSyncService.class);

	/**
	 * Die Klauselgrenze je Kern (User 2026-10-07): 3000 statt 1024. Jedes Suchwort läuft über jedes Feld der qf samt
	 * Zwilling, mit vielen Hooks sind das schnell dreißig Klauseln je Wort. Derselbe Wert wie solr.max.booleanClauses in
	 * der Compose, denn die Grenze des Kerns kann die des Knotens nicht überschreiten.
	 */
	public static final int MAX_BOOLEAN_CLAUSES = 3000;

	/** So oft versucht der Abgleich es, wenn der Kern noch lädt, und so lange wartet er dazwischen */
	private static final int CORE_LOADING_ATTEMPTS = 10;
	private static final long CORE_LOADING_WAIT_MS = 3_000;

	/** Der Feldtyp, den der Abgleich in älteren Kernen nachlegt (ensureTextNgramFieldType) */
	private static final String TEXT_NGRAM_FIELD_TYPE_NAME = SolrFieldType.TEXT_NGRAM.getSolrFieldType();

	/** Felder des Basis-Configsets, die der Abgleich nie anfasst und nicht als fremd meldet */
	private static final Set<String> SYSTEM_FIELD_NAMES = Set.of("_version_", "_root_");

	/** Die Schalter, die der Abgleich vergleicht und per replace-field nachzieht */
	private static final List<String> COMPARED_FLAGS = List.of("indexed", "stored", "multiValued", "docValues",
			"required");

	private final SolrClientRegistry solrClientRegistry;
	private final SolrCoreService solrCoreService;
	private final SolrFieldService solrFieldService;

	/** Ein Thread, damit zwei Abgleiche desselben Kerns nie gleichzeitig laufen */
	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "solr-sync");
		thread.setDaemon(true);
		return thread;
	});

	/** Der letzte Bericht je Kern */
	private final Map<String, SolrCoreSyncReport> reportsByCoreKey = new ConcurrentHashMap<>();

	public SolrSchemaSyncService(SolrClientRegistry solrClientRegistry, SolrCoreService solrCoreService,
			SolrFieldService solrFieldService) {
		this.solrClientRegistry = solrClientRegistry;
		this.solrCoreService = solrCoreService;
		this.solrFieldService = solrFieldService;
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

	/**
	 * Ein Kern. Wirft nie: Jeder Fehler steht im Bericht. synchronized, damit Hintergrund und Admin sich nicht kreuzen.
	 * Lädt der Kern noch, wartet der Abgleich und versucht es noch einmal, bis zu CORE_LOADING_ATTEMPTS Mal.
	 */
	public synchronized SolrCoreSyncReport syncCore(String coreKey) {
		long start = System.nanoTime();
		SolrCoreSyncReport report = null;
		for (int attempt = 1; report == null; attempt++) {
			try {
				report = doSync(coreKey, start);
				log.info("Solr-Abgleich {}: {} Felder in Solr, {} neu, {} angepasst, {} copyFields neu, {} nur in Solr, {} Typkonflikte, Klauselgrenze {}{}, {} ms",
						coreKey, report.fieldsInSolr(), report.added().size(), report.replaced().size(),
						report.copyFieldsAdded().size(), report.onlyInSolr().size(), report.typeConflicts().size(),
						report.maxBooleanClauses(), report.maxBooleanClausesChanged() ? " (gesetzt)" : "", report.durationMs());
			} catch (Exception e) {
				if (isCoreStillLoading(e) && attempt < CORE_LOADING_ATTEMPTS && waitForCore(coreKey, attempt)) {
					continue;
				}
				String error = e.getClass().getSimpleName() + ": " + e.getMessage();
				log.warn("Solr-Abgleich {} fehlgeschlagen: {}", coreKey, error);
				report = SolrCoreSyncReport.failed(coreKey, error, millisSince(start));
			}
		}
		reportsByCoreKey.put(coreKey, report);
		return report;
	}

	/** „SolrCore is loading“ oder ein 503: Solr steht, der Kern noch nicht. Alles andere ist ein echter Fehler. */
	private static boolean isCoreStillLoading(Throwable e) {
		Throwable cause = e;
		while (cause != null) {
			if (cause instanceof SolrException solrException && solrException.code() == 503) {
				return true;
			}
			String message = cause.getMessage();
			if (message != null && message.contains("SolrCore is loading")) {
				return true;
			}
			cause = cause.getCause() == cause ? null : cause.getCause();
		}
		return false;
	}

	/** Kurz warten, bevor der Kern noch einmal gefragt wird. false, wenn der Thread beendet wird. */
	private static boolean waitForCore(String coreKey, int attempt) {
		log.info("Solr-Abgleich {}: der Kern lädt noch, Versuch {} von {} in {} s", coreKey, attempt + 1,
				CORE_LOADING_ATTEMPTS, CORE_LOADING_WAIT_MS / 1000);
		try {
			Thread.sleep(CORE_LOADING_WAIT_MS);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private SolrCoreSyncReport doSync(String coreKey, long start) throws SolrServerException, IOException {
		CoreAdminResponse status = CoreAdminRequest.getStatus(coreKey, solrClientRegistry.adminClient());
		// Einen fehlenden Kern meldet Solr nicht als Fehler, sondern als leeren Eintrag ohne Namen
		CoreStatusResponse.SingleCoreData coreStatus = status.getCoreStatus(coreKey);
		if (coreStatus == null || coreStatus.name == null) {
			return SolrCoreSyncReport.missingCore(coreKey, millisSince(start));
		}

		SolrClient client = solrClientRegistry.clientFor(coreKey);
		// zuerst der Feldtyp, sonst scheiterte ein add-field mit text_ngram in einem älteren Kern
		if (ensureTextNgramFieldType(client)) {
			log.info("Solr-Abgleich {}: Feldtyp {} angelegt", coreKey, TEXT_NGRAM_FIELD_TYPE_NAME);
		}
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
				planTwin(expected, SolrFieldName.SUGGEST_SUFFIX, expected.type().getSuggestSolrFieldType(), fieldsInSolr,
						copyFieldsInSolr, expectedNames, updates, added, replaced, typeConflicts, copyFieldsAdded);
			}
			// der Ngram-Zwilling (User 2026-10-16): jedes Stück ab 3 Zeichen trifft, das Feld selbst bleibt exakt
			if (expected.ngram() && expected.type().isNgramTwinAllowed()) {
				planTwin(expected, SolrFieldName.NGRAM_SUFFIX, TEXT_NGRAM_FIELD_TYPE_NAME, fieldsInSolr, copyFieldsInSolr,
						expectedNames, updates, added, replaced, typeConflicts, copyFieldsAdded);
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

		// Die Klauselgrenze zuletzt: set-property lädt den Kern nach, die Schemaänderungen sind dann schon durch
		int maxBooleanClauses = readMaxBooleanClauses(client);
		boolean maxBooleanClausesChanged = false;
		if (maxBooleanClauses != MAX_BOOLEAN_CLAUSES) {
			setMaxBooleanClauses(client);
			maxBooleanClauses = MAX_BOOLEAN_CLAUSES;
			maxBooleanClausesChanged = true;
		}

		int fieldsCount = (int) fieldsInSolr.keySet().stream().filter(name -> !SYSTEM_FIELD_NAMES.contains(name)).count()
				+ added.size();
		return new SolrCoreSyncReport(coreKey, true, true, fieldsCount, added, replaced, copyFieldsAdded, onlyInSolr,
				typeConflicts, maxBooleanClauses, maxBooleanClausesChanged, null, Instant.now(), millisSince(start));
	}

	/**
	 * Legt den Feldtyp text_ngram an, wenn der Kern ihn nicht kennt (User 2026-10-14), mit genau der Definition aus
	 * dem Configset (managed-schema.xml): Solr kopiert das Configset nur beim Anlegen eines Kerns, ein neuer Typ käme in
	 * die bestehenden sonst nie. true, wenn er angelegt wurde. Ein vorhandener Typ wird nie verändert.
	 */
	private static boolean ensureTextNgramFieldType(SolrClient client) throws SolrServerException, IOException {
		for (FieldTypeRepresentation fieldType : new SchemaRequest.FieldTypes().process(client).getFieldTypes()) {
			if (TEXT_NGRAM_FIELD_TYPE_NAME.equals(fieldType.getAttributes().get("name"))) {
				return false;
			}
		}
		AnalyzerDefinition indexAnalyzer = new AnalyzerDefinition();
		indexAnalyzer.setTokenizer(Map.of("class", "solr.StandardTokenizerFactory"));
		indexAnalyzer.setFilters(List.of(
				Map.of("class", "solr.LowerCaseFilterFactory"),
				Map.of("class", "solr.GermanNormalizationFilterFactory"),
				Map.of("class", "solr.NGramFilterFactory", "minGramSize", "3", "maxGramSize", "50",
						"preserveOriginal", "true")));
		AnalyzerDefinition queryAnalyzer = new AnalyzerDefinition();
		queryAnalyzer.setTokenizer(Map.of("class", "solr.StandardTokenizerFactory"));
		queryAnalyzer.setFilters(List.of(
				Map.of("class", "solr.LowerCaseFilterFactory"),
				Map.of("class", "solr.GermanNormalizationFilterFactory")));
		FieldTypeDefinition definition = new FieldTypeDefinition();
		definition.setAttributes(Map.of("name", TEXT_NGRAM_FIELD_TYPE_NAME, "class", "solr.TextField",
				"positionIncrementGap", "100"));
		definition.setIndexAnalyzer(indexAnalyzer);
		definition.setQueryAnalyzer(queryAnalyzer);
		SchemaResponse.UpdateResponse response = new SchemaRequest.AddFieldType(definition).process(client);
		Object errors = response.getResponse().get("errors");
		if (errors != null) {
			throw new IllegalStateException("Schema-API lehnt " + TEXT_NGRAM_FIELD_TYPE_NAME + " ab: " + errors);
		}
		return true;
	}

	/** Die wirksame Klauselgrenze des Kerns aus der Config-API: solrconfig.xml samt configoverlay.json */
	private static int readMaxBooleanClauses(SolrClient client) throws SolrServerException, IOException {
		NamedList<Object> response = new GenericSolrRequest(SolrRequest.METHOD.GET, "/config/query").process(client)
				.getResponse();
		Object query = child(response.get("config"), "query");
		Object value = child(query, "maxBooleanClauses");
		if (value == null) {
			throw new IllegalStateException("Config-API nennt kein query.maxBooleanClauses");
		}
		return Integer.parseInt(String.valueOf(value).strip());
	}

	/** Ein Eintrag einer verschachtelten Antwort, egal ob Solr sie als NamedList oder Map liefert */
	private static Object child(Object parent, String name) {
		if (parent instanceof NamedList<?> namedList) {
			return namedList.get(name);
		}
		if (parent instanceof Map<?, ?> map) {
			return map.get(name);
		}
		return null;
	}

	/** set-property über die Config-API: Solr schreibt die configoverlay.json des Kerns und lädt ihn nach */
	private static void setMaxBooleanClauses(SolrClient client) throws SolrServerException, IOException {
		String body = "{\"set-property\":{\"query.maxBooleanClauses\":" + MAX_BOOLEAN_CLAUSES + "}}";
		NamedList<Object> response = new GenericSolrRequest(SolrRequest.METHOD.POST, "/config")
				.withContent(body.getBytes(StandardCharsets.UTF_8), "application/json")
				.process(client)
				.getResponse();
		Object errors = response.get("errorMessages");
		if (errors != null) {
			throw new IllegalStateException("Config-API lehnt ab: " + errors);
		}
	}

	/**
	 * Ein Zwilling eines Feldes: {@code <name><suffix>} mit dem genannten Typ, indexed, nie stored (sonst verdoppelte
	 * jedes Atomic Update seine Werte), immer multiValued, dazu das copyField von der Quelle, wenn es noch fehlt.
	 */
	private static void planTwin(ExpectedField expected, String suffix, String twinSolrType,
			Map<String, Map<String, Object>> fieldsInSolr, Set<String> copyFieldsInSolr, Set<String> expectedNames,
			List<SchemaRequest.Update> updates, List<String> added, List<String> replaced, List<String> typeConflicts,
			List<String> copyFieldsAdded) {
		String twin = expected.name() + suffix;
		expectedNames.add(twin);
		planField(attributes(twin, twinSolrType, true, false, true, false, false), fieldsInSolr, updates, added,
				replaced, typeConflicts);
		if (!copyFieldsInSolr.contains(expected.name() + "→" + twin)) {
			updates.add(new SchemaRequest.AddCopyField(expected.name(), List.of(twin)));
			copyFieldsAdded.add(expected.name() + " → " + twin);
		}
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

	/** Alles, was der Kern laut Postgres hat: seine Felder, die jedes Kerns, die Zeilen der Hooks */
	private List<ExpectedField> expectedFields(String coreKey) {
		List<ExpectedField> expected = new ArrayList<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(coreKey)) {
			expected.add(new ExpectedField(solrField.getName(), solrField.getType(), solrField.isIndexed(),
					solrField.isStored(), solrField.isMultiValued(), solrField.isDocValues(), solrField.isRequired(),
					solrField.isSuggest(), solrField.isNgram()));
		}
		return expected;
	}

	private record ExpectedField(String name, SolrFieldType type, boolean indexed, boolean stored,
			boolean multiValued, boolean docValues, boolean required, boolean suggest, boolean ngram) {

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
