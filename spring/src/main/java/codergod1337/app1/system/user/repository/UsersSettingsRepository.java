package codergod1337.app1.system.user.repository;

import codergod1337.app1.system.user.model.UsersSettings;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsersSettingsRepository extends JpaRepository<UsersSettings, Long> {

	List<UsersSettings> findByUsersGuid(UUID usersGuid);

	void deleteByUsersGuid(UUID usersGuid);

}
