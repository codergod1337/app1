package codergod1337.app1.mapping.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ein Eintrag eines {@link ResponseMapping} (User 2026-10-10): erst die Quelle im JSON, dann optional eine Bedingung,
 * dann der Key, an den der Wert geht. Die Einträge laufen in Reihenfolge; derselbe Key zweimal ist eine Kaskade, der
 * erste Wert gewinnt. Derselbe Key in zwei Mappings ist gewollt: Firma und Ansprechpartner liefern beide
 * {@code KONTAKT_NAME}.
 *
 * Adressen über Feldnamen, nie über Positionen: {@code $.content.data.head.fields.name.value}. Eine Liste über
 * {@code sourcePath} auf die Liste und {@code elementPath} je Element: {@code $.content.data.table[artikel]} und
 * {@code fields.artikel.text} liefert die Artikelnummern aller Zeilen, in denen {@code artikel} gefüllt ist, als Liste
 * für ein multiValued-Feld; {@code [*]} alle Zeilen, {@code [typa=(AU/BE-Position,BV)]} ein Wert. Eine Zeile ohne Wert
 * bleibt eine Lücke, damit die Listen eines Mappings zeilengleich bleiben.
 *
 * Vier Arten ({@link ResponseMappingLineKind}): VALUE ein Wert oder eine Liste; TABLE eine ganze Tabelle in ein Feld,
 * je Zeile ein JSON-Objekt mit den Spalten aus {@code tableColumnsJson}; SUBFETCH_STERNZEIT und SUBFETCH_INFOSYS holen
 * eine weitere Antwort, ihre Eingaben stehen in {@code subfetchInputParametersJson}, aus dem JSON oder statisch, beim
 * Infosystem zeigt {@code abasInfosysKey} auf den hinterlegten Aufruf mit seiner Body-Vorlage.
 *
 * Ein Subfetch je Zeile (User 2026-10-13, Prüfeinstellung → Prüfplan): trägt er in {@code tableLineKey} den Key eines
 * TABLE-Eintrags desselben Mappings, läuft er je Zeile dieser Tabelle. Seine {@code path}-Eingaben sind dann Adressen
 * je Zeilenelement wie der {@code elementPath} einer Spalte ({@code fields.yqmappqm2pplan.value}), und die Werte der
 * Unterantwort hängen als Objekt unter dem Key dieses Eintrags in der Zeile.
 */
@Entity
@Table(name = "mapping_response_line", indexes = {
		@Index(name = "idx_mapping_response_line_mapping", columnList = "response_mapping_id, position") })
public class ResponseMappingLine {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, updatable = false)
	private Long id;

	/** Verweis auf {@link ResponseMapping}, von Hand aufgelöst */
	@Column(name = "response_mapping_id", nullable = false)
	private Long responseMappingId;

	/** Die Reihenfolge im Mapping, ab 1 */
	@Column(name = "position", nullable = false)
	private int position;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, length = 20)
	private ResponseMappingLineKind kind;

	/** Die Quelle im JSON bei VALUE und TABLE (dort die Liste); bei einem Subfetch leer, dessen Eingaben stehen in den Parametern */
	@Column(name = "source_path", columnDefinition = "text")
	private String sourcePath;

	/** null heißt sourcePath ist ein Wert; gesetzt der Unter-Key je Element der Liste an sourcePath, z. B. fields.artikel.text */
	@Column(name = "element_path", columnDefinition = "text")
	private String elementPath;

	/** Die optionale Bedingung: nur wenn an onlyIfPath der Wert onlyIfEquals steht */
	@Column(name = "only_if_path", columnDefinition = "text")
	private String onlyIfPath;

	@Column(name = "only_if_equals", columnDefinition = "text")
	private String onlyIfEquals;

	/** Der Key, an den der Wert geht, Key-Regel, z. B. KONTAKT_NAME; später an einem Solr-Feld verknüpft */
	@NotBlank
	@Size(max = 200)
	@Column(name = "key", nullable = false, length = 200)
	private String key;

	/** null heißt ein normaler Wert; PRIMARY die eindeutige id des Dokuments, COMPOSITE ihre Erweiterung, ALTERNATIVE ein weiterer eindeutiger Wert */
	@Enumerated(EnumType.STRING)
	@Column(name = "id_role", length = 20)
	private ResponseMappingLineIdRole idRole;

	/** Nur bei SUBFETCH_INFOSYS: der Key des hinterlegten Aufrufs ({@code AbasInfosys}), loser Fremdschlüssel */
	@Size(max = 200)
	@Column(name = "abas_infosys_key", length = 200)
	private String abasInfosysKey;

	/** Nur bei Subfetches: die Eingaben als JSON-Liste von {@link ResponseMappingSubfetchInputParameter} */
	@Column(name = "subfetch_input_parameters_json", columnDefinition = "text")
	private String subfetchInputParametersJson;

	/** Nur bei TABLE: die Spalten als JSON-Liste von {@link ResponseMappingTableColumn} */
	@Column(name = "table_columns_json", columnDefinition = "text")
	private String tableColumnsJson;

	/**
	 * Nur bei Subfetches: der Key des TABLE-Eintrags desselben Mappings, je dessen Zeile der Subfetch läuft; null heißt
	 * einmal für den Kopf der Antwort, wie bisher
	 */
	@Size(max = 200)
	@Column(name = "table_line_key", length = 200)
	private String tableLineKey;

	/** Für JPA und Jackson. */
	protected ResponseMappingLine() {
	}

	/** Der Service legt die Einträge mit dem Mapping an, die id vergibt die Datenbank. */
	public ResponseMappingLine(Long responseMappingId, int position, ResponseMappingLineKind kind, String sourcePath,
			String elementPath, String onlyIfPath, String onlyIfEquals, String key, ResponseMappingLineIdRole idRole,
			String abasInfosysKey, String subfetchInputParametersJson, String tableColumnsJson, String tableLineKey) {
		this.responseMappingId = responseMappingId;
		this.position = position;
		this.kind = kind;
		this.sourcePath = sourcePath;
		this.elementPath = elementPath;
		this.onlyIfPath = onlyIfPath;
		this.onlyIfEquals = onlyIfEquals;
		this.key = key;
		this.idRole = idRole;
		this.abasInfosysKey = abasInfosysKey;
		this.subfetchInputParametersJson = subfetchInputParametersJson;
		this.tableColumnsJson = tableColumnsJson;
		this.tableLineKey = tableLineKey;
	}

	public Long getId() {
		return id;
	}

	public Long getResponseMappingId() {
		return responseMappingId;
	}

	public int getPosition() {
		return position;
	}

	/** Nur der Positions-Endpunkt setzt sie nach Drag and Drop; beim Speichern des Mappings zählt der Service selbst durch */
	public void setPosition(int position) {
		this.position = position;
	}

	public ResponseMappingLineKind getKind() {
		return kind;
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public String getElementPath() {
		return elementPath;
	}

	public String getOnlyIfPath() {
		return onlyIfPath;
	}

	public String getOnlyIfEquals() {
		return onlyIfEquals;
	}

	public String getKey() {
		return key;
	}

	public ResponseMappingLineIdRole getIdRole() {
		return idRole;
	}

	public String getAbasInfosysKey() {
		return abasInfosysKey;
	}

	public String getSubfetchInputParametersJson() {
		return subfetchInputParametersJson;
	}

	public String getTableColumnsJson() {
		return tableColumnsJson;
	}

	public String getTableLineKey() {
		return tableLineKey;
	}

	/** Ein Subfetch holt eine weitere Antwort, ein Wert oder eine Tabelle nicht */
	public boolean isSubfetch() {
		return kind == ResponseMappingLineKind.SUBFETCH_STERNZEIT || kind == ResponseMappingLineKind.SUBFETCH_INFOSYS;
	}

	/** Ein Subfetch je Zeile eines TABLE-Eintrags, statt einmal für den Kopf */
	public boolean isRowSubfetch() {
		return isSubfetch() && tableLineKey != null && !tableLineKey.isBlank();
	}

}
