package codergod1337.app1.solr.core.model;

import codergod1337.app1.solr.SolrLook;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.hibernate.annotations.ColumnDefault;

/**
 * Ein Solr-Kern, z. B. DMS oder ARTIKEL. Der key folgt der normalen Key-Regel und ist zugleich der Name des Kerns in
 * Solr. Aus ihm ergeben sich später die Rollen {@code <KEY>} (Auflösen) und {@code <KEY>_SUCHE} (Suchen).
 *
 * Kein Badge, sondern ein Emblem: Symbol und Anzeigename auf dem Verlauf, das Aussehen steckt im {@link SolrLook}.
 *
 * Das Format des Keys prüft {@code SolrCoreService.isValidSolrCoreKey}, nicht diese Klasse.
 */
@Entity
@Table(name = "solr_core")
public class SolrCore {

	/** Der Schlüssel und Name des Kerns in Solr, z. B. DMS. Von Hand vergeben, ändert sich nie. */
	@Id
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, updatable = false, length = 200)
	private String key;

	/** Mehrsprachiges JSON, z. B. {"de":"Dokumente"}. Pflicht, steht am Tab und im Emblem. */
	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String displayName;

	/** Das Aussehen des Emblems. Spalten liegen flach in dieser Tabelle. */
	@Valid
	@Embedded
	private SolrLook look;

	/** Reihenfolge der Tabs, nicht eindeutig. Sortiert wird nach Position und dann nach key. */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	/** Für JPA und Jackson. */
	protected SolrCore() {
	}

	/** Admin und Import: jedes Feld. Das Feld id legt der Service dazu. */
	public SolrCore(String key, String displayName, SolrLook look, int listingPosition) {
		this.key = key;
		this.displayName = displayName;
		this.look = look != null ? look : SolrLook.EMPTY;
		this.listingPosition = listingPosition;
	}

	/**
	 * Das Feld id: der Primärschlüssel (uniqueKey) jedes Kerns, als Feld angelegt, sobald der Kern entsteht. Es gibt
	 * keine Wahl einer Quelle am Kern: Was hineinkommt, steht in der Beschreibung des Feldes, z. B. beim DMS der Hash der
	 * Bytes, und wer den Kern befüllt, hält sich daran.
	 */
	public static final String ID_FIELD_NAME = "id";

	/**
	 * Das Feld cursorDate: wann ein Reindex-Lauf das Dokument zuletzt angefasst hat, der Fortschrittszeiger eines Laufs.
	 * Wie id in jedem Kern, vom Backend angelegt.
	 */
	public static final String CURSOR_DATE_FIELD_NAME = "cursorDate";

	/** Die Felder, die jeder Kern hat: mit dem Kern angelegt, beim Start nachgezogen, nicht löschbar, Typ fest */
	public static final Set<String> BUILT_IN_FIELD_NAMES = Set.of(ID_FIELD_NAME, CURSOR_DATE_FIELD_NAME);

	public String getKey() {
		return key;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	/** Nie null: Sind alle Spalten leer, liefert Hibernate null, nach außen gilt dann Standard. */
	public SolrLook getLook() {
		return look != null ? look : SolrLook.EMPTY;
	}

	public void setLook(SolrLook look) {
		this.look = look != null ? look : SolrLook.EMPTY;
	}

	public int getListingPosition() {
		return listingPosition;
	}

	public void setListingPosition(int listingPosition) {
		this.listingPosition = listingPosition;
	}

}
