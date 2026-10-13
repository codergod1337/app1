package codergod1337.app1.system.user.repository;

import codergod1337.app1.system.user.model.UsersDebuffs;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsersDebuffsRepository extends JpaRepository<UsersDebuffs, Long> {

	List<UsersDebuffs> findByUsersGuid(UUID usersGuid);

	boolean existsByUsersGuid(UUID usersGuid);

	void deleteByUsersGuid(UUID usersGuid);

}
