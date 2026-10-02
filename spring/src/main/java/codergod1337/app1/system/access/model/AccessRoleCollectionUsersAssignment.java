package codergod1337.app1.system.access.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Die ARC (Position) eines Users. Jeder User hat höchstens eine: Die guid ist der PK, eine zweite Zeile ist damit
 * unmöglich. Einzelne AR direkt bleiben daneben möglich ({@link AccessRoleUsersAssignment}).
 * Keine JPA-Beziehung: Beim Löschen von User oder ARC räumen deren Services die Zeile weg.
 */
@Entity
@Table(name = "access_role_collection_users_assignment")
public class AccessRoleCollectionUsersAssignment {

	/** PK und zugleich Verweis auf {@code Users.guid}, also höchstens eine ARC pro User. */
	@Id
	@Column(name = "users_guid", nullable = false, updatable = false)
	private UUID usersGuid;

	/** Verweis auf {@code AccessRoleCollection.key}, z. B. ARC_LAGER */
	@Column(name = "access_role_collection_key", nullable = false, length = 200)
	private String accessRoleCollectionKey;

	/** Seit wann der User diese ARC hat. Wechselt die ARC, beginnt die Zeit neu. */
	@Column(nullable = false)
	private OffsetDateTime assignedAt;

	/** Für JPA und Jackson. */
	protected AccessRoleCollectionUsersAssignment() {
	}

	/** Neue Zuordnung ab jetzt. */
	public AccessRoleCollectionUsersAssignment(UUID usersGuid, String accessRoleCollectionKey) {
		this(usersGuid, accessRoleCollectionKey, OffsetDateTime.now());
	}

	/** Umzug auf eine neue guid: assignedAt bleibt. */
	public AccessRoleCollectionUsersAssignment(UUID usersGuid, String accessRoleCollectionKey,
			OffsetDateTime assignedAt) {
		this.usersGuid = usersGuid;
		this.accessRoleCollectionKey = accessRoleCollectionKey;
		this.assignedAt = assignedAt;
	}

	public UUID getUsersGuid() {
		return usersGuid;
	}

	public String getAccessRoleCollectionKey() {
		return accessRoleCollectionKey;
	}

	/** Andere ARC für denselben User: die Zeit seit der Zuordnung beginnt neu. */
	public void setAccessRoleCollectionKey(String accessRoleCollectionKey) {
		this.accessRoleCollectionKey = accessRoleCollectionKey;
		this.assignedAt = OffsetDateTime.now();
	}

	public OffsetDateTime getAssignedAt() {
		return assignedAt;
	}

}
