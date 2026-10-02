package codergod1337.app1.system.security.login.repository;

import codergod1337.app1.system.security.login.model.Area;
import codergod1337.app1.system.security.login.model.UsersLogin2fa;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Bestätigen und Einlösen laufen als eine SQL-Anweisung und nicht als Lesen-Prüfen-Schreiben in Java: Zwei gleichzeitige
 * Anfragen mit demselben Token dürfen nicht beide gewinnen. In Java läge zwischen Prüfen und Schreiben eine Lücke.
 */
public interface UsersLogin2faRepository extends JpaRepository<UsersLogin2fa, Long> {

	Optional<UsersLogin2fa> findByPollTokenHash(String pollTokenHash);

	/** Bestätigt, nur wenn noch nicht bestätigt und nicht abgelaufen. 1 = soeben bestätigt */
	@Modifying
	@Query("""
			update UsersLogin2fa u
			   set u.confirmedAt = :now
			 where u.confirmationTokenHash = :confirmationTokenHash
			   and u.confirmedAt is null
			   and u.expiresAt > :now
			""")
	int markConfirmedIfActive(@Param("confirmationTokenHash") String confirmationTokenHash,
			@Param("now") OffsetDateTime now);

	/** Schon bestätigt und noch gültig? Damit gilt ein zweiter Klick auf den Link als Erfolg. */
	boolean existsByConfirmationTokenHashAndConfirmedAtIsNotNullAndExpiresAtAfter(String confirmationTokenHash,
			OffsetDateTime now);

	/** Löst den Abholschein ein: löscht, nur wenn bestätigt, gültig und von dieser area. 1 = gehört jetzt dir */
	@Modifying
	@Query("""
			delete from UsersLogin2fa u
			 where u.pollTokenHash = :pollTokenHash
			   and u.area = :area
			   and u.confirmedAt is not null
			   and u.expiresAt > :now
			""")
	int deleteIfConfirmedAndActive(@Param("pollTokenHash") String pollTokenHash, @Param("area") Area area,
			@Param("now") OffsetDateTime now);

	void deleteByExpiresAtBefore(OffsetDateTime now);

	void deleteByUsersGuid(UUID usersGuid);

}
