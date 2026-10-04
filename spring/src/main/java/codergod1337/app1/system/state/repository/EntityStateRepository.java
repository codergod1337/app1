package codergod1337.app1.system.state.repository;

import codergod1337.app1.system.state.model.EntityState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityStateRepository extends JpaRepository<EntityState, String> {

}
