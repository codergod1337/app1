package codergod1337.app1.solr.field.repository;

import codergod1337.app1.solr.field.model.SolrField;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolrFieldRepository extends JpaRepository<SolrField, Long> {

	/** Der Name ist eindeutig über alle Kerne */
	boolean existsByName(String name);

	Optional<SolrField> findByName(String name);

	/** Nur die Felder dieses Kerns, ohne die Felder, die in jedem Kern liegen (coreKey null) */
	List<SolrField> findByCoreKey(String coreKey);

}
