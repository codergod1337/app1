package codergod1337.app1.system.access.repository;

import codergod1337.app1.system.access.model.AccessRole;
import org.springframework.data.jpa.repository.JpaRepository;

/** Der Key ist die ID: findById(key) sucht nach dem Key. */
public interface AccessRoleRepository extends JpaRepository<AccessRole, String> {

}
