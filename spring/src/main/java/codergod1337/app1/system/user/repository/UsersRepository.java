package codergod1337.app1.system.user.repository;

import codergod1337.app1.system.user.model.Users;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsersRepository extends JpaRepository<Users, UUID> {

	Optional<Users> findByEmail(String email);

	/** Die Vervollständigung: E-Mail-Schnipsel, egal wie geschrieben, höchstens zehn, nach E-Mail sortiert */
	List<Users> findTop10ByEmailContainingIgnoreCaseOrderByEmailAsc(String emailPart);

	boolean existsByEmail(String email);

	boolean existsByUsername(String username);

}
