package codergod1337.app1.solr.field.repository;

import codergod1337.app1.solr.field.model.SolrField;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolrFieldRepository extends JpaRepository<SolrField, Long> {

	/** Gibt es den Namen in irgendeinem Kern, als Feld oder als Hook? Ein neues Feld darf keinen vergebenen Namen tragen. */
	boolean existsByName(String name);

	Optional<SolrField> findByCoreKeyAndName(String coreKey, String name);

	/** Nur die Felder dieses Kerns, ohne die Felder, die in jedem Kern liegen (coreKey null) */
	List<SolrField> findByCoreKey(String coreKey);

	/** Die Zeilen eines Hooks, eine je Kern */
	List<SolrField> findByHookKey(String hookKey);

}
