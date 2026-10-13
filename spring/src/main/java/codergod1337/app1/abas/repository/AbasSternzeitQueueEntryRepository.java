package codergod1337.app1.abas.repository;

import codergod1337.app1.abas.model.AbasSternzeitQueueAction;
import codergod1337.app1.abas.model.AbasSternzeitQueueEntry;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Warteschlange. Jede Abfrage muss den Index {@code (claimed_at, queued_at)}, den Index {@code (queued_at)} oder
 * einen Teilindex aus docs/backend/abas-indizes.sql treffen.
 */
public interface AbasSternzeitQueueEntryRepository extends JpaRepository<AbasSternzeitQueueEntry, String> {

	/**
	 * Die eine Abfrage des Dispatchers: die freien Einträge unterhalb der Fehlergrenze, älteste zuerst, so viele wie
	 * Scheine frei sind. Der Index (claimed_at, queued_at) bedient sie vollständig: Die freien liegen im Index
	 * beieinander und darin schon nach queued_at sortiert.
	 */
	@Query("SELECT e FROM AbasSternzeitQueueEntry e WHERE e.claimedAt IS NULL AND e.attempts < :maxAttempts "
			+ "ORDER BY e.queuedAt ASC")
	List<AbasSternzeitQueueEntry> findFreeEntries(@Param("maxAttempts") int maxAttempts, Pageable pageable);

	/** Die vordersten Einträge in FIFO-Ordnung für die Übersicht, Index (queued_at) */
	List<AbasSternzeitQueueEntry> findAllByOrderByQueuedAtAsc(Pageable pageable);

	/** Liegt etwas an? Der Watcher fragt das im Takt, bevor er den Pool weckt; derselbe Index. */
	boolean existsByClaimedAtIsNullAndAttemptsLessThan(int maxAttempts);

	/**
	 * Die Absturz-Erholung: Claims, die älter sind als die Frist, wieder freigeben. Ein Fetch dauert rund eine
	 * Sekunde; wer nach Minuten noch „in Arbeit“ steht, dessen Worker lebt nicht mehr. Teilindex auf claimed_at.
	 */
	@Modifying
	@Transactional
	@Query("UPDATE AbasSternzeitQueueEntry e SET e.claimedAt = NULL WHERE e.claimedAt < :before")
	int releaseClaimsOlderThan(@Param("before") Instant before);

	/**
	 * Der Abschluss-Guard des Workers: löscht den Eintrag nur, wenn er noch die Aktion trägt, mit der der Worker ihn
	 * gezogen hat. 0 heißt, die Aktion wurde während der Arbeit umgestellt, der Eintrag bleibt für den nächsten Umlauf.
	 */
	@Modifying
	@Transactional
	long deleteBySternzeitAndAction(String sternzeit, AbasSternzeitQueueAction action);

	/** Die geschätzte Zeilenzahl aus dem Postgres-Katalog, -1 wenn nie analysiert; siehe AbasSternzeitRepository */
	@Query(value = "SELECT reltuples::bigint FROM pg_class WHERE oid = 'abas_sternzeit_queue'::regclass", nativeQuery = true)
	long estimateRowCount();

	/** Einträge, die gerade ein Worker hält: immer klein, Teilindex auf claimed_at */
	long countByClaimedAtIsNotNull();

	/** Einträge mit mindestens so vielen Fehlversuchen: immer klein, Teilindex auf attempts */
	long countByAttemptsGreaterThan(int attempts);

}
