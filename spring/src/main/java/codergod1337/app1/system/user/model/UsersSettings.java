package codergod1337.app1.system.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * Beliebige Einstellungen eines Users als Schlüssel-Wert-Paare, eine Zeile pro Schlüssel.
 */
@Entity
@Table(name = "users_settings", uniqueConstraints = @UniqueConstraint(columnNames = { "users_guid", "key" }))
public class UsersSettings {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Verweis auf {@code Users.guid}. */
	@Column(name = "users_guid", nullable = false)
	private UUID usersGuid;

	@Column(nullable = false, length = 200)
	private String key;

	@Column(columnDefinition = "text")
	private String value;

	/** Für JPA und Jackson. */
	protected UsersSettings() {
	}

	/** Neue Einstellung, die id vergibt die Datenbank. */
	public UsersSettings(UUID usersGuid, String key, String value) {
		this.usersGuid = usersGuid;
		this.key = key;
		this.value = value;
	}

	public Long getId() {
		return id;
	}

	public UUID getUsersGuid() {
		return usersGuid;
	}

	public void setUsersGuid(UUID usersGuid) {
		this.usersGuid = usersGuid;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

}
