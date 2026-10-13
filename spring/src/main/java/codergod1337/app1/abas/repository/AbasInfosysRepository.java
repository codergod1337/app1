package codergod1337.app1.abas.repository;

import codergod1337.app1.abas.model.AbasInfosys;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AbasInfosysRepository extends JpaRepository<AbasInfosys, String> {

	List<AbasInfosys> findAllByOrderByListingPositionAscKeyAsc();

}
