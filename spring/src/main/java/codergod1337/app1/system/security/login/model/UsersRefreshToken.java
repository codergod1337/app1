package codergod1337.app1.system.security.login.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Ein Glied einer Sitzungskette, der widerrufbare Teil einer Sitzung. Der Token selbst ist ein Zufallswert im
 * httpOnly-Cookie, hier liegt nur sein SHA-256. Jede Erneuerung stempelt usedAt und legt einen Nachfolger mit derselben
 * familyId an. Verbrauchte Zeilen bleiben stehen: Taucht ein verbrauchter Token wieder auf, ist das Diebstahl, und alle
 * Sitzungen des Users fliegen.
 */
@Entity
@Table(name = "users_refresh_token")
public class UsersRefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** SHA-256 des Tokens, der Klartext lebt nur im Cookie */
	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	/** Verweis auf {@code Users.guid} */
	@Column(name = "users_guid", nullable = false)
	private UUID usersGuid;

	/** Über welchen Login die Kette entstand. Ein Refresh stellt immer ein Token derselben area aus. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Area area;

	/** Die Kette, also ein Gerät. Bleibt über alle Erneuerungen gleich. */
	@Column(nullable = false)
	private UUID familyId;

	/** Sitzungsplatz 1 bis 3. Bleibt der Kette, solange es sie gibt, niemand rutscht nach. */
	@Column(nullable = false)
	private int sessionNumber;

	/** {@code null}: der aktuell gültige Token seiner Kette */
	private OffsetDateTime usedAt;

	/** 14 Tage nach dem Anlegen */
	@Column(nullable = false)
	private OffsetDateTime expiresAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/** Wann die Kette entstand. Nachfolger tragen den Wert weiter: „angemeldet seit“. */
	@Column(nullable = false)
	private OffsetDateTime familyCreatedAt;

	/** Wie oft die Kette schon erneuert wurde */
	@Column(nullable = false)
	private int refreshCount;

	/** Für JPA. */
	protected UsersRefreshToken() {
	}

	public UsersRefreshToken(String tokenHash, UUID usersGuid, Area area, UUID familyId, int sessionNumber,
			OffsetDateTime expiresAt, OffsetDateTime familyCreatedAt, int refreshCount) {
		this.tokenHash = tokenHash;
		this.usersGuid = usersGuid;
		this.area = area;
		this.familyId = familyId;
		this.sessionNumber = sessionNumber;
		this.expiresAt = expiresAt;
		this.familyCreatedAt = familyCreatedAt;
		this.refreshCount = refreshCount;
	}

	public Long getId() {
		return id;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public UUID getUsersGuid() {
		return usersGuid;
	}

	public Area getArea() {
		return area;
	}

	public UUID getFamilyId() {
		return familyId;
	}

	public int getSessionNumber() {
		return sessionNumber;
	}

	/** Setzt das Repository in einer SQL-Anweisung, deshalb kein Setter. */
	public OffsetDateTime getUsedAt() {
		return usedAt;
	}

	public OffsetDateTime getExpiresAt() {
		return expiresAt;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getFamilyCreatedAt() {
		return familyCreatedAt;
	}

	public int getRefreshCount() {
		return refreshCount;
	}

}
