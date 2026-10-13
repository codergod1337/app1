package codergod1337.app1.abas.service;

import codergod1337.app1.abas.model.AbasProperties;
import codergod1337.app1.abas.model.AbasSternzeit;
import codergod1337.app1.abas.model.AbasSternzeitQueueAction;
import codergod1337.app1.abas.model.AbasSternzeitQueueCounts;
import codergod1337.app1.abas.model.AbasSternzeitQueueEnqueueResult;
import codergod1337.app1.abas.model.AbasSternzeitQueueEntry;
import codergod1337.app1.abas.model.AbasSternzeitQueueSource;
import codergod1337.app1.abas.model.AbasSternzeitQueuedEvent;
import codergod1337.app1.abas.repository.AbasSternzeitQueueEntryRepository;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Das Schreiben in die Warteschlange: einreihen, dem Dispatcher freie Einträge zuteilen, Einträge ausbuchen oder ihren
 * Fehler festhalten, alte Claims freigeben. Bewusst ohne Abhängigkeit zum Worker-Pool, damit nie ein Bean-Kreis
 * entsteht: Der Pool zeigt hierher, geweckt wird er über {@link AbasSternzeitQueuedEvent} nach dem Commit. Kein
 * @PreAuthorize an den Schreibwegen, denn das Einreihen dient auch dem öffentlichen ERP-Eingang ohne Token, und die
 * Worker haben keinen Security-Context; wer einreihen darf, haben die Controller vorher entschieden.
 */
@Service
public class AbasSternzeitQueueService {

	private static final Logger log = LoggerFactory.getLogger(AbasSternzeitQueueService.class);

	private final AbasSternzeitQueueEntryRepository abasSternzeitQueueEntryRepository;

	/**
	 * ZIRKELSCHLUSS: Das Einreihen fragt hier, ob die Kennung gesperrt ist. Umgekehrt muss das Sperren einen wartenden
	 * Queue-Eintrag entfernen und nutzt dafür ausnahmsweise direkt das AbasSternzeitQueueEntryRepository.
	 */
	private final AbasIgnoredSternzeitService abasIgnoredSternzeitService;
	private final AbasProperties properties;
	private final ApplicationEventPublisher applicationEventPublisher;

	/** Nur für das Paket-Einreihen des Imports, auf die eigene Tabelle abas_sternzeit_queue */
	private final JdbcTemplate jdbcTemplate;

	public AbasSternzeitQueueService(AbasSternzeitQueueEntryRepository abasSternzeitQueueEntryRepository,
			AbasIgnoredSternzeitService abasIgnoredSternzeitService, AbasProperties properties,
			ApplicationEventPublisher applicationEventPublisher, JdbcTemplate jdbcTemplate) {
		this.abasSternzeitQueueEntryRepository = abasSternzeitQueueEntryRepository;
		this.abasIgnoredSternzeitService = abasIgnoredSternzeitService;
		this.properties = properties;
		this.applicationEventPublisher = applicationEventPublisher;
		this.jdbcTemplate = jdbcTemplate;
	}

	// ===== Einreihen =====

	/**
	 * Reiht eine Kennung ein. 400 bei ungültiger Klammerform, 403 wenn sie auf der Sperrliste steht (User
	 * 2026-10-10), dann wird nichts geschrieben. Der Letzte gewinnt: Steht die Kennung schon mit der anderen Aktion
	 * drin, wird der Eintrag umgestellt, Quelle mit, queuedAt und ein laufender Claim bleiben. Gleiche Aktion: Eintrag
	 * unangetastet, alreadyQueued true. Nach dem Commit weckt das Event den Pool.
	 */
	@Transactional
	public AbasSternzeitQueueEnqueueResult enqueueAbasSternzeit(String sternzeit, AbasSternzeitQueueAction action,
			AbasSternzeitQueueSource source) {
		if (!AbasSternzeit.isValidSternzeit(sternzeit)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sternzeit fehlt oder hat nicht die Klammerform");
		}
		if (abasIgnoredSternzeitService.isSternzeitIgnored(sternzeit)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sternzeit " + sternzeit + " ist gesperrt");
		}
		AbasSternzeitQueueEntry existing = abasSternzeitQueueEntryRepository.findById(sternzeit).orElse(null);
		if (existing != null) {
			if (existing.getAction() == action) {
				return new AbasSternzeitQueueEnqueueResult(sternzeit, action, true);
			}
			existing.setAction(action);
			existing.setSource(source);
			abasSternzeitQueueEntryRepository.save(existing);
			log.info("Sternzeit {} in der Queue auf {} umgestellt (Quelle {})", sternzeit, action, source);
			publishQueuedAfterCommit(sternzeit, action);
			return new AbasSternzeitQueueEnqueueResult(sternzeit, action, false);
		}
		abasSternzeitQueueEntryRepository.save(new AbasSternzeitQueueEntry(sternzeit, action, source));
		log.info("Sternzeit {} als {} in die Queue eingereiht (Quelle {})", sternzeit, action, source);
		publishQueuedAfterCommit(sternzeit, action);
		return new AbasSternzeitQueueEnqueueResult(sternzeit, action, false);
	}

	/**
	 * Ein ganzes Paket zum Holen einreihen, für den Import einer Datei (User 2026-10-13): ungültige und doppelte
	 * Kennungen fallen weg, gesperrte auch, vorhandene Einträge bleiben, wie sie sind, auch mit anderer Aktion oder einem
	 * laufenden Claim. Eine einzige Anweisung je Paket ({@code INSERT … SELECT FROM unnest … ON CONFLICT DO NOTHING}),
	 * so kostet ein Paket von tausend Kennungen einen Weg zur Datenbank. Nach dem Commit weckt das Event den Pool.
	 *
	 * @return wie viele Kennungen neu in der Queue stehen
	 */
	@Transactional
	public int enqueueAbasSternzeiten(List<String> sternzeiten, AbasSternzeitQueueSource source) {
		List<String> valid = sternzeiten.stream().filter(AbasSternzeit::isValidSternzeit).distinct().toList();
		if (valid.isEmpty()) {
			return 0;
		}
		Set<String> ignored = abasIgnoredSternzeitService.getIgnoredSternzeitenAmong(valid);
		List<String> allowed = ignored.isEmpty() ? valid : valid.stream().filter(sternzeit -> !ignored.contains(sternzeit)).toList();
		if (allowed.isEmpty()) {
			return 0;
		}
		// die Queue-Tabelle selbst, nur per JDBC statt über das Repository: Eine Liste als Postgres-Array bindet JPA nicht verlässlich
		int queued = jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement(ENQUEUE_FETCH_BATCH_SQL);
			statement.setString(1, source.name());
			statement.setArray(2, connection.createArrayOf("varchar", allowed.toArray()));
			return statement;
		});
		if (queued > 0) {
			publishQueuedAfterCommit(allowed.get(0), AbasSternzeitQueueAction.FETCH);
		}
		log.info("Paket von {} Kennungen eingereiht: {} neu, Quelle {}", allowed.size(), queued, source);
		return queued;
	}

	/** Die Anweisung des Paket-Einreihens: jede Kennung des Arrays einmal, FETCH, jetzt eingereiht, frei, ohne Fehlversuch */
	private static final String ENQUEUE_FETCH_BATCH_SQL = "INSERT INTO abas_sternzeit_queue (sternzeit, action, source, queued_at, attempts) "
			+ "SELECT DISTINCT s, 'FETCH', ?, now(), 0 FROM unnest(?::varchar[]) AS s ON CONFLICT (sternzeit) DO NOTHING";

	/** Das Event erst nach dem Commit: Vorher sähe der Dispatcher den Eintrag noch nicht. Gleiches Muster wie im SolrHookService. */
	private void publishQueuedAfterCommit(String sternzeit, AbasSternzeitQueueAction action) {
		AbasSternzeitQueuedEvent event = new AbasSternzeitQueuedEvent(sternzeit, action);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					applicationEventPublisher.publishEvent(event);
				}
			});
		} else {
			applicationEventPublisher.publishEvent(event);
		}
	}

	// ===== Die Handgriffe des Pools und des Watchers =====

	/**
	 * Dem Dispatcher freie Einträge zuteilen: die ältesten unterhalb der Fehlergrenze, höchstens so viele wie Scheine
	 * frei sind, jeder bekommt claimedAt jetzt. Nur ein Dispatcher ruft das, deshalb kein Locking. Zurück kommen die
	 * Einträge mit der Aktion, die der Worker festhält (der Letzte gewinnt).
	 */
	@Transactional
	public List<AbasSternzeitQueueEntry> claimFreeAbasSternzeitQueueEntries(int limit) {
		List<AbasSternzeitQueueEntry> entries = abasSternzeitQueueEntryRepository
				.findFreeEntries(properties.fetch().maxAttempts(), PageRequest.of(0, Math.max(1, limit)));
		Instant now = Instant.now();
		for (AbasSternzeitQueueEntry entry : entries) {
			entry.setClaimedAt(now);
		}
		abasSternzeitQueueEntryRepository.saveAll(entries);
		return entries;
	}

	/**
	 * Ein Worker ist fertig: Eintrag löschen, aber nur, wenn er noch die Aktion trägt, mit der der Worker ihn gezogen
	 * hat. Wurde sie umgestellt, bleibt er stehen, nur der Claim wird freigegeben, sonst läge er bis zur Frist des
	 * Watchers als „in Arbeit“ herum.
	 */
	@Transactional
	public void finishAbasSternzeitQueueEntry(String sternzeit, AbasSternzeitQueueAction action) {
		long deleted = abasSternzeitQueueEntryRepository.deleteBySternzeitAndAction(sternzeit, action);
		if (deleted == 0) {
			abasSternzeitQueueEntryRepository.findById(sternzeit).ifPresent(entry -> {
				entry.setClaimedAt(null);
				abasSternzeitQueueEntryRepository.save(entry);
			});
			log.info("Sternzeit {}: Aktion wurde während der Arbeit umgestellt, Eintrag bleibt für den nächsten Umlauf",
					sternzeit);
		}
	}

	/**
	 * Ein Worker ist gescheitert: Zähler hoch, Meldung im Klartext, Claim frei. Der Eintrag bleibt auch nach der
	 * Fehlergrenze stehen, damit sein Fehler nachzulesen ist; dass er nicht mehr gezogen wird, entscheidet die Abfrage
	 * des Dispatchers.
	 */
	@Transactional
	public void failAbasSternzeitQueueEntry(String sternzeit, String errorMessage) {
		abasSternzeitQueueEntryRepository.findById(sternzeit).ifPresent(entry -> {
			entry.setAttempts(entry.getAttempts() + 1);
			entry.setLastErrorMessage(errorMessage);
			entry.setClaimedAt(null);
			abasSternzeitQueueEntryRepository.save(entry);
			if (entry.getAttempts() >= properties.fetch().maxAttempts()) {
				log.error("Sternzeit {} nach {} Fehlversuchen aufgegeben, bleibt mit ihrem Fehler in der Queue: {}",
						sternzeit, entry.getAttempts(), errorMessage);
			}
		});
	}

	/** Die Absturz-Erholung des Watchers: Claims, die älter sind als die Frist, wieder freigeben. */
	@Transactional
	public int releaseStaleAbasSternzeitQueueClaims(Instant before) {
		return abasSternzeitQueueEntryRepository.releaseClaimsOlderThan(before);
	}

	/** Liegt etwas an, das der Pool holen könnte? Der Watcher fragt das, bevor er weckt. */
	public boolean hasFreeAbasSternzeitQueueEntries() {
		return abasSternzeitQueueEntryRepository
				.existsByClaimedAtIsNullAndAttemptsLessThan(properties.fetch().maxAttempts());
	}

	// ===== Die Übersicht =====

	/** Die vordersten Einträge in FIFO-Ordnung, was als Nächstes drankommt; limit 1 bis 500 */
	@PreAuthorize("hasRole('ADMIN')")
	public List<AbasSternzeitQueueEntry> getAbasSternzeitQueueTop(int limit) {
		return abasSternzeitQueueEntryRepository
				.findAllByOrderByQueuedAtAsc(PageRequest.of(0, Math.max(1, Math.min(500, limit))));
	}

	/** Die Zahlen der Queue für die Übersicht: gesamt geschätzt ab einer halben Million, in Arbeit und mit Fehlern genau */
	@PreAuthorize("hasRole('ADMIN')")
	public AbasSternzeitQueueCounts getAbasSternzeitQueueCounts() {
		AbasRowCountEstimation.EstimatedOrExactRowCount queueCount = AbasRowCountEstimation.rowCount(
				abasSternzeitQueueEntryRepository.estimateRowCount(), abasSternzeitQueueEntryRepository::count);
		return new AbasSternzeitQueueCounts(queueCount.count(), queueCount.estimated(),
				abasSternzeitQueueEntryRepository.countByClaimedAtIsNotNull(),
				abasSternzeitQueueEntryRepository.countByAttemptsGreaterThan(0));
	}

}
