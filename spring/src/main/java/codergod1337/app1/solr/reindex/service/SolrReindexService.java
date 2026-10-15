package codergod1337.app1.solr.reindex.service;

import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.core.service.SolrCoreService;
import codergod1337.app1.solr.dms.model.DmsReindexOption;
import codergod1337.app1.solr.dms.service.SolrDocumentDmsService;
import codergod1337.app1.solr.document.service.SolrDocumentService;
import codergod1337.app1.solr.nachrichten.model.NachrichtenReindexOption;
import codergod1337.app1.solr.nachrichten.service.SolrDocumentNachrichtenService;
import codergod1337.app1.solr.reindex.model.SolrReindexCheck;
import codergod1337.app1.solr.reindex.model.SolrReindexProgress;
import codergod1337.app1.solr.reindex.model.SolrReindexStatus;
import jakarta.annotation.PreDestroy;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Das Nachziehen eines Kerns aus dem SolrManager (User 2026-10-14), z. B. readAcl und writeAcl aller DMS-Dokumente aus
 * ihrer Dateiart, nachdem sich eine Dateiart geändert hat, oder aller Nachrichten aus ihrer Nachrichtenart. Hier nur
 * die Steuerung: Start, Stand, Abbruch und die Liste der geänderten ids. Was je Dokument geprüft und geschrieben wird,
 * steht im Service des Kerns (reindexDmsDocuments, reindexNachrichtenDocuments), die Schleife im SolrReindexIterator.
 *
 * Ein Lauf zur Zeit über alle Kerne, im Hintergrund mit dem Security-Context des Admins, der ihn startet. Die
 * geänderten ids schreibt der Lauf in eine Temp-Datei, eine je Zeile; sie gilt bis zum nächsten Lauf oder Neustart.
 *
 * Eine Option gibt es in jedem Kern: SEARCH_SUGGESTION_REBUILD, die Suche neu aufbauen
 * (SolrReindexSearchSuggestionRebuilder). Sie hat keine Kernlogik, läuft deshalb hier über den allgemeinen Schreibweg
 * (SolrDocumentService) und allein, ohne Liste der ids, denn sie schreibt jedes Dokument.
 */
@Service
public class SolrReindexService {

	private static final Logger log = LoggerFactory.getLogger(SolrReindexService.class);

	/**
	 * Die Option, die es in jedem Kern gibt: Suche neu aufbauen, jedes Dokument einmal neu schreiben
	 * (SolrReindexSearchSuggestionRebuilder, User 2026-10-14)
	 */
	public static final String SEARCH_SUGGESTION_REBUILD = "SEARCH_SUGGESTION_REBUILD";

	private final SolrDocumentDmsService solrDocumentDmsService;
	private final SolrDocumentNachrichtenService solrDocumentNachrichtenService;
	private final SolrReindexIterator solrReindexIterator;
	private final SolrDocumentService solrDocumentService;
	private final SolrCoreService solrCoreService;

	/** Ein Thread: nie zwei Läufe gleichzeitig */
	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "solr-reindex");
		thread.setDaemon(true);
		return thread;
	});

	/** Der letzte Lauf, null, solange seit dem Start des Backends keiner lief */
	private volatile Run currentRun;

	public SolrReindexService(SolrDocumentDmsService solrDocumentDmsService,
			SolrDocumentNachrichtenService solrDocumentNachrichtenService, SolrReindexIterator solrReindexIterator,
			SolrDocumentService solrDocumentService, SolrCoreService solrCoreService) {
		this.solrDocumentDmsService = solrDocumentDmsService;
		this.solrDocumentNachrichtenService = solrDocumentNachrichtenService;
		this.solrReindexIterator = solrReindexIterator;
		this.solrDocumentService = solrDocumentService;
		this.solrCoreService = solrCoreService;
	}

	@PreDestroy
	void shutdown() {
		Run run = currentRun;
		if (run != null) {
			run.progress.requestCancel();
		}
		executor.shutdownNow();
	}

	/**
	 * Startet das Nachziehen eines Kerns mit diesen Optionen (die Werte des Options-Enums des Kerns). 400 bei einem Kern
	 * ohne Nachziehen oder einer unbekannten Option, 409, wenn schon ein Lauf läuft.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public synchronized SolrReindexStatus start(String coreKey, List<String> options) {
		Run running = currentRun;
		if (running != null && running.finishedAt == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "es läuft schon ein Nachziehen im Kern " + running.coreKey);
		}
		Consumer<SolrReindexProgress> task = taskOf(coreKey, options);
		// der Neuaufbau schreibt jedes Dokument: eine Liste wäre der ganze Kern, er zählt nur
		boolean recordChangedIds = !options.contains(SEARCH_SUGGESTION_REBUILD);
		Path changedIdsFile = null;
		BufferedWriter changedIdsWriter = null;
		try {
			// die Liste des vorigen Laufs hat ausgedient
			if (running != null && running.changedIdsFile != null) {
				Files.deleteIfExists(running.changedIdsFile);
			}
			if (recordChangedIds) {
				changedIdsFile = Files.createTempFile("solr-reindex-" + coreKey + "-", ".txt");
				changedIdsWriter = Files.newBufferedWriter(changedIdsFile, StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Liste der geänderten ids nicht angelegt", e);
		}
		Run run = new Run(coreKey, List.copyOf(options), changedIdsFile, changedIdsWriter);
		currentRun = run;
		executor.submit(new DelegatingSecurityContextRunnable(() -> execute(run, task)));
		log.info("Nachziehen {} gestartet mit {}", coreKey, options);
		return statusOf(run);
	}

	/** Der Stand des letzten Laufs, null, solange seit dem Start des Backends keiner lief */
	@PreAuthorize("hasRole('ADMIN')")
	public SolrReindexStatus getStatus() {
		Run run = currentRun;
		return run == null ? null : statusOf(run);
	}

	/** Bricht den laufenden Lauf nach dem aktuellen Dokument ab; ohne laufenden Lauf geschieht nichts */
	@PreAuthorize("hasRole('ADMIN')")
	public SolrReindexStatus cancel() {
		Run run = currentRun;
		if (run == null) {
			return null;
		}
		if (run.finishedAt == null) {
			run.progress.requestCancel();
		}
		return statusOf(run);
	}

	/** Die Datei mit allen geänderten ids des letzten Laufs, eine je Zeile. 404 ohne Lauf, 409 solange er läuft. */
	@PreAuthorize("hasRole('ADMIN')")
	public Path getChangedIdsFile() {
		Run run = currentRun;
		if (run == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "es lief noch kein Nachziehen");
		}
		if (run.finishedAt == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "das Nachziehen läuft noch");
		}
		if (run.changedIdsFile == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "dieser Lauf führt keine Liste der geänderten ids");
		}
		return run.changedIdsFile;
	}

	/** Der Kern und seine Optionen als Aufgabe an seinen Service; prüft beides, bevor etwas läuft */
	private Consumer<SolrReindexProgress> taskOf(String coreKey, List<String> options) {
		if (options == null || options.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "options fehlt oder ist leer");
		}
		// Suche neu aufbauen: in jedem Kern gleich, ohne Kernlogik, darum hier und über den allgemeinen Schreibweg; allein,
		// denn er schreibt ohnehin jedes Dokument
		if (options.contains(SEARCH_SUGGESTION_REBUILD)) {
			if (options.size() > 1) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						SEARCH_SUGGESTION_REBUILD + " läuft nur allein, ohne weitere Optionen");
			}
			solrCoreService.getSolrCoreByKey(coreKey); // 404, wenn es den Kern nicht gibt
			List<SolrReindexCheck> checks = List.of(new SolrReindexSearchSuggestionRebuilder());
			return progress -> solrReindexIterator.iterate(coreKey, checks, update -> writeAtomicUpdate(coreKey, update),
					progress);
		}
		if (SolrDocumentNachrichtenService.CORE_KEY.equals(coreKey)) {
			Set<NachrichtenReindexOption> parsed = optionsOf(options, NachrichtenReindexOption.class);
			return progress -> solrDocumentNachrichtenService.reindexNachrichtenDocuments(parsed, progress);
		}
		if (SolrDocumentDmsService.DMS_CORE_KEY.equals(coreKey)) {
			Set<DmsReindexOption> parsed = optionsOf(options, DmsReindexOption.class);
			return progress -> solrDocumentDmsService.reindexDmsDocuments(parsed, progress);
		}
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "der Kern " + coreKey + " kennt kein Nachziehen");
	}

	/** Das Update der Schleife über den allgemeinen Schreibweg: die id und je Feld sein Modifier, z. B. {"set": …} */
	private void writeAtomicUpdate(String coreKey, SolrInputDocument update) {
		String id = null;
		Map<String, Map<String, Object>> modifiersByFieldName = new LinkedHashMap<>();
		for (SolrInputField field : update) {
			if (SolrCore.ID_FIELD_NAME.equals(field.getName())) {
				id = String.valueOf(field.getValue());
			} else if (field.getValue() instanceof Map<?, ?> modifier) {
				Map<String, Object> converted = new LinkedHashMap<>();
				modifier.forEach((operation, value) -> converted.put(String.valueOf(operation), value));
				modifiersByFieldName.put(field.getName(), converted);
			}
		}
		solrDocumentService.atomicUpdateDocument(coreKey, id, modifiersByFieldName);
	}

	private static <E extends Enum<E>> Set<E> optionsOf(List<String> options, Class<E> optionType) {
		Set<E> parsed = EnumSet.noneOf(optionType);
		for (String option : options) {
			try {
				parsed.add(Enum.valueOf(optionType, option));
			} catch (IllegalArgumentException e) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Option " + option + " ist unbekannt");
			}
		}
		return parsed;
	}

	private void execute(Run run, Consumer<SolrReindexProgress> task) {
		try {
			task.accept(run.progress);
			log.info("Nachziehen {} {}: {} geprüft, {} geändert, {} Fehler", run.coreKey,
					run.progress.isCancelRequested() ? "abgebrochen" : "fertig", run.progress.getChecked(),
					run.progress.getChanged(), run.progress.getFailed());
		} catch (RuntimeException e) {
			run.errorMessage = e.getMessage();
			log.warn("Nachziehen {} abgebrochen: {}", run.coreKey, e.getMessage(), e);
		} finally {
			try {
				if (run.changedIdsWriter != null) {
					run.changedIdsWriter.close();
				}
			} catch (IOException e) {
				log.warn("Nachziehen {}: Liste der geänderten ids nicht geschlossen: {}", run.coreKey, e.getMessage());
			}
			run.finishedAt = Instant.now();
		}
	}

	private static SolrReindexStatus statusOf(Run run) {
		SolrReindexProgress progress = run.progress;
		Instant end = run.finishedAt != null ? run.finishedAt : Instant.now();
		double seconds = Math.max(Duration.between(run.startedAt, end).toMillis() / 1000.0, 0.001);
		return new SolrReindexStatus(run.coreKey, run.finishedAt == null, progress.isCancelRequested(), run.options,
				run.startedAt, run.finishedAt, progress.getTotal(), progress.getChecked(), progress.getChanged(),
				progress.getFailed(), (progress.getChecked() + progress.getFailed()) / seconds,
				progress.isChangedIdsRecorded(), progress.getFirstChangedIds(), progress.getLastErrorMessage(),
				run.errorMessage);
	}

	/** Ein Lauf: was er tut, sein Stand und seine Liste */
	private static final class Run {

		private final String coreKey;
		private final List<String> options;
		private final Path changedIdsFile;
		private final BufferedWriter changedIdsWriter;
		private final SolrReindexProgress progress;
		private final Instant startedAt = Instant.now();
		private volatile Instant finishedAt;
		private volatile String errorMessage;

		private Run(String coreKey, List<String> options, Path changedIdsFile, BufferedWriter changedIdsWriter) {
			this.coreKey = coreKey;
			this.options = options;
			this.changedIdsFile = changedIdsFile;
			this.changedIdsWriter = changedIdsWriter;
			this.progress = new SolrReindexProgress(changedIdsWriter);
		}

	}

}
