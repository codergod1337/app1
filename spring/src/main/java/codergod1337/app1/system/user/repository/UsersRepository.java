package codergod1337.app1.system.user.repository;

import codergod1337.app1.system.user.model.Users;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsersRepository extends JpaRepository<Users, UUID> {

	Optional<Users> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByUsername(String username);

}
