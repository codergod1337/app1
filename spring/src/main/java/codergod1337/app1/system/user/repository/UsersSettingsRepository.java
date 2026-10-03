package codergod1337.app1.system.user.repository;

import codergod1337.app1.system.user.model.UsersSettings;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsersSettingsRepository extends JpaRepository<UsersSettings, Long> {

	List<UsersSettings> findByUsersGuid(UUID usersGuid);

	Optional<UsersSettings> findByUsersGuidAndKey(UUID usersGuid, String key);

	void deleteByUsersGuid(UUID usersGuid);

	void deleteByUsersGuidAndKey(UUID usersGuid, String key);

}
