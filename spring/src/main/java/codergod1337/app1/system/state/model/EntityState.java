package codergod1337.app1.system.state.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;

/**
 * Ein Status für alle Entities, die einen brauchen, z. B. die Dokumente des DMS (ACTIVE, ARCHIVED, PENDING_ADMIN,
 * DELETED). Eine Entity statt eines Enums: Der Anzeigename ist mehrsprachig und vom Admin pflegbar, die Reihenfolge
 * steht fest, und neue Status kommen ohne Code dazu.
 *
 * scope sagt, wo ein Status gilt: Eine Maske bietet nur die Status ihres Bereichs an, z. B. DMS. Ohne scope gilt er
 * überall. So lassen sich Untergruppen bilden, ohne dass jede Entity ihre eigene Statustabelle braucht.
 *
 * Das Format des Keys und des scope prüft {@code HelperInputs.isValidKey}, nicht diese Klasse.
 */
@Entity
@Table(name = "entity_state")
public class EntityState {

	/** Normale Key-Regel, z. B. ACTIVE. Von Hand vergeben, ändert sich nie. In den Dokumenten steht dieser key. */
	@Id
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, updatable = false, length = 200)
	private String key;

	/** Mehrsprachiges JSON, z. B. {"de":"aktiv","en":"active","bg":"активен"}. Pflicht. */
	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String displayName;

	/** Wo der Status gilt, normale Key-Regel, z. B. DMS. null: überall. */
	@Size(max = 200)
	@Column(length = 200)
	private String scope;

	/** Reihenfolge in jeder Anzeige, nicht eindeutig. Sortiert wird nach Position und dann nach key. */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	/** Für JPA und Jackson. */
	protected EntityState() {
	}

	/** Admin und Import: jedes Feld. */
	public EntityState(String key, String displayName, String scope, int listingPosition) {
		this.key = key;
		this.displayName = displayName;
		this.scope = scope;
		this.listingPosition = listingPosition;
	}

	public String getKey() {
		return key;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	public int getListingPosition() {
		return listingPosition;
	}

	public void setListingPosition(int listingPosition) {
		this.listingPosition = listingPosition;
	}

}
