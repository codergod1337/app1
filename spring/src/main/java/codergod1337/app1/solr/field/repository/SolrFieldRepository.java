package codergod1337.app1.solr.field.repository;

import codergod1337.app1.solr.field.model.SolrField;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolrFieldRepository extends JpaRepository<SolrField, Long> {

	boolean existsByCoreKeyAndName(String coreKey, String name);

	List<SolrField> findByCoreKey(String coreKey);

}
