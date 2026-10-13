package codergod1337.app1.solr.share.repository;

import codergod1337.app1.solr.share.model.ShareToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareTokenRepository extends JpaRepository<ShareToken, Long> {

	Optional<ShareToken> findByTokenHash(String tokenHash);

	List<ShareToken> findByCoreKeyAndDocumentIdOrderByCreatedAtDesc(String coreKey, String documentId);

	List<ShareToken> findByCoreKeyOrderByCreatedAtDesc(String coreKey);

}
