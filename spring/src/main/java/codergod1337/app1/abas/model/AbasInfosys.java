package codergod1337.app1.abas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Ein Infosystem-Aufruf bei ABAS, einmal hinterlegt (User 2026-10-10): die Endung der URL (Gruppe und Name) und der
 * Request-Body, wie er aussehen muss, mit Platzhaltern {@code {{name}}} für das, was je Aufruf wechselt, meist genau
 * eine Nummer. Jede Infosystem-Anfrage geht über so einen Aufruf (User 2026-10-11): Ein Subfetch-Eintrag im Mapping
 * zeigt mit seinem Key hierher und füllt nur die Platzhalter, die Artikelmappe lädt damit nach, die Belegnummer schlägt
 * damit nach. Der Key folgt der normalen Key-Regel ({@code HelperInputs.isValidKey}) und ändert sich nie, denn
 * Mapping-Einträge verweisen darüber hierher. Gehört zu den Stammdaten ({@code MasterDataSection.ABAS_INFOSYS}).
 */
@Entity
@Table(name = "abas_infosysteme")
public class AbasInfosys {

	/** Normale Key-Regel, z. B. KUNDEN_ARTIKELNUMMERN. Von Hand vergeben, ändert sich nie. */
	@Id
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, updatable = false, length = 200)
	private String key;

	/** Mehrsprachiges JSON, z. B. {"de":"Kunden-Artikelnummern"}. Pflicht. */
	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String displayName;

	/** Die Gruppe in der URL, z. B. st */
	@NotBlank
	@Size(max = 100)
	@Column(nullable = false, length = 100)
	private String infosysGroup;

	/** Der Name in der URL, z. B. CUSTOMERPRODUCT */
	@NotBlank
	@Size(max = 100)
	@Column(nullable = false, length = 100)
	private String infosysName;

	/**
	 * Der Request-Body als Vorlage: {@code {"actions":[{"_type":"SetFieldValue","fieldName":"kartikel","value":"{{kartikel}}"},
	 * {"_type":"SetFieldValue","fieldName":"bstart"}]}}. Jeder Platzhalter {@code {{name}}} ist eine Eingabe des Aufrufs,
	 * alles andere steht fest.
	 */
	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String requestBodyTemplate;

	/** Reihenfolge in der Liste, nicht eindeutig. Sortiert wird nach Position und dann nach key. */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	/**
	 * Die Anzeigeorte (User 2026-10-11, wie bei der Dateiart): wo dieser Aufruf von selbst erscheint, je Ort ein Key
	 * nach der normalen Key-Regel. {@code ARTIKELMAPPE} heißt: ein Tab der Artikelmappe, die Platzhalter heißen wie die
	 * Felder des Artikels, aus denen sie gefüllt werden. Leer heißt nirgends, der Aufruf dient nur Subfetches und dem Tab
	 * „Ausführen“.
	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> displayContextKeys;

	/** Für JPA und Jackson. */
	protected AbasInfosys() {
	}

	/** Admin und Import: jedes Feld. */
	public AbasInfosys(String key, String displayName, String infosysGroup, String infosysName,
			String requestBodyTemplate, int listingPosition, List<String> displayContextKeys) {
		this.key = key;
		this.displayName = displayName;
		this.infosysGroup = infosysGroup;
		this.infosysName = infosysName;
		this.requestBodyTemplate = requestBodyTemplate;
		this.listingPosition = listingPosition;
		this.displayContextKeys = displayContextKeys;
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

	public String getInfosysGroup() {
		return infosysGroup;
	}

	public void setInfosysGroup(String infosysGroup) {
		this.infosysGroup = infosysGroup;
	}

	public String getInfosysName() {
		return infosysName;
	}

	public void setInfosysName(String infosysName) {
		this.infosysName = infosysName;
	}

	public String getRequestBodyTemplate() {
		return requestBodyTemplate;
	}

	public void setRequestBodyTemplate(String requestBodyTemplate) {
		this.requestBodyTemplate = requestBodyTemplate;
	}

	public int getListingPosition() {
		return listingPosition;
	}

	public void setListingPosition(int listingPosition) {
		this.listingPosition = listingPosition;
	}

	/** Nie null: eine alte Zeile ohne Wert oder ein Import ohne das Feld gilt als nirgends gezeigt. */
	public List<String> getDisplayContextKeys() {
		return displayContextKeys != null ? displayContextKeys : List.of();
	}

	public void setDisplayContextKeys(List<String> displayContextKeys) {
		this.displayContextKeys = displayContextKeys;
	}

	/** Ob der Aufruf an diesem Ort von selbst erscheint */
	public boolean isShownIn(String displayContextKey) {
		return getDisplayContextKeys().contains(displayContextKey);
	}

}
