package codergod1337.app1.system.access.repository;

import codergod1337.app1.system.access.model.AccessRoleCollectionUsersAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessRoleCollectionUsersAssignmentRepository
		extends JpaRepository<AccessRoleCollectionUsersAssignment, UUID> {

	List<AccessRoleCollectionUsersAssignment> findByAccessRoleCollectionKey(String accessRoleCollectionKey);

	void deleteByAccessRoleCollectionKey(String accessRoleCollectionKey);

}
