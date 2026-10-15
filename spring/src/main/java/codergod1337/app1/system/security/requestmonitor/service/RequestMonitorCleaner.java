package codergod1337.app1.system.security.requestmonitor.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Der Aufräumer der beiden Maps (User 2026-10-15): alle app1.request-monitor.cleanup-interval (5 min) löscht er aus
 * der Map der Anfragen alles, was älter als app1.request-monitor.keep-for (1 h) ist, und aus der Sperrliste jede
 * abgelaufene Sperre. Ein eigener Daemon-Thread statt @Scheduled, wie der AbasSternzeitQueueWatcher: So gilt die
 * Duration aus der application.yml direkt, und ein Lauf startet erst, wenn der vorige fertig ist.
 */
@Service
public class RequestMonitorCleaner {

	private static final Logger log = LoggerFactory.getLogger(RequestMonitorCleaner.class);

	private final RequestMonitorService requestMonitorService;
	private final BlockedClientIpService blockedClientIpService;
	private final Duration cleanupInterval;
	private final Duration keepFor;

	private volatile boolean alive;
	private Thread cleaner;

	public RequestMonitorCleaner(RequestMonitorService requestMonitorService,
			BlockedClientIpService blockedClientIpService,
			@Value("${app1.request-monitor.cleanup-interval}") Duration cleanupInterval,
			@Value("${app1.request-monitor.keep-for}") Duration keepFor) {
		this.requestMonitorService = requestMonitorService;
		this.blockedClientIpService = blockedClientIpService;
		this.cleanupInterval = cleanupInterval;
		this.keepFor = keepFor;
	}

	@PostConstruct
	void start() {
		alive = true;
		cleaner = new Thread(this::cleanupLoop, "RequestMonitorCleaner");
		cleaner.setDaemon(true);
		cleaner.start();
	}

	@PreDestroy
	void stop() {
		alive = false;
		cleaner.interrupt();
	}

	private void cleanupLoop() {
		long intervalMs = Math.max(1_000, cleanupInterval.toMillis());
		while (alive) {
			try {
				Thread.sleep(intervalMs);
				tick();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception e) {
				// ein Aussetzer darf den Aufräumer nicht beenden
				log.error("RequestMonitorCleaner: {}", e.getMessage(), e);
			}
		}
	}

	/** Ein Lauf: Anfragen weg, die älter als keep-for sind, und Sperren weg, die abgelaufen sind */
	void tick() {
		Instant now = Instant.now();
		int deletedRequests = requestMonitorService.deleteLoggedClientRequestsReceivedBefore(now.minus(keepFor));
		int deletedBlocks = blockedClientIpService.deleteExpiredClientIpBlocks(now);
		if (deletedRequests > 0 || deletedBlocks > 0) {
			log.debug("RequestMonitorCleaner: {} Anfragen älter als {} und {} abgelaufene Sperren gelöscht",
					deletedRequests, keepFor, deletedBlocks);
		}
	}

}
