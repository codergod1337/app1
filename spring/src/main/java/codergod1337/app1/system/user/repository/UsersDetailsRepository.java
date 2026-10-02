package codergod1337.app1.system.user.repository;

import codergod1337.app1.system.user.model.UsersDetails;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsersDetailsRepository extends JpaRepository<UsersDetails, UUID> {

}
