package codergod1337.app1.abas.repository;

import codergod1337.app1.abas.model.AbasObjectType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Die Nachschlagetabelle der ABAS-Objekttypen, ein paar Dutzend Zeilen, der Service hält sie als Kopie im Speicher. */
public interface AbasObjectTypeRepository extends JpaRepository<AbasObjectType, Short> {

	Optional<AbasObjectType> findByObjectType(String objectType);

}
