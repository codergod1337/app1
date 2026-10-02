package codergod1337.app1.file.fileextension.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;

/**
 * Eine Dateiendung im Katalog, aus dem die Dateiarten ihre erlaubten Endungen freischalten. Hier steht, was das Format
 * kann. Tika bekommt ohnehin jede Datei, dafür gibt es keinen Schalter.
 */
@Entity
@Table(name = "file_extension")
public class FileExtension {

	/** Die Endung selbst: klein, ohne Punkt, z. B. pdf. Ändert sich nie, die Dateiarten schalten sie per String frei. */
	@Id
	@NotBlank
	@Pattern(regexp = "[a-z0-9]{1,20}")
	@Column(nullable = false, updatable = false, length = 20)
	private String extension;

	/** Mehrsprachiges JSON: wofür die Endung da ist, damit niemand eine exotische (catpart, 3mf) versehentlich löscht. */
	@Column(columnDefinition = "text")
	private String description;

	/** Verweis auf FileExtensionCollection.key, z. B. BILDER. null: ohne Gruppe. */
	@Size(max = 200)
	@Column(length = 200)
	private String fileExtensionCollectionKey;

	/** Heikel, z. B. html oder exe: Beim Freischalten wird gewarnt, gesperrt wird nicht. */
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean dangerous;

	/** Reihenfolge innerhalb der Gruppe, in Katalog und Freischalt-Matrix. Nicht eindeutig, danach nach extension. */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	/** Text aus Bildern und Scans erkennen */
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean ocr;

	/** Für die maschinelle Auswertung */
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean ki;

	/** Ein Vorschaubild rechnen */
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean imagePreview;

	/** Für JPA und Jackson. */
	protected FileExtension() {
	}

	/** Admin und Import: jedes Feld. */
	public FileExtension(String extension, String description, String fileExtensionCollectionKey, boolean dangerous,
			int listingPosition, boolean ocr, boolean ki, boolean imagePreview) {
		this.extension = extension;
		this.description = description;
		this.fileExtensionCollectionKey = fileExtensionCollectionKey;
		this.dangerous = dangerous;
		this.listingPosition = listingPosition;
		this.ocr = ocr;
		this.ki = ki;
		this.imagePreview = imagePreview;
	}

	public String getExtension() {
		return extension;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getFileExtensionCollectionKey() {
		return fileExtensionCollectionKey;
	}

	public void setFileExtensionCollectionKey(String fileExtensionCollectionKey) {
		this.fileExtensionCollectionKey = fileExtensionCollectionKey;
	}

	public boolean isDangerous() {
		return dangerous;
	}

	public void setDangerous(boolean dangerous) {
		this.dangerous = dangerous;
	}

	public int getListingPosition() {
		return listingPosition;
	}

	public void setListingPosition(int listingPosition) {
		this.listingPosition = listingPosition;
	}

	public boolean isOcr() {
		return ocr;
	}

	public void setOcr(boolean ocr) {
		this.ocr = ocr;
	}

	public boolean isKi() {
		return ki;
	}

	public void setKi(boolean ki) {
		this.ki = ki;
	}

	public boolean isImagePreview() {
		return imagePreview;
	}

	public void setImagePreview(boolean imagePreview) {
		this.imagePreview = imagePreview;
	}

}
