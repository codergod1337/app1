package codergod1337.app1.file.fileextension.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;

/**
 * Eine Gruppe von Endungen, z. B. Bilder oder CAD. Sie legt die Form fest, in der jede Datei mit einer ihrer Endungen
 * gezeichnet wird. Symbol und Farbe darin kommen von der Dateiart.
 *
 * Das Format des Keys prüft {@code HelperInputs.isValidKey}, nicht diese Klasse.
 */
@Entity
@Table(name = "file_extension_collection")
public class FileExtensionCollection {

	/**
	 * Der Schlüssel, z. B. BILDER. Von Hand vergeben, ändert sich nie: Die Endungen verweisen darüber hierher, in jeder
	 * Datenbank gleich, deshalb findet ihn auch der Import wieder.
	 */
	@Id
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, updatable = false, length = 200)
	private String key;

	/** Mehrsprachiges JSON, z. B. {"de":"Bilder"}. Pflicht. */
	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String displayName;

	/** Die Form, in der Dateien dieser Gruppe gezeichnet werden. null: Standardform. */
	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private SymbolShape symbolShape;

	/** Reihenfolge der Gruppen in Katalog und Matrix. Nicht eindeutig, danach nach key. */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	/** Für JPA und Jackson. */
	protected FileExtensionCollection() {
	}

	/** Admin und Import: jedes Feld. */
	public FileExtensionCollection(String key, String displayName, SymbolShape symbolShape, int listingPosition) {
		this.key = key;
		this.displayName = displayName;
		this.symbolShape = symbolShape;
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

	public SymbolShape getSymbolShape() {
		return symbolShape;
	}

	public void setSymbolShape(SymbolShape symbolShape) {
		this.symbolShape = symbolShape;
	}

	public int getListingPosition() {
		return listingPosition;
	}

	public void setListingPosition(int listingPosition) {
		this.listingPosition = listingPosition;
	}

}
