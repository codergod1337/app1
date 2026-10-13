package codergod1337.app1.system.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Einschränkungen eines Users, eine Zeile je User, Typ und Solr-Feld. Ein Debuff schreibt nie in ein Dokument, er
 * filtert nur: Bei RESOLVE bekommt jede Anfrage des Users den Filter solrFieldName:(wert1 OR wert2 …), egal was er fragt.
 */
@Entity
@Table(name = "users_debuffs", uniqueConstraints = @UniqueConstraint(columnNames = { "users_guid", "type", "solr_field_name" }))
public class UsersDebuffs {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Verweis auf {@code Users.guid}. */
	@Column(name = "users_guid", nullable = false)
	private UUID usersGuid;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private UsersDebuffType type;

	/** Name eines Hooks oder Feldes in Solr, über alle Kerne eindeutig. */
	@Column(name = "solr_field_name", nullable = false, length = 100)
	private String solrFieldName;

	/** Die Konstanten als Text, ein Eintrag bei einwertigen Feldern. Solr liest den Text je Feldtyp. */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> values;

	/** Für JPA und Jackson. */
	protected UsersDebuffs() {
	}

	/** Neuer Debuff, die id vergibt die Datenbank. */
	public UsersDebuffs(UUID usersGuid, UsersDebuffType type, String solrFieldName, List<String> values) {
		this.usersGuid = usersGuid;
		this.type = type;
		this.solrFieldName = solrFieldName;
		this.values = values != null ? List.copyOf(values) : List.of();
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

	public UsersDebuffType getType() {
		return type;
	}

	public void setType(UsersDebuffType type) {
		this.type = type;
	}

	public String getSolrFieldName() {
		return solrFieldName;
	}

	public void setSolrFieldName(String solrFieldName) {
		this.solrFieldName = solrFieldName;
	}

	public List<String> getValues() {
		return values != null ? values : List.of();
	}

	public void setValues(List<String> values) {
		this.values = values != null ? List.copyOf(values) : List.of();
	}

}
