package codergod1337.app1.system.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Öffentliche Daten eines Users.
 *
 * Einstellungen liegen in {@link UsersSettings}, Passwort und andere nicht öffentliche Daten in
 * {@link UsersCredentials}. Beide verweisen nur über {@code usersGuid} hierher: keine JPA-Beziehung.
 */
@Entity
@Table(name = "users")
public class Users {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID guid;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(length = 100, unique = true)
	private String username;

	@Column(length = 100)
	private String vorname;

	@Column(length = 100)
	private String nachname;

	@Column(columnDefinition = "text")
	private String info;

	@Column(columnDefinition = "text")
	private String profilText;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private OffsetDateTime createdAt;

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

	public String getInfo() {
		return info;
	}

	public void setInfo(String info) {
		this.info = info;
	}

	public String getProfilText() {
		return profilText;
	}

	public void setProfilText(String profilText) {
		this.profilText = profilText;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

}
