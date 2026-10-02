package codergod1337.app1.system.user.repository;

import codergod1337.app1.system.user.model.UsersCredentials;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsersCredentialsRepository extends JpaRepository<UsersCredentials, UUID> {

}
