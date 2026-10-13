package codergod1337.app1.abas.repository;

import codergod1337.app1.abas.model.AbasSternzeit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Stammtabelle der Sternzeiten, gebaut für 10^9 Zeilen. Hier kommen nur Abfragen hinein, die der Primärschlüssel,
 * der eindeutige Index auf sternzeit oder ein Teilindex bedient: kein count(*), kein LIKE, kein OFFSET.
 */
public interface AbasSternzeitRepository extends JpaRepository<AbasSternzeit, Long> {

	/** Der Treffer per Kennung, über den eindeutigen Index */
	Optional<AbasSternzeit> findBySternzeit(String sternzeit);

	/**
	 * Die nächste Seite der Liste per Keyset: alle Kennungen hinter dem Anker, aufsteigend, so viele wie das Pageable
	 * sagt. Der Anker ist die letzte Kennung der vorigen Seite, der leere String der Anfang. Bedient vom Index auf
	 * sternzeit mit COLLATE "C", kostet bei jeder Seite gleich viel; OFFSET ließe Postgres bei jeder Seite alle
	 * vorherigen Zeilen abzählen.
	 */
	List<AbasSternzeit> findBySternzeitGreaterThanOrderBySternzeitAsc(String afterSternzeit, Pageable pageable);

	/**
	 * Die geschätzte Zeilenzahl aus dem Postgres-Katalog, hinterlegt vom letzten ANALYZE; -1, wenn nie analysiert.
	 * count(*) liest in Postgres jede Zeile, bei 10^9 sind das zig Sekunden; eine Zeile aus pg_class ist konstant
	 * billig. Ob die Schätzung reicht, entscheidet der Service (unter einer halben Million wird genau gezählt).
	 */
	@Query(value = "SELECT reltuples::bigint FROM pg_class WHERE oid = 'abas_sternzeit'::regclass", nativeQuery = true)
	long estimateRowCount();

	/** Geholt, aber ohne Objekttyp: eine immer kleine Menge, bedient vom Teilindex aus docs/backend/abas-indizes.sql */
	long countByObjectTypeIdIsNull();

	/** Der DELETE-Weg der Queue: Eine in ABAS gelöschte Kennung hat hier nichts mehr verloren. 0 Zeilen ist ein Leerlauf. */
	@Modifying
	@Transactional
	long deleteBySternzeit(String sternzeit);

}
