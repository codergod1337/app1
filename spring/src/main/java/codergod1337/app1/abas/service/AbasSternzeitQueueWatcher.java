package codergod1337.app1.abas.service;

import codergod1337.app1.abas.model.AbasProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Der Taktgeber des Pools: Er arbeitet nicht, er schaut nur nach. Der eigentliche Antrieb sind die Weckrufe nach jedem
 * Einreihen und nach jedem fertigen Worker; der Watcher ist das Sicherheitsnetz darunter für alles, was durch die
 * Maschen fällt: Einträge, die beim Neustart schon in der Queue lagen, verlorene Weckrufe und Claims abgestürzter
 * Worker. Zwei Handgriffe je Takt (app1.abas.fetch.watch-interval): Claims älter als claim-timeout freigeben, und den
 * Pool wecken, wenn freie Einträge anliegen. Ob geholt werden darf, entscheidet der Pool selbst.
 *
 * Ein eigener Daemon-Thread statt @Scheduled, wie der Abgleich in solr/sync: So gilt die Duration aus den Properties
 * direkt, und ein Takt startet erst, wenn der vorige fertig ist; hängt die Datenbank, stapeln sich keine Takte.
 */
@Service
public class AbasSternzeitQueueWatcher {

	private static final Logger log = LoggerFactory.getLogger(AbasSternzeitQueueWatcher.class);

	private final AbasProperties properties;
	private final AbasSternzeitQueueService abasSternzeitQueueService;
	private final AbasSternzeitFetchWorkerPool abasSternzeitFetchWorkerPool;

	private volatile boolean alive;
	private Thread watcher;

	public AbasSternzeitQueueWatcher(AbasProperties properties, AbasSternzeitQueueService abasSternzeitQueueService,
			AbasSternzeitFetchWorkerPool abasSternzeitFetchWorkerPool) {
		this.properties = properties;
		this.abasSternzeitQueueService = abasSternzeitQueueService;
		this.abasSternzeitFetchWorkerPool = abasSternzeitFetchWorkerPool;
	}

	@PostConstruct
	void start() {
		alive = true;
		watcher = new Thread(this::watchLoop, "AbasQueueWatcher");
		watcher.setDaemon(true);
		watcher.start();
	}

	@PreDestroy
	void stop() {
		alive = false;
		watcher.interrupt();
	}

	private void watchLoop() {
		long intervalMs = Math.max(1_000, properties.fetch().watchInterval().toMillis());
		while (alive) {
			try {
				Thread.sleep(intervalMs);
				tick();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception e) {
				// ein Aussetzer (DB kurz weg) darf den Watcher nicht beenden
				log.error("AbasQueueWatcher: {}", e.getMessage(), e);
			}
		}
	}

	/** Ein Takt: Absturz-Erholung, dann nachsehen, ob etwas anliegt */
	void tick() {
		int released = abasSternzeitQueueService
				.releaseStaleAbasSternzeitQueueClaims(Instant.now().minus(properties.fetch().claimTimeout()));
		if (released > 0) {
			log.warn("Absturz-Erholung: {} Queue-Claims älter als {} wieder freigegeben", released,
					properties.fetch().claimTimeout());
		}
		if (abasSternzeitQueueService.hasFreeAbasSternzeitQueueEntries()) {
			abasSternzeitFetchWorkerPool.wake();
		}
	}

}
