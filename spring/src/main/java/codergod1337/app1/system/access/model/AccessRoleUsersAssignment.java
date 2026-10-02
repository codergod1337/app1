package codergod1337.app1.system.access.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Eine AR, die einem User einzeln zugewiesen ist. Eine Zeile pro Paar: ein User trägt beliebig viele AR, eine AR
 * beliebig viele User. AR aus einer ARC stehen hier nie, die kommen erst beim Ausstellen des Tokens dazu.
 * Keine JPA-Beziehung: Beim Löschen von User oder AR räumen deren Services die Zeilen weg.
 */
@Entity
@Table(name = "access_role_users_assignment",
		uniqueConstraints = @UniqueConstraint(columnNames = { "users_guid", "access_role_key" }))
public class AccessRoleUsersAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Verweis auf {@code Users.guid}. Ändert sich nur, wenn der Admin die guid des Users ändert. */
	@Column(name = "users_guid", nullable = false)
	private UUID usersGuid;

	/** Verweis auf {@code AccessRole.key}, z. B. ADMIN */
	@Column(name = "access_role_key", nullable = false, length = 200)
	private String accessRoleKey;

	/** Seit wann der User die AR hat. Wer sie vergeben hat, steht absichtlich nicht dabei. */
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private OffsetDateTime assignedAt;

	/** Für JPA und Jackson. */
	protected AccessRoleUsersAssignment() {
	}

	public AccessRoleUsersAssignment(UUID usersGuid, String accessRoleKey) {
		this.usersGuid = usersGuid;
		this.accessRoleKey = accessRoleKey;
	}

	public Long getId() {
		return id;
	}

	public UUID getUsersGuid() {
		return usersGuid;
	}

	/** Nur für den Umzug, wenn der Admin die guid des Users ändert. */
	public void setUsersGuid(UUID usersGuid) {
		this.usersGuid = usersGuid;
	}

	public String getAccessRoleKey() {
		return accessRoleKey;
	}

	public OffsetDateTime getAssignedAt() {
		return assignedAt;
	}

}
