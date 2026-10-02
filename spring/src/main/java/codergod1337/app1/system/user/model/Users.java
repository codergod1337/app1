package codergod1337.app1.system.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.ColumnDefault;

/**
 * Öffentliche Daten eines Users.
 *
 * Info und Profiltext liegen in {@link UsersDetails}, Einstellungen in {@link UsersSettings}, Passwort und andere
 * nicht öffentliche Daten in {@link UsersCredentials}. Alle verweisen nur über {@code usersGuid} hierher: keine
 * JPA-Beziehung.
 */
@Entity
@Table(name = "users")
public class Users {

	/** Vergibt der Konstruktor, nicht Hibernate: Admin und Import dürfen eine eigene mitgeben. */
	@Id
	private UUID guid;

	// Formatprüfung (@Email) vorerst weggelassen
	@NotBlank
	@Size(max = 255)
	@Column(nullable = false, unique = true)
	private String email;

	@Size(max = 100)
	@Column(length = 100, unique = true)
	private String username;

	@Size(max = 100)
	@Column(length = 100)
	private String vorname;

	@Size(max = 100)
	@Column(length = 100)
	private String nachname;

	/** Vergibt der Konstruktor, nicht Hibernate: Beim Import muss der alte Wert erhalten bleiben. */
	@Column(nullable = false)
	private OffsetDateTime createdAt;

	/** Kein Mensch, sondern ein Backend-Dienst. Ändert nur der Admin. */
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean serviceAccount;

	/**
	 * Freier String: ACTIVE, PENDING_ADMIN_ACTION, SUSPENDED, ARCHIVED, DEPRECATED oder DELETED. Das Frontend zeigt
	 * ihn in der gewählten Sprache an. Anmelden geht nur mit ACTIVE. Der Default füllt beim Anlegen der Spalte auch
	 * die vorhandenen User.
	 */
	@NotBlank
	@Size(max = 30)
	@Column(nullable = false, length = 30)
	@ColumnDefault("'ACTIVE'")
	private String status = "ACTIVE";

	/** Für JPA und Jackson. */
	protected Users() {
	}

	/** Registrierung: nur die E-Mail. guid und createdAt vergibt der Konstruktor, kein serviceAccount, Status ACTIVE. */
	public Users(String email) {
		this.guid = UUID.randomUUID();
		this.email = email;
		this.createdAt = OffsetDateTime.now();
	}

	/** Admin und Import: jedes Feld. Fehlen guid oder createdAt, werden sie vergeben, fehlt status, gilt ACTIVE. */
	public Users(UUID guid, String email, String username, String vorname, String nachname,
			OffsetDateTime createdAt, boolean serviceAccount, String status) {
		this.guid = guid != null ? guid : UUID.randomUUID();
		this.email = email;
		this.username = username;
		this.vorname = vorname;
		this.nachname = nachname;
		this.createdAt = createdAt != null ? createdAt : OffsetDateTime.now();
		this.serviceAccount = serviceAccount;
		this.status = status != null ? status : "ACTIVE";
	}

	public UUID getGuid() {
		return guid;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getVorname() {
		return vorname;
	}

	public void setVorname(String vorname) {
		this.vorname = vorname;
	}

	public String getNachname() {
		return nachname;
	}

	public void setNachname(String nachname) {
		this.nachname = nachname;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public boolean isServiceAccount() {
		return serviceAccount;
	}

	public void setServiceAccount(boolean serviceAccount) {
		this.serviceAccount = serviceAccount;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

}
