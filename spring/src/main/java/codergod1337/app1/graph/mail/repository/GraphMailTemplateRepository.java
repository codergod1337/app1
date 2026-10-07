package codergod1337.app1.graph.mail.repository;

import codergod1337.app1.graph.mail.model.GraphMailTemplate;
import codergod1337.app1.graph.mail.model.GraphMailTemplateType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GraphMailTemplateRepository extends JpaRepository<GraphMailTemplate, String> {

	Optional<GraphMailTemplate> findByType(GraphMailTemplateType type);

	boolean existsByType(GraphMailTemplateType type);

}
