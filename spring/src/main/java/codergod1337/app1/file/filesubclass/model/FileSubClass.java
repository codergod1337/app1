package codergod1337.app1.file.filesubclass.model;

import codergod1337.app1.system.access.model.AccessRoleSymbol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Eine Dateiart (FSC): was ein Dokument fachlich ist, z. B. RECHNUNG oder DATENBLATT. Beim Upload die einzige Wahl
 * des Users, deshalb hängen hier die Rechte und die erlaubten Endungen.
 *
 * Das Format des Keys prüft {@code HelperInputs.isValidKey}, nicht diese Klasse.
 */
@Entity
@Table(name = "file_sub_class")
public class FileSubClass {

	private static final String HEX_COLOR = "#[0-9a-fA-F]{6}";

	/** Der Schlüssel, z. B. RECHNUNG. Von Hand vergeben, ändert sich nie: Dateien verweisen darüber hierher. */
	@Id
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, updatable = false, length = 200)
	private String key;

	/** Mehrsprachiges JSON, z. B. {"de":"Rechnung"}. Pflicht. */
	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String displayName;

	/** Mehrsprachiges JSON wie displayName, optional. */
	@Column(columnDefinition = "text")
	private String description;

	/** Reihenfolge in Listen und Matrizen, nicht eindeutig. Sortiert wird nach Position und dann nach key. */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	/** Symbol der Dateiart, z. B. {"pack":"bootstrap","id":"receipt"}. Die Form drumherum kommt von der Endung. */
	@Valid
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private AccessRoleSymbol symbol;

	/** Farbe des Symbols, immer Hex. */
	@Pattern(regexp = HEX_COLOR)
	@Column(length = 7)
	private String color;

	/** Wer Dateien dieser Art lesen darf: AR-Keys. Enthält immer auch alle Schreibrollen, das setzt der Service durch. */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> readAccessRoleKeys;

	/** Wer Dateien dieser Art ändern darf: AR-Keys. */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> writeAccessRoleKeys;

	/**
	 * Die freigeschalteten Endungen, z. B. ["pdf","docx"], aus dem Endungskatalog. Erlaubnisliste: Leer heißt, die Art
	 * nimmt nichts an. Gepflegt nur über die Freischalt-Matrix, das Speichern der FSC fasst sie nie an.
	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> extensions;

	/** Für JPA und Jackson. */
	protected FileSubClass() {
	}

	/** Admin und Import: jedes Feld. */
	public FileSubClass(String key, String displayName, String description, int listingPosition,
			AccessRoleSymbol symbol, String color, List<String> readAccessRoleKeys, List<String> writeAccessRoleKeys,
			List<String> extensions) {
		this.key = key;
		this.displayName = displayName;
		this.description = description;
		this.listingPosition = listingPosition;
		this.symbol = symbol;
		this.color = color;
		this.readAccessRoleKeys = readAccessRoleKeys;
		this.writeAccessRoleKeys = writeAccessRoleKeys;
		this.extensions = extensions;
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

	public AccessRoleSymbol getSymbol() {
		return symbol;
	}

	public void setSymbol(AccessRoleSymbol symbol) {
		this.symbol = symbol;
	}

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	public List<String> getReadAccessRoleKeys() {
		return readAccessRoleKeys;
	}

	public void setReadAccessRoleKeys(List<String> readAccessRoleKeys) {
		this.readAccessRoleKeys = readAccessRoleKeys;
	}

	public List<String> getWriteAccessRoleKeys() {
		return writeAccessRoleKeys;
	}

	public void setWriteAccessRoleKeys(List<String> writeAccessRoleKeys) {
		this.writeAccessRoleKeys = writeAccessRoleKeys;
	}

	public List<String> getExtensions() {
		return extensions;
	}

	public void setExtensions(List<String> extensions) {
		this.extensions = extensions;
	}

}
