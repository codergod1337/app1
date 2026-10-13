package codergod1337.app1.solr.share.repository;

import codergod1337.app1.solr.share.model.UserGrant;
import codergod1337.app1.solr.share.model.UserGrantStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGrantRepository extends JpaRepository<UserGrant, Long> {

	List<UserGrant> findByCoreKeyAndDocumentIdOrderByCreatedAtDesc(String coreKey, String documentId);

	List<UserGrant> findByCoreKeyOrderByCreatedAtDesc(String coreKey);

	List<UserGrant> findByCoreKeyAndDocumentIdAndGranteeUsersGuidAndStatus(String coreKey, String documentId,
			UUID granteeUsersGuid, UserGrantStatus status);

}
