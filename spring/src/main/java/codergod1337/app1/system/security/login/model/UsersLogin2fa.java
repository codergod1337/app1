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
 * Die Wartezeile zwischen „Passwort stimmt“ und „angemeldet“. Zwei Tokens, zwei Wege: Der Abholschein (pollToken)
 * bleibt im Fenster mit dem Passwort und ist der einzige Weg an die Cookies. Der Bestätigungs-Token geht über den
 * Aktivierungslink (später per Mail) und kann nur bestätigen. Erst beides zusammen ergibt eine Sitzung.
 * Gespeichert werden nur die SHA-256-Hashes. Lebenslauf: anlegen bei start, confirmedAt bei confirm, löschen bei claim.
 */
@Entity
@Table(name = "users_login_2fa")
public class UsersLogin2fa {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** SHA-256 des Abholscheins */
	@Column(nullable = false, unique = true, length = 64)
	private String pollTokenHash;

	/** SHA-256 des Bestätigungs-Tokens */
	@Column(nullable = false, unique = true, length = 64)
	private String confirmationTokenHash;

	/** Verweis auf {@code Users.guid}: wem die Anmeldung gehören wird */
	@Column(name = "users_guid", nullable = false)
	private UUID usersGuid;

	/** Über welchen Login die Anmeldung läuft. claim stellt nur ein Token dieser area aus. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Area area;

	/** {@code null}: noch nicht bestätigt, das Fenster wartet weiter */
	private OffsetDateTime confirmedAt;

	/** 10 Minuten nach dem Anlegen. Danach beginnt alles von vorn. */
	@Column(nullable = false)
	private OffsetDateTime expiresAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/** Für JPA. */
	protected UsersLogin2fa() {
	}

	public UsersLogin2fa(String pollTokenHash, String confirmationTokenHash, UUID usersGuid, Area area,
			OffsetDateTime expiresAt) {
		this.pollTokenHash = pollTokenHash;
		this.confirmationTokenHash = confirmationTokenHash;
		this.usersGuid = usersGuid;
		this.area = area;
		this.expiresAt = expiresAt;
	}

	public Long getId() {
		return id;
	}

	public String getPollTokenHash() {
		return pollTokenHash;
	}

	public String getConfirmationTokenHash() {
		return confirmationTokenHash;
	}

	public UUID getUsersGuid() {
		return usersGuid;
	}

	public Area getArea() {
		return area;
	}

	/** Setzt das Repository in einer SQL-Anweisung, deshalb kein Setter. */
	public OffsetDateTime getConfirmedAt() {
		return confirmedAt;
	}

	public OffsetDateTime getExpiresAt() {
		return expiresAt;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

}
