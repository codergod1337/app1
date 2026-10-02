package codergod1337.app1.system.access.repository;

import codergod1337.app1.system.access.model.AccessRoleCollection;
import org.springframework.data.jpa.repository.JpaRepository;

/** Der Key ist die ID: findById(key) sucht nach dem Key. */
public interface AccessRoleCollectionRepository extends JpaRepository<AccessRoleCollection, String> {

}
