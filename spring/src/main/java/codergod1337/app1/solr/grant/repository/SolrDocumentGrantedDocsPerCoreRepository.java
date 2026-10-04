package codergod1337.app1.solr.grant.repository;

import codergod1337.app1.solr.grant.model.SolrDocumentGrantedDocsPerCore;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolrDocumentGrantedDocsPerCoreRepository extends JpaRepository<SolrDocumentGrantedDocsPerCore, Long> {

	List<SolrDocumentGrantedDocsPerCore> findByGranteeUsersGuid(UUID granteeUsersGuid);

	List<SolrDocumentGrantedDocsPerCore> findByStatus(String status);

}
