package codergod1337.app1.abas.repository;

import codergod1337.app1.abas.model.AbasIgnoredSternzeit;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Die Sperrliste. Die eine Abfrage ist der Prüfgriff vor jedem Einreihen, sie trifft den eindeutigen Index. */
public interface AbasIgnoredSternzeitRepository extends JpaRepository<AbasIgnoredSternzeit, Long> {

	boolean existsBySternzeit(String sternzeit);

	/** Derselbe Prüfgriff für ein ganzes Paket eines Imports: eine Abfrage statt tausend, über denselben Index */
	@Query("SELECT i.sternzeit FROM AbasIgnoredSternzeit i WHERE i.sternzeit IN :sternzeiten")
	List<String> findSternzeitenAmong(@Param("sternzeiten") Collection<String> sternzeiten);

}
