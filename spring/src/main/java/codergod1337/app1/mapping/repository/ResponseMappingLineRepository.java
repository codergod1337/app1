package codergod1337.app1.mapping.repository;

import codergod1337.app1.mapping.model.ResponseMappingLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResponseMappingLineRepository extends JpaRepository<ResponseMappingLine, Long> {

	List<ResponseMappingLine> findByResponseMappingIdOrderByPositionAsc(Long responseMappingId);

	/** Alle Einträge aller Mappings in einer Abfrage, für die Übersicht */
	List<ResponseMappingLine> findAllByOrderByResponseMappingIdAscPositionAsc();

	long deleteByResponseMappingId(Long responseMappingId);

}
