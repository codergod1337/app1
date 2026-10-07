package codergod1337.app1.solr.grant.repository;

import codergod1337.app1.solr.grant.model.SolrHookGrantAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolrHookGrantAccessRepository extends JpaRepository<SolrHookGrantAccess, Long> {

	List<SolrHookGrantAccess> findByGranteeUsersGuid(UUID granteeUsersGuid);

	List<SolrHookGrantAccess> findByStatus(String status);

}
