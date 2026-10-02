package codergod1337.app1.system.access.repository;

import codergod1337.app1.system.access.model.AccessRoleUsersAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessRoleUsersAssignmentRepository extends JpaRepository<AccessRoleUsersAssignment, Long> {

	List<AccessRoleUsersAssignment> findByUsersGuid(UUID usersGuid);

	void deleteByUsersGuid(UUID usersGuid);

	void deleteByAccessRoleKey(String accessRoleKey);

}
