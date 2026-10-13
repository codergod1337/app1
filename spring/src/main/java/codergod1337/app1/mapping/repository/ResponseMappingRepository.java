package codergod1337.app1.mapping.repository;

import codergod1337.app1.mapping.model.ResponseMapping;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResponseMappingRepository extends JpaRepository<ResponseMapping, Long> {

	Optional<ResponseMapping> findByKey(String key);

	boolean existsByKey(String key);

	List<ResponseMapping> findAllByOrderByKeyAsc();

	/** Alle Mappings zu einem Typ, z. B. Firma und Ansprechpartner zu obj:data:19:0 */
	List<ResponseMapping> findByResponseObjectTypeOrderByKeyAsc(String responseObjectType);

}
