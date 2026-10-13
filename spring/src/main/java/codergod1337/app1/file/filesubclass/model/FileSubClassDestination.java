package codergod1337.app1.file.filesubclass.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;

/**
 * Ein Anzeigeort für Dateiarten (User 2026-10-13, „Destinations“): eine Stelle in der Oberfläche, an der Dateiarten als
 * Abschnitt stehen und zum Hochladen angeboten werden, z. B. der Dokumente-Tab der Artikelmappe, die Kundenmappe, die
 * Lieferantenmappe. Der Admin legt sie selbst an; eine Dateiart und ein Infosystem-Aufruf verweisen über
 * {@code displayContextKeys} lose auf diese Keys, und der Code eines Ortes nennt seinen Key selbst. Eine Entity statt
 * einer festen Liste: Der Anzeigename ist mehrsprachig, die Reihenfolge steht fest, neue Orte kommen ohne Code dazu.
 *
 * Das Format des Keys prüft {@code HelperInputs.isValidKey}, nicht diese Klasse.
 */
@Entity
@Table(name = "file_sub_class_destination")
public class FileSubClassDestination {

	/** Normale Key-Regel, z. B. ARTIKELMAPPE_DOKUMENTE. Von Hand vergeben, ändert sich nie: Dateiarten und Aufrufe tragen ihn. */
	@Id
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, updatable = false, length = 200)
	private String key;

	/** Mehrsprachiges JSON, z. B. {"de":"Artikelmappe, Dokumente","en":"Article file, documents","bg":"…"}. Pflicht. */
	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String displayName;

	/** Mehrsprachiges JSON: wo das ist und was dort gezeigt wird. Optional. */
	@Column(columnDefinition = "text")
	private String description;

	/** Reihenfolge in jeder Anzeige, nicht eindeutig. Sortiert wird nach Position und dann nach key. */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	/** Für JPA und Jackson. */
	protected FileSubClassDestination() {
	}

	/** Admin und Import: jedes Feld. */
	public FileSubClassDestination(String key, String displayName, String description, int listingPosition) {
		this.key = key;
		this.displayName = displayName;
		this.description = description;
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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public int getListingPosition() {
		return listingPosition;
	}

	public void setListingPosition(int listingPosition) {
		this.listingPosition = listingPosition;
	}

}
