package codergod1337.app1.solr.transfer.service;

import codergod1337.app1.solr.SolrFieldName;
import codergod1337.app1.solr.client.SolrClientRegistry;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.core.service.SolrCoreService;
import codergod1337.app1.solr.document.service.SolrDocumentService;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.model.SolrFieldType;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.solr.transfer.model.SolrCoreImportAnalysis;
import codergod1337.app1.solr.transfer.model.SolrCoreImportAnalysis.FileField;
import codergod1337.app1.solr.transfer.model.SolrCoreImportAnalysis.MissingCoreField;
import codergod1337.app1.solr.transfer.model.SolrCoreImportMode;
import codergod1337.app1.solr.transfer.model.SolrCoreImportStatus;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * Der Import eines Kerns (User 2026-10-14), maximal robust: Die Datei muss nicht jedes Feld haben und darf Felder
 * anders benennen. Drei Schritte:
 *
 * 1. Prüfen (analyze): Die Datei wird einmal hochgeladen und bleibt als Temp-Datei auf dem Server, damit sie nicht für
 * den Import noch einmal übertragen wird. Einmal als Strom gelesen: wie viele Dokumente, ob die Schlusszeile des Exports
 * da ist, je Feld der Datei, ob der Kern es kennt und ob die Werte zum Typ passen, und welche Felder des Kerns fehlen.
 * Suggest-Zwillinge und Solrs _version_ werden übergangen, sie entstehen beim Schreiben neu.
 *
 * 2. Zuordnen: Für ein fehlendes Kernfeld darf ein unbekanntes Feld der Datei einspringen (mapping Datei → Kern). Jedes
 * unbekannte Feld der Datei ist zugeordnet oder ignoriert, und jedes essenzielle Feld des Kerns (SolrField.essential,
 * id immer) kommt aus der Datei oder über eine Zuordnung; sonst startet nichts.
 *
 * 3. Importieren (start): im Hintergrund, jedes Dokument einzeln (User 2026-10-14), ganz geschrieben. Modus
 * SKIP_EXISTING lässt Dokumente mit bekannter id stehen (geprüft je Block mit einer Abfrage), REPLACE ersetzt sie. Beim
 * ersten Fehler hält der Lauf an, mit Zeile, id und Meldung; ein neuer Start ab dieser Zeile macht dort weiter.
 *
 * Eine geprüfte Datei zur Zeit und ein Import zur Zeit; eine neue Prüfung ersetzt die alte Temp-Datei.
 */
@Service
public class SolrCoreImportService {

	private static final Logger log = LoggerFactory.getLogger(SolrCoreImportService.class);

	/** So oft meldet die Prüfung ihren Zwischenstand */
	private static final Duration PROGRESS_INTERVAL = Duration.ofSeconds(2);

	/** Solrs eigene Felder, die beim Schreiben neu entstehen */
	private static final Set<String> SKIPPED_FIELD_NAMES = Set.of("_version_", "_root_");

	/** So lang wird ein Beispielwert höchstens */
	private static final int SAMPLE_MAX_CHARS = 120;

	private final SolrCoreService solrCoreService;
	private final SolrFieldService solrFieldService;
	private final SolrDocumentService solrDocumentService;
	private final SolrClientRegistry solrClientRegistry;
	private final ObjectMapper objectMapper;

	/** So viele Dokumente je Block beim Prüfen der bekannten ids, app1.solr.transfer.batch-size */
	private final int batchSize;

	/** Ein Thread: nie zwei Importe gleichzeitig */
	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "solr-core-import");
		thread.setDaemon(true);
		return thread;
	});

	private final AtomicLong nextImportId = new AtomicLong(1);

	/** Die zuletzt geprüfte Datei, null ohne */
	private volatile PreparedImport preparedImport;

	/** Der letzte Lauf, null, solange seit dem Start des Backends keiner lief */
	private volatile Run currentRun;

	/** Eine geprüfte Datei: ihre Nummer, ihr Kern, wo sie liegt und was die Prüfung über sie weiß */
	private record PreparedImport(long importId, String coreKey, Path file, SolrCoreImportAnalysis analysis) {
	}

	/** Ein Dokument eines Blocks, fertig zum Schreiben */
	private record Pending(long line, String id, Map<String, Object> document) {
	}

	public SolrCoreImportService(SolrCoreService solrCoreService, SolrFieldService solrFieldService,
			SolrDocumentService solrDocumentService, SolrClientRegistry solrClientRegistry, ObjectMapper objectMapper,
			@Value("${app1.solr.transfer.batch-size}") int batchSize) {
		this.solrCoreService = solrCoreService;
		this.solrFieldService = solrFieldService;
		this.solrDocumentService = solrDocumentService;
		this.solrClientRegistry = solrClientRegistry;
		this.objectMapper = objectMapper;
		this.batchSize = batchSize;
	}

	@PreDestroy
	void shutdown() {
		Run run = currentRun;
		if (run != null) {
			run.cancelRequested = true;
		}
		executor.shutdownNow();
		deletePreparedFile();
	}

	// ===== 1. Prüfen =====

	/**
	 * Legt die hochgeladene Datei als Temp-Datei ab und liest sie einmal: onProgress bekommt alle zwei Sekunden die Zahl
	 * der gelesenen Dokumente. 404 ohne den Kern, 409 während ein Import läuft.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public SolrCoreImportAnalysis analyze(String coreKey, MultipartFile file, LongConsumer onProgress) throws IOException {
		solrCoreService.getSolrCoreByKey(coreKey);
		requireNoRunningImport();
		Path target = Files.createTempFile("solr-import-" + coreKey + "-", ".data");
		// transferTo verschiebt die Upload-Datei, wo es geht, statt sie zu kopieren; das Ziel darf dafür nicht da sein
		Files.delete(target);
		file.transferTo(target);

		Map<String, SolrField> coreFieldsByName = coreFieldsByName(coreKey);
		Set<String> twinNames = twinNamesOf(coreFieldsByName.values());
		Map<String, FieldStats> statsByName = new TreeMap<>();
		long documents = 0;
		long unreadable = 0;
		String firstUnreadable = null;
		Long declaredDocuments = null;
		long nextProgress = System.nanoTime() + PROGRESS_INTERVAL.toNanos();
		try (SolrCoreImportFileReader reader = new SolrCoreImportFileReader(target, objectMapper)) {
			SolrCoreImportFileReader.Entry entry;
			while ((entry = reader.next()) != null) {
				if (entry.fields() == null) {
					unreadable++;
					if (firstUnreadable == null) {
						firstUnreadable = "Zeile " + entry.line() + ": " + entry.unreadableReason();
					}
					continue;
				}
				if (isEndLine(entry.fields())) {
					declaredDocuments = ((Number) entry.fields().get(SolrCoreExportService.END_LINE_COUNT_KEY)).longValue();
					continue;
				}
				documents++;
				for (Map.Entry<String, Object> field : entry.fields().entrySet()) {
					if (isEmpty(field.getValue())) {
						continue;
					}
					FieldStats stats = statsByName.computeIfAbsent(field.getKey(), name -> new FieldStats());
					stats.count++;
					if (stats.sample == null) {
						stats.sample = abbreviated(field.getValue());
					}
					SolrField coreField = coreFieldsByName.get(field.getKey());
					if (coreField != null && !fits(coreField, field.getValue())) {
						stats.invalid++;
						if (stats.firstInvalid == null) {
							stats.firstInvalid = "Zeile " + entry.line() + ": " + abbreviated(field.getValue());
						}
					}
				}
				if (System.nanoTime() >= nextProgress) {
					onProgress.accept(documents);
					nextProgress = System.nanoTime() + PROGRESS_INTERVAL.toNanos();
				}
			}
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(target);
			throw e;
		}

		List<FileField> fileFields = new ArrayList<>();
		for (Map.Entry<String, FieldStats> entry : statsByName.entrySet()) {
			String name = entry.getKey();
			FieldStats stats = entry.getValue();
			SolrField coreField = coreFieldsByName.get(name);
			boolean skipped = SKIPPED_FIELD_NAMES.contains(name) || twinNames.contains(name);
			fileFields.add(new FileField(name, stats.count, stats.sample, coreField == null ? null : coreField.getType().name(),
					coreField != null && isEssential(coreField), skipped, stats.invalid, stats.firstInvalid));
		}
		List<MissingCoreField> missingCoreFields = new ArrayList<>();
		for (SolrField coreField : coreFieldsByName.values()) {
			if (!statsByName.containsKey(coreField.getName())) {
				missingCoreFields.add(new MissingCoreField(coreField.getName(), coreField.getType().name(),
						isEssential(coreField)));
			}
		}
		long importId = nextImportId.getAndIncrement();
		SolrCoreImportAnalysis analysis = new SolrCoreImportAnalysis(importId, coreKey, file.getOriginalFilename(),
				documents, declaredDocuments != null && declaredDocuments == documents, declaredDocuments, unreadable,
				firstUnreadable, fileFields, missingCoreFields);
		deletePreparedFile();
		preparedImport = new PreparedImport(importId, coreKey, target, analysis);
		log.info("Import {} geprüft: {} Dokumente, {} Felder, {} fehlen, {} unlesbar", coreKey, documents, fileFields.size(),
				missingCoreFields.size(), unreadable);
		return analysis;
	}

	/** Was die Prüfung je Feld der Datei zählt */
	private static final class FieldStats {

		private long count;
		private String sample;
		private long invalid;
		private String firstInvalid;

	}

	// ===== 2. und 3. Zuordnen und importieren =====

	/**
	 * Startet den Import der geprüften Datei im Hintergrund. mapping: Feld der Datei → Feld des Kerns, für Felder, die
	 * der Kern nicht kennt; ignored: Felder der Datei, die nicht mitgehen; fromLine: ab dieser Zeile, 1 für alles. 404,
	 * wenn importId nicht die zuletzt geprüfte Datei ist, 409, wenn schon ein Import läuft, 400 bei einer ungültigen
	 * Zuordnung, einem unentschiedenen unbekannten Feld oder einem essenziellen Feld ohne Quelle.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public synchronized SolrCoreImportStatus start(long importId, SolrCoreImportMode mode, Map<String, String> mapping,
			Set<String> ignored, long fromLine) {
		PreparedImport prepared = preparedImport;
		if (prepared == null || prepared.importId() != importId) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Import-Nummer unbekannt: die Datei bitte neu prüfen");
		}
		requireNoRunningImport();
		if (mode == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode fehlt");
		}
		if (fromLine < 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromLine muss mindestens 1 sein");
		}
		Map<String, SolrField> coreFieldsByName = coreFieldsByName(prepared.coreKey());
		Map<String, String> targetBySource = targetBySource(prepared.analysis(), coreFieldsByName,
				mapping == null ? Map.of() : mapping, ignored == null ? Set.of() : ignored);
		Set<String> essentialNames = new LinkedHashSet<>();
		for (SolrField coreField : coreFieldsByName.values()) {
			if (isEssential(coreField)) {
				essentialNames.add(coreField.getName());
			}
		}
		Set<String> coveredNames = new HashSet<>(targetBySource.values());
		List<String> uncovered = essentialNames.stream().filter(name -> !coveredNames.contains(name)).toList();
		if (!uncovered.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"essenzielle Felder ohne Quelle: " + String.join(", ", uncovered));
		}
		Run run = new Run(prepared, mode, Map.copyOf(targetBySource), Set.copyOf(essentialNames), fromLine);
		currentRun = run;
		executor.submit(new DelegatingSecurityContextRunnable(() -> execute(run)));
		log.info("Import {} gestartet ab Zeile {}, Modus {}, Zuordnung {}", prepared.coreKey(), fromLine, mode, mapping);
		return statusOf(run);
	}

	/** Der Stand des letzten Imports, null, solange seit dem Start des Backends keiner lief */
	@PreAuthorize("hasRole('ADMIN')")
	public SolrCoreImportStatus getStatus() {
		Run run = currentRun;
		return run == null ? null : statusOf(run);
	}

	/** Hält den laufenden Import nach dem aktuellen Dokument an; die Zeile danach steht im Stand zum Fortsetzen */
	@PreAuthorize("hasRole('ADMIN')")
	public SolrCoreImportStatus cancel() {
		Run run = currentRun;
		if (run == null) {
			return null;
		}
		if (run.finishedAt == null) {
			run.cancelRequested = true;
		}
		return statusOf(run);
	}

	/**
	 * Je Feld der Datei, wohin es geht: ein Feld, das der Kern kennt, in sich selbst; ein unbekanntes über die Zuordnung.
	 * Ignorierte und übergangene Felder (Zwillinge, _version_) fehlen. Jedes Ziel höchstens einmal.
	 */
	private static Map<String, String> targetBySource(SolrCoreImportAnalysis analysis,
			Map<String, SolrField> coreFieldsByName, Map<String, String> mapping, Set<String> ignored) {
		Map<String, FileField> fileFieldsByName = new HashMap<>();
		for (FileField fileField : analysis.fileFields()) {
			fileFieldsByName.put(fileField.name(), fileField);
		}
		for (String source : mapping.keySet()) {
			if (!fileFieldsByName.containsKey(source)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feld " + source + " steht nicht in der Datei");
			}
		}
		Map<String, String> targetBySource = new LinkedHashMap<>();
		List<String> undecided = new ArrayList<>();
		for (FileField fileField : analysis.fileFields()) {
			String name = fileField.name();
			if (fileField.skipped() || ignored.contains(name)) {
				continue;
			}
			String mapped = mapping.get(name);
			if (mapped != null) {
				if (!coreFieldsByName.containsKey(mapped)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ziel " + mapped + " ist kein Feld des Kerns");
				}
				targetBySource.put(name, mapped);
			} else if (coreFieldsByName.containsKey(name)) {
				targetBySource.put(name, name);
			} else {
				undecided.add(name);
			}
		}
		if (!undecided.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"dem Kern unbekannte Felder, bitte zuordnen oder ignorieren: " + String.join(", ", undecided));
		}
		Set<String> targets = new HashSet<>();
		for (String target : targetBySource.values()) {
			if (!targets.add(target)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "das Feld " + target + " bekäme zwei Quellen");
			}
		}
		return targetBySource;
	}

	private void execute(Run run) {
		String coreKey = run.prepared.coreKey();
		List<Pending> block = new ArrayList<>(batchSize);
		try (SolrCoreImportFileReader reader = new SolrCoreImportFileReader(run.prepared.file(), objectMapper)) {
			SolrCoreImportFileReader.Entry entry;
			while ((entry = reader.next()) != null) {
				if (run.cancelRequested) {
					run.stoppedAtLine = block.isEmpty() ? entry.line() : block.get(0).line();
					return;
				}
				run.line = entry.line();
				if (entry.line() < run.fromLine) {
					continue;
				}
				if (entry.fields() == null) {
					// was davor gelesen ist, geht noch raus, dann hält der Lauf an dieser Zeile
					if (writeBlock(run, block)) {
						stop(run, entry.line(), null, "kein JSON-Objekt: " + entry.unreadableReason());
					}
					return;
				}
				if (isEndLine(entry.fields())) {
					continue;
				}
				Map<String, Object> document;
				String problem;
				try {
					document = translated(entry.fields(), run.targetBySource);
					problem = missingEssentialOf(document, run.essentialNames);
				} catch (IllegalArgumentException e) {
					document = null;
					problem = e.getMessage();
				}
				if (problem != null) {
					Object id = entry.fields().get(SolrCore.ID_FIELD_NAME);
					if (writeBlock(run, block)) {
						stop(run, entry.line(), id == null ? null : String.valueOf(id), problem);
					}
					return;
				}
				block.add(new Pending(entry.line(), String.valueOf(document.get(SolrCore.ID_FIELD_NAME)), document));
				if (block.size() >= batchSize) {
					if (!writeBlock(run, block)) {
						return;
					}
					block.clear();
				}
			}
			writeBlock(run, block);
		} catch (IOException | RuntimeException e) {
			run.errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			log.warn("Import {} abgebrochen: {}", coreKey, run.errorMessage, e);
		} finally {
			run.finishedAt = Instant.now();
			log.info("Import {} {}: {} geschrieben, {} übersprungen{}", coreKey,
					run.stoppedAtLine == null ? "fertig" : "angehalten", run.written.get(), run.skippedExisting.get(),
					run.stoppedAtLine == null ? "" : ", Halt in Zeile " + run.stoppedAtLine);
		}
	}

	/**
	 * Schreibt einen Block, jedes Dokument einzeln. Im Modus SKIP_EXISTING fragt eine Abfrage vorher, welche ids es
	 * schon gibt. false, wenn der Lauf angehalten hat (Fehler oder Abbruch), dann steht die Zeile im Stand.
	 */
	private boolean writeBlock(Run run, List<Pending> block) {
		if (block.isEmpty()) {
			return true;
		}
		Set<String> existingIds = run.mode == SolrCoreImportMode.SKIP_EXISTING ? existingIdsOf(run, block) : Set.of();
		for (Pending pending : block) {
			if (run.cancelRequested) {
				run.stoppedAtLine = pending.line();
				return false;
			}
			if (existingIds.contains(pending.id())) {
				run.skippedExisting.incrementAndGet();
				continue;
			}
			try {
				solrDocumentService.writeNewDocument(run.prepared.coreKey(), pending.document());
				run.written.incrementAndGet();
			} catch (RuntimeException e) {
				stop(run, pending.line(), pending.id(), messageOf(e));
				return false;
			}
		}
		return true;
	}

	/** Die ids des Blocks, die es im Kern schon gibt: eine Abfrage für den ganzen Block */
	private Set<String> existingIdsOf(Run run, List<Pending> block) {
		List<String> terms = new ArrayList<>(block.size());
		for (Pending pending : block) {
			terms.add(ClientUtils.escapeQueryChars(pending.id()));
		}
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.set("q", SolrCore.ID_FIELD_NAME + ":(" + String.join(" OR ", terms) + ")");
		params.set("fl", SolrCore.ID_FIELD_NAME);
		params.set("rows", block.size());
		try {
			Set<String> existing = new HashSet<>();
			for (SolrDocument found : solrClientRegistry.clientFor(run.prepared.coreKey()).query(params).getResults()) {
				existing.add(String.valueOf(found.getFieldValue(SolrCore.ID_FIELD_NAME)));
			}
			return existing;
		} catch (SolrServerException | IOException e) {
			throw new IllegalStateException("Solr nicht erreichbar (Kern " + run.prepared.coreKey() + "): " + e.getMessage(), e);
		}
	}

	/** Die Felder eines Dokuments unter ihren Zielnamen; ohne Ziel fällt ein Feld weg. Ein JSON-Objekt als Wert ist ein Fehler. */
	private static Map<String, Object> translated(Map<String, Object> fields, Map<String, String> targetBySource) {
		Map<String, Object> document = new LinkedHashMap<>();
		for (Map.Entry<String, Object> field : fields.entrySet()) {
			String target = targetBySource.get(field.getKey());
			if (target == null || isEmpty(field.getValue())) {
				continue;
			}
			if (field.getValue() instanceof Map<?, ?>) {
				// Solr läse ein Objekt als Atomic Update und schriebe nur einen Teil des Dokuments
				throw new IllegalArgumentException("das Feld " + field.getKey() + " hat ein JSON-Objekt als Wert");
			}
			document.put(target, field.getValue());
		}
		return document;
	}

	/** Das erste essenzielle Feld ohne Wert, als Meldung; null, wenn alle da sind */
	private static String missingEssentialOf(Map<String, Object> document, Set<String> essentialNames) {
		for (String name : essentialNames) {
			if (isEmpty(document.get(name))) {
				return "das essenzielle Feld " + name + " fehlt";
			}
		}
		return null;
	}

	private static void stop(Run run, long line, String id, String message) {
		run.stoppedAtLine = line;
		run.stoppedAtId = id;
		run.errorMessage = message;
	}

	private void requireNoRunningImport() {
		Run running = currentRun;
		if (running != null && running.finishedAt == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "es läuft gerade ein Import in den Kern " + running.prepared.coreKey());
		}
	}

	private void deletePreparedFile() {
		PreparedImport prepared = preparedImport;
		preparedImport = null;
		if (prepared != null) {
			try {
				Files.deleteIfExists(prepared.file());
			} catch (IOException e) {
				log.warn("Import: Temp-Datei {} nicht gelöscht: {}", prepared.file(), e.getMessage());
			}
		}
	}

	/** Die Felder des Kerns nach Namen: seine eigenen, die jedes Kerns (id, cursorDate …) und seine Hook-Zeilen */
	private Map<String, SolrField> coreFieldsByName(String coreKey) {
		Map<String, SolrField> byName = new LinkedHashMap<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(coreKey)) {
			byName.put(solrField.getName(), solrField);
		}
		return byName;
	}

	/** Die Namen der Suggest- und Ngram-Zwillinge: Sie entstehen über copyField, aus der Datei kommen sie nie */
	private static Set<String> twinNamesOf(Collection<SolrField> coreFields) {
		Set<String> twinNames = new HashSet<>();
		for (SolrField coreField : coreFields) {
			if (coreField.isSuggest()) {
				twinNames.add(coreField.getName() + SolrFieldName.SUGGEST_SUFFIX);
			}
			if (coreField.isNgram()) {
				twinNames.add(coreField.getName() + SolrFieldName.NGRAM_SUFFIX);
			}
		}
		return twinNames;
	}

	/** id ist immer essenziell, sonst entscheidet der Schalter am Feld */
	private static boolean isEssential(SolrField coreField) {
		return coreField.isEssential() || SolrCore.ID_FIELD_NAME.equals(coreField.getName());
	}

	/** Die Schlusszeile des Exports: {"ende": Anzahl, "kern": key}, ohne id */
	private static boolean isEndLine(Map<String, Object> fields) {
		return !fields.containsKey(SolrCore.ID_FIELD_NAME)
				&& fields.get(SolrCoreExportService.END_LINE_COUNT_KEY) instanceof Number;
	}

	private static boolean isEmpty(Object value) {
		return value == null || (value instanceof String text && text.isBlank())
				|| (value instanceof Collection<?> values && values.isEmpty());
	}

	/** Passt ein Wert der Datei zu einem Feld des Kerns? Eine Liste nur bei multiValued, ein JSON-Objekt nie. */
	private static boolean fits(SolrField coreField, Object value) {
		if (value instanceof Map<?, ?>) {
			return false;
		}
		if (value instanceof Collection<?> values) {
			if (!coreField.isMultiValued() && values.size() > 1) {
				return false;
			}
			for (Object element : values) {
				if (!fitsOne(coreField.getType(), element)) {
					return false;
				}
			}
			return true;
		}
		return fitsOne(coreField.getType(), value);
	}

	private static boolean fitsOne(SolrFieldType type, Object value) {
		if (value == null) {
			return true;
		}
		if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
			return false;
		}
		return switch (type) {
			case STRING, EMAIL, TEXT_EDGE, TEXT_NGRAM, TEXT_SUCHE ->
				value instanceof String || value instanceof Number || value instanceof Boolean;
			case LONG -> value instanceof Integer || value instanceof Long || value instanceof Short
					|| value instanceof BigInteger || (value instanceof String text && parsesAsLong(text));
			case DOUBLE -> value instanceof Number || (value instanceof String text && parsesAsDouble(text));
			case BOOLEAN -> value instanceof Boolean
					|| (value instanceof String text && ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)));
			case DATE -> value instanceof String text && parsesAsInstant(text);
		};
	}

	private static boolean parsesAsLong(String text) {
		try {
			Long.parseLong(text.trim());
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static boolean parsesAsDouble(String text) {
		try {
			Double.parseDouble(text.trim());
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static boolean parsesAsInstant(String text) {
		try {
			Instant.parse(text.trim());
			return true;
		} catch (DateTimeParseException e) {
			return false;
		}
	}

	private static String abbreviated(Object value) {
		String text = String.valueOf(value);
		return text.length() <= SAMPLE_MAX_CHARS ? text : text.substring(0, SAMPLE_MAX_CHARS) + "…";
	}

	/** Die Meldung eines Fehlers: die Begründung einer Ablehnung, sonst die Meldung, sonst der Typ */
	private static String messageOf(Exception e) {
		if (e instanceof ResponseStatusException rejection && rejection.getReason() != null) {
			return rejection.getReason();
		}
		return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
	}

	private static SolrCoreImportStatus statusOf(Run run) {
		Instant end = run.finishedAt != null ? run.finishedAt : Instant.now();
		double seconds = Math.max(Duration.between(run.startedAt, end).toMillis() / 1000.0, 0.001);
		return new SolrCoreImportStatus(run.prepared.importId(), run.prepared.coreKey(), run.finishedAt == null,
				run.cancelRequested, run.mode, run.startedAt, run.finishedAt, run.fromLine, run.line, run.written.get(),
				run.skippedExisting.get(), run.prepared.analysis().documents(),
				(run.written.get() + run.skippedExisting.get()) / seconds, run.stoppedAtLine, run.stoppedAtId,
				run.errorMessage);
	}

	/** Ein Import: welche Datei, wie, wohin die Felder gehen, und sein Stand */
	private static final class Run {

		private final PreparedImport prepared;
		private final SolrCoreImportMode mode;
		private final Map<String, String> targetBySource;
		private final Set<String> essentialNames;
		private final long fromLine;
		private final Instant startedAt = Instant.now();
		private final AtomicLong written = new AtomicLong();
		private final AtomicLong skippedExisting = new AtomicLong();
		private volatile long line;
		private volatile boolean cancelRequested;
		private volatile Long stoppedAtLine;
		private volatile String stoppedAtId;
		private volatile String errorMessage;
		private volatile Instant finishedAt;

		private Run(PreparedImport prepared, SolrCoreImportMode mode, Map<String, String> targetBySource,
				Set<String> essentialNames, long fromLine) {
			this.prepared = prepared;
			this.mode = mode;
			this.targetBySource = targetBySource;
			this.essentialNames = essentialNames;
			this.fromLine = fromLine;
		}

	}

}
