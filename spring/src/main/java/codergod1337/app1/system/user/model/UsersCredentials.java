package codergod1337.app1.system.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Nicht öffentliche Daten eines Users: Passwort und alles, was nie nach außen gehen darf.
 * Getrennt von {@link Users}, damit nichts davon versehentlich mit den öffentlichen Daten herausgegeben wird.
 */
@Entity
@Table(name = "users_credentials")
public class UsersCredentials {

	/** PK und zugleich Verweis auf {@code Users.guid}, also höchstens eine Zeile pro User. */
	@Id
	@Column(name = "users_guid", nullable = false, updatable = false)
	private UUID usersGuid;

	/** Nur der Hash, nie das Passwort im Klartext. */
	@Column(nullable = false)
	private String passwordHash;

	/** Ab wann das Passwort nicht mehr gilt. {@code null} heißt: läuft nie ab. */
	private OffsetDateTime passwordExpiresAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private OffsetDateTime updatedAt;

	/** Für JPA. */
	protected UsersCredentials() {
	}

	/** Neue Zugangsdaten: Passwort läuft nicht ab. */
	public UsersCredentials(UUID usersGuid, String passwordHash) {
		this.usersGuid = usersGuid;
		this.passwordHash = passwordHash;
	}

	public UUID getUsersGuid() {
		return usersGuid;
	}

	public void setUsersGuid(UUID usersGuid) {
		this.usersGuid = usersGuid;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public OffsetDateTime getPasswordExpiresAt() {
		return passwordExpiresAt;
	}

	public void setPasswordExpiresAt(OffsetDateTime passwordExpiresAt) {
		this.passwordExpiresAt = passwordExpiresAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

}
