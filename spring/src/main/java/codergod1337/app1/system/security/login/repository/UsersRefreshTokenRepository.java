package codergod1337.app1.system.security.login.repository;

import codergod1337.app1.system.security.login.model.UsersRefreshToken;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsersRefreshTokenRepository extends JpaRepository<UsersRefreshToken, Long> {

	Optional<UsersRefreshToken> findByTokenHash(String tokenHash);

	/**
	 * Verbraucht, nur wenn unverbraucht und gültig. Eine SQL-Anweisung, sonst könnten zwei gleichzeitige Anfragen beide
	 * „war noch gültig“ lesen und der Diebstahl fiele nie auf. 1 = soeben verbraucht, 0 bei einem gültigen Token heißt:
	 * war schon verbraucht.
	 */
	@Modifying
	@Query("""
			update UsersRefreshToken u
			   set u.usedAt = :now
			 where u.tokenHash = :tokenHash
			   and u.usedAt is null
			   and u.expiresAt > :now
			""")
	int markUsedIfActive(@Param("tokenHash") String tokenHash, @Param("now") OffsetDateTime now);

	/** Die lebenden Ketten eines Users (unverbraucht, nicht abgelaufen), daraus die belegten Plätze */
	List<UsersRefreshToken> findByUsersGuidAndUsedAtIsNullAndExpiresAtAfter(UUID usersGuid, OffsetDateTime now);

	void deleteByFamilyId(UUID familyId);

	void deleteByUsersGuid(UUID usersGuid);

	void deleteByUsersGuidAndExpiresAtBefore(UUID usersGuid, OffsetDateTime now);

}
