package codergod1337.app1.solr.hook.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;

/**
 * Eine Gruppe von Hooks, nur für die Anzeige: Kern-Tab, Hook-Auswahl und später die Upload-Maske zeigen die Hooks
 * gruppenweise, z. B. Artikel, Partner, Vorgang. Ein Hook zeigt mit hookGroupKey auf seine Gruppe, ohne Gruppe geht
 * auch. Die Gruppe trägt keine Rechte und hat keine Bedeutung in Solr.
 *
 * Eigene Entity statt eines Textes am Hook: Eine Gruppe wird einmal umbenannt, nicht an jedem Hook, ihre Reihenfolge
 * steht fest, und nichts hängt an gleicher Schreibweise.
 *
 * Das Format des Keys prüft {@code HelperInputs.isValidKey}, nicht diese Klasse.
 */
@Entity
@Table(name = "solr_hook_group")
public class SolrHookGroup {

	/** Normale Key-Regel, z. B. ARTIKEL. Von Hand vergeben, ändert sich nie. */
	@Id
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, updatable = false, length = 200)
	private String key;

	/** Mehrsprachiges JSON, z. B. {"de":"Artikel","en":"Articles"}. Pflicht. */
	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String displayName;

	/** Reihenfolge der Gruppen in jeder Anzeige, nicht eindeutig. Sortiert wird nach Position und dann nach key. */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	/** Für JPA und Jackson. */
	protected SolrHookGroup() {
	}

	/** Admin und Import: jedes Feld. */
	public SolrHookGroup(String key, String displayName, int listingPosition) {
		this.key = key;
		this.displayName = displayName;
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

	public int getListingPosition() {
		return listingPosition;
	}

	public void setListingPosition(int listingPosition) {
		this.listingPosition = listingPosition;
	}

}
