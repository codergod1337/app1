package codergod1337.app1.solr.grant.repository;

import codergod1337.app1.solr.grant.model.SolrAdvancedFieldGrantAccessPerCore;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolrAdvancedFieldGrantAccessPerCoreRepository
		extends JpaRepository<SolrAdvancedFieldGrantAccessPerCore, Long> {

	List<SolrAdvancedFieldGrantAccessPerCore> findByGranteeUsersGuid(UUID granteeUsersGuid);

	List<SolrAdvancedFieldGrantAccessPerCore> findByStatus(String status);

}
