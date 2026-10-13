package codergod1337.app1.abas.service;

import codergod1337.app1.abas.model.AbasProperties;
import codergod1337.app1.abas.model.AbasSternzeitFetcherState;
import codergod1337.app1.abas.model.AbasSternzeitFetcherStatus;
import codergod1337.app1.abas.model.AbasSternzeitQueueAction;
import codergod1337.app1.abas.model.AbasSternzeitQueueCounts;
import codergod1337.app1.abas.model.AbasSternzeitQueueEntry;
import codergod1337.app1.abas.model.AbasSternzeitQueuedEvent;
import codergod1337.app1.system.security.SystemUsersSecurityContextService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/**
 * Der Worker-Pool, der die Queue abarbeitet: ein Dispatcher verteilt, viele Worker holen (nach dem Muster der
 * Vorlage, abgestimmt 2026-10-10).
 *
 * Der Dispatcher ist ein einzelner Thread, nur er liest die Queue und vergibt Einträge, deshalb braucht es kein
 * DB-Locking. Er zieht immer nur so viele Einträge, wie die Semaphore Scheine frei hat, egal wie oft ihn jemand weckt:
 * Ein Weckruf zu viel verpufft. Geweckt wird er vom Queue-Service nach jedem Commit eines Einreihens (Event), vom
 * Watcher im Takt und von jedem Worker, der fertig wird; sonst schläft er, mit einem Sicherheitsnetz von fünf Sekunden.
 * Die Worker kommen aus einem Cached-Pool, die Grenze ist die Semaphore, nicht der Pool.
 *
 * Ein Worker bei FETCH: Antwort holen, den Objekttyp lesen und bei Bedarf anlegen, den Stammsatz fortschreiben, dann
 * als System-User über den Security-Context mappen und in den Kern des Ziels schreiben (seit 2026-10-13), den Eintrag
 * ausbuchen. Bei DELETE: den Stammsatz entfernen, Eintrag ausbuchen; das Auflösen in den Kernen kommt mit den Kernen.
 * Fehler: attempts hoch, Meldung an den Eintrag, Claim frei.
 */
@Service
public class AbasSternzeitFetchWorkerPool {

	private static final Logger log = LoggerFactory.getLogger(AbasSternzeitFetchWorkerPool.class);

	/** Unter 1 stünde das Holen still, dafür gibt es die Pause; über 100 soll ein Zahlendreher das ERP nicht fluten */
	private static final int MIN_PARALLEL = 1;
	private static final int MAX_PARALLEL = 100;

	/** Spätestens so lange schläft der Dispatcher, dann schaut er von selbst nach: fängt verlorene Weckrufe */
	private static final long WAIT_MS = 5_000;

	private final AbasProperties properties;
	/** Nur für isConfigured: Ohne Zugang zu ABAS zieht der Dispatcher nichts */
	private final AbasErpRestClientService abasErpRestClientService;
	private final AbasSternzeitResponseFetchService abasSternzeitResponseFetchService;
	private final AbasSternzeitQueueService abasSternzeitQueueService;
	private final AbasSternzeitService abasSternzeitService;
	private final AbasObjectTypeService abasObjectTypeService;
	/** Nach dem Holen: mappen und in den Kern des Ziels schreiben, als System-User */
	private final AbasSternzeitMappingService abasSternzeitMappingService;
	private final SystemUsersSecurityContextService systemUsersSecurityContextService;

	/** Die Weckglocke des Dispatchers */
	private final Object wakeBell = new Object();

	/** Der Schalter der Übersicht: aus heißt, die Queue füllt sich, verarbeitet wird nichts */
	private volatile boolean paused;

	/** Lebenszeichen der Bean, false beendet die Dispatcher-Schleife */
	private volatile boolean alive;

	private int parallel;
	private Semaphore freeSlots;
	private ExecutorService workers;
	private Thread dispatcher;

	/** Zähler seit dem Start der Bean, nur im Speicher: für die Badge und spätere Raten */
	private final AtomicInteger busyWorkers = new AtomicInteger();
	private final AtomicLong processedCount = new AtomicLong();
	private final AtomicLong failedCount = new AtomicLong();

	public AbasSternzeitFetchWorkerPool(AbasProperties properties, AbasErpRestClientService abasErpRestClientService,
			AbasSternzeitResponseFetchService abasSternzeitResponseFetchService,
			AbasSternzeitQueueService abasSternzeitQueueService, AbasSternzeitService abasSternzeitService,
			AbasObjectTypeService abasObjectTypeService, AbasSternzeitMappingService abasSternzeitMappingService,
			SystemUsersSecurityContextService systemUsersSecurityContextService) {
		this.properties = properties;
		this.abasErpRestClientService = abasErpRestClientService;
		this.abasSternzeitResponseFetchService = abasSternzeitResponseFetchService;
		this.abasSternzeitQueueService = abasSternzeitQueueService;
		this.abasSternzeitService = abasSternzeitService;
		this.abasObjectTypeService = abasObjectTypeService;
		this.abasSternzeitMappingService = abasSternzeitMappingService;
		this.systemUsersSecurityContextService = systemUsersSecurityContextService;
	}

	// ===== Lebenslauf =====

	@PostConstruct
	void start() {
		paused = properties.fetch().startPaused();
		parallel = Math.max(MIN_PARALLEL, Math.min(MAX_PARALLEL, properties.fetch().parallel()));
		freeSlots = new Semaphore(parallel);
		AtomicInteger number = new AtomicInteger();
		workers = Executors.newCachedThreadPool(runnable -> {
			Thread thread = new Thread(runnable, "AbasWorker_" + number.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		});
		alive = true;
		dispatcher = new Thread(this::dispatchLoop, "AbasDispatcher");
		dispatcher.setDaemon(true);
		dispatcher.start();
		log.info("AbasSternzeitFetchWorkerPool gestartet: {} Worker, Holen {}", parallel,
				paused ? "pausiert" : "aktiv");
	}

	@PreDestroy
	void stop() {
		alive = false;
		dispatcher.interrupt();
		workers.shutdownNow();
	}

	// ===== Die Handgriffe von außen =====

	/** Den Dispatcher wecken: Queue-Service nach dem Commit, Watcher im Takt, jeder fertige Worker */
	public void wake() {
		synchronized (wakeBell) {
			wakeBell.notifyAll();
		}
	}

	/** Nach jedem Einreihen: in Millisekunden holen statt erst beim nächsten Takt */
	@EventListener
	void onAbasSternzeitQueued(AbasSternzeitQueuedEvent event) {
		wake();
	}

	/** Holen anhalten: Laufende Worker werden fertig, neue Einträge zieht der Dispatcher nicht mehr. */
	@PreAuthorize("hasRole('ADMIN')")
	public void pause() {
		paused = true;
		log.info("ABAS-Holen pausiert");
	}

	/** Holen einschalten und sofort loslegen */
	@PreAuthorize("hasRole('ADMIN')")
	public void resume() {
		paused = false;
		log.info("ABAS-Holen eingeschaltet");
		wake();
	}

	/** Die Badge der Übersicht: Zustand, Worker, Queue und die Zähler seit dem Start */
	@PreAuthorize("hasRole('ADMIN')")
	public AbasSternzeitFetcherStatus getAbasSternzeitFetcherStatus() {
		AbasSternzeitFetcherState state;
		if (!abasErpRestClientService.isConfigured()) {
			state = AbasSternzeitFetcherState.NOT_CONFIGURED;
		} else if (paused) {
			state = AbasSternzeitFetcherState.PAUSED;
		} else {
			state = AbasSternzeitFetcherState.RUNNING;
		}
		AbasSternzeitQueueCounts queueCounts = abasSternzeitQueueService.getAbasSternzeitQueueCounts();
		return new AbasSternzeitFetcherStatus(state, parallel, busyWorkers.get(), queueCounts.queueCount(),
				queueCounts.queueCountEstimated(), processedCount.get(), failedCount.get());
	}

	// ===== Der Dispatcher: die eine Instanz, die die Queue liest =====

	private void dispatchLoop() {
		while (alive) {
			try {
				if (paused || !abasErpRestClientService.isConfigured()) {
					waitForWake();
					continue;
				}
				int free = freeSlots.availablePermits();
				if (free == 0) {
					waitForWake();
					continue;
				}
				List<AbasSternzeitQueueEntry> entries = abasSternzeitQueueService
						.claimFreeAbasSternzeitQueueEntries(free);
				if (entries.isEmpty()) {
					waitForWake();
					continue;
				}
				for (AbasSternzeitQueueEntry entry : entries) {
					submit(entry);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception e) {
				// die Schleife darf an einem Fehler (DB kurz weg) nicht sterben, sonst steht das Holen bis zum Neustart
				log.error("AbasDispatcher: {}, nächster Anlauf nach der Wartezeit", e.getMessage(), e);
				waitQuietly();
			}
		}
	}

	/** Einen zugeteilten Eintrag einem Worker übergeben; Aktion und Kennung werden jetzt festgehalten */
	private void submit(AbasSternzeitQueueEntry entry) throws InterruptedException {
		freeSlots.acquire();
		String sternzeit = entry.getSternzeit();
		AbasSternzeitQueueAction action = entry.getAction();
		try {
			workers.submit(() -> {
				try {
					process(sternzeit, action);
				} finally {
					freeSlots.release();
					// der fertige Worker läutet selbst: Sein Schein ist wieder frei, der Dispatcher kann nachlegen
					wake();
				}
			});
		} catch (RuntimeException e) {
			freeSlots.release();
			throw e;
		}
	}

	/** Schlafen bis zum nächsten Läuten, spätestens WAIT_MS */
	private void waitForWake() throws InterruptedException {
		synchronized (wakeBell) {
			wakeBell.wait(WAIT_MS);
		}
	}

	private void waitQuietly() {
		try {
			waitForWake();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	// ===== Der Worker: eine Kennung, ein Durchlauf =====

	private void process(String sternzeit, AbasSternzeitQueueAction action) {
		busyWorkers.incrementAndGet();
		try {
			if (action == AbasSternzeitQueueAction.DELETE) {
				abasSternzeitService.deleteAbasSternzeit(sternzeit);
			} else {
				fetch(sternzeit);
			}
			abasSternzeitQueueService.finishAbasSternzeitQueueEntry(sternzeit, action);
			processedCount.incrementAndGet();
		} catch (Exception e) {
			failedCount.incrementAndGet();
			String message = e instanceof ResponseStatusException rse && rse.getReason() != null ? rse.getReason()
					: String.valueOf(e.getMessage());
			log.warn("Sternzeit {} ({}) fehlgeschlagen: {}", sternzeit, action, message);
			try {
				abasSternzeitQueueService.failAbasSternzeitQueueEntry(sternzeit, message);
			} catch (Exception failure) {
				log.error("Fehler beim Festhalten des Fehlers zu Sternzeit {}: {}", sternzeit, failure.getMessage(),
						failure);
			}
		} finally {
			busyWorkers.decrementAndGet();
		}
	}

	/**
	 * Der FETCH-Weg: holen, Typ anlegen oder lesen, Stammsatz fortschreiben, dann als System-User mappen und in den Kern
	 * des Ziels schreiben (User 2026-10-13). Ein Typ ohne Mapping mit Kernziel schreibt nichts. Ein Fehler beim Mappen
	 * oder Schreiben wird der Fehler des Eintrags; gescheiterte Subfetches sind nur Warnungen im Dokument.
	 */
	private void fetch(String sternzeit) {
		// alle Kopffelder: Was das Mapping später braucht, steht erst mit der Vorschrift fest
		JsonNode response = abasSternzeitResponseFetchService.fetchAbasResponseBySternzeit(sternzeit, List.of(),
				List.of());
		JsonNode objectTypeNode = response.path("content").path("data").path("meta").path("objectType");
		Short objectTypeId = null;
		if (!objectTypeNode.isMissingNode() && !objectTypeNode.isNull() && !objectTypeNode.asString().isBlank()) {
			objectTypeId = abasObjectTypeService.getOrCreateAbasObjectType(objectTypeNode.asString()).getId();
		}
		abasSternzeitService.recordAbasSternzeitFetched(sternzeit, objectTypeId);
		String objectTypeText = objectTypeId == null ? "unbekannt" : objectTypeNode.asString();
		systemUsersSecurityContextService.runAsSystemUsers(() -> abasSternzeitMappingService
				.writeAbasSternzeitMappingFromResponse(response)
				.ifPresentOrElse(preview -> log.info("Sternzeit {} geholt (objectType {}), Mapping {}, {}", sternzeit,
						objectTypeText, preview.result().responseMappingKey(),
						preview.written() ? "geschrieben nach " + preview.result().targetKey() : "ohne Dokument"),
						() -> log.info("Sternzeit {} geholt (objectType {}), kein Mapping mit Kernziel", sternzeit,
								objectTypeText)));
	}

}
