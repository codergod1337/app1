package codergod1337.app1.dms.repository;

import codergod1337.app1.dms.model.DmsSolrDocumentRevision;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DmsSolrDocumentRevisionRepository extends JpaRepository<DmsSolrDocumentRevision, Long> {

	List<DmsSolrDocumentRevision> findByDocumentIdOrderByRevisionNoDesc(String documentId);

	Optional<DmsSolrDocumentRevision> findByDocumentIdAndRevisionNo(String documentId, long revisionNo);

}
