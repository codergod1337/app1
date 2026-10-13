package codergod1337.app1.mapping.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Das Gesamt-Mapping einer Antwort (User 2026-10-10): genau eines je Response-Typ. Der Typ ist zusammengesetzt aus
 * {@code responseObjectType} (bei ABAS {@code content.data.meta.objectType}, z. B. {@code obj:data:19:0}) und
 * optional weiteren Feldern, die an einen Inhalt gekoppelt sind ({@code matchConditionsJson}): So werden aus 19:0 zwei
 * Mappings, die Firma und der Ansprechpartner, unterschieden an {@code grbez.value}. Die Einträge darin sind die
 * {@link ResponseMappingLine}: je Eintrag die Quelle im JSON, optional eine Bedingung, und der Key, an den der Wert
 * geht. Diese Keys werden später an den Solr-Feldern verknüpft ({@code solr_field.command}).
 *
 * Das Mapping kennt weder ABAS noch Solr: {@code targetKey} sagt nur, welches Dokument die Antwort füllt
 * ({@code SOLR_<KERN>}), eine Antwort, die nur als Subfetch dient, hat keins. Adressen nennen immer Feldnamen, nie
 * Positionen; das Format des Keys prüft {@code HelperInputs.isValidKey}.
 */
@Entity
@Table(name = "mapping_response", indexes = {
		@Index(name = "idx_mapping_response_key", columnList = "key", unique = true) })
public class ResponseMapping {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, updatable = false)
	private Long id;

	/** Der Name, normale Key-Regel, z. B. KONTAKT_ANSPRECHPARTNER, eindeutig, ändert sich nie */
	@NotBlank
	@Size(max = 200)
	@Column(name = "key", nullable = false, updatable = false, length = 200)
	private String key;

	/** Der Typ der Antwort, z. B. obj:data:19:0 oder infosys:data:st:CUSTOMERPRODUCT */
	@NotBlank
	@Size(max = 200)
	@Column(name = "response_object_type", nullable = false, length = 200)
	private String responseObjectType;

	/**
	 * JSON-Liste von {@link ResponseMappingMatchCondition}: weitere Felder, die zusammen mit dem Typ die Antwort
	 * kennzeichnen, leer heißt der Typ allein
	 */
	@NotBlank
	@Column(name = "match_conditions_json", nullable = false, columnDefinition = "text")
	private String matchConditionsJson;

	/** Welches Dokument diese Antwort füllt, Key-Regel, SOLR_<KERN>; null bei einer Antwort, die nur als Subfetch dient */
	@Size(max = 200)
	@Column(name = "target_key", length = 200)
	private String targetKey;

	/**
	 * JSON-Liste von Adressen, die bei diesem Typ nicht gebraucht werden (User 2026-10-10): Der Baum blendet sie aus,
	 * so bleibt beim Durchklicken nur übrig, was noch zu entscheiden ist. Listenelemente als [*], nie eine Position.
	 */
	@NotBlank
	@Column(name = "ignored_paths_json", nullable = false, columnDefinition = "text")
	private String ignoredPathsJson;

	/** JSON-Objekt {@link ResponseMappingFetchWay}: wie die Antwort geholt wird, bei STERNZEIT mit Beispiel-Sternzeit, bei INFOSYS mit Parametern */
	@NotBlank
	@Column(name = "fetch_way_json", nullable = false, columnDefinition = "text")
	private String fetchWayJson;

	/** Für JPA und Jackson. */
	protected ResponseMapping() {
	}

	/** Admin: jedes Feld, die id vergibt die Datenbank. */
	public ResponseMapping(String key, String responseObjectType, String matchConditionsJson, String targetKey,
			String ignoredPathsJson, String fetchWayJson) {
		this.key = key;
		this.responseObjectType = responseObjectType;
		this.matchConditionsJson = matchConditionsJson;
		this.targetKey = targetKey;
		this.ignoredPathsJson = ignoredPathsJson;
		this.fetchWayJson = fetchWayJson;
	}

	public Long getId() {
		return id;
	}

	public String getKey() {
		return key;
	}

	public String getResponseObjectType() {
		return responseObjectType;
	}

	public void setResponseObjectType(String responseObjectType) {
		this.responseObjectType = responseObjectType;
	}

	public String getMatchConditionsJson() {
		return matchConditionsJson;
	}

	public void setMatchConditionsJson(String matchConditionsJson) {
		this.matchConditionsJson = matchConditionsJson;
	}

	public String getTargetKey() {
		return targetKey;
	}

	public void setTargetKey(String targetKey) {
		this.targetKey = targetKey;
	}

	public String getIgnoredPathsJson() {
		return ignoredPathsJson;
	}

	public void setIgnoredPathsJson(String ignoredPathsJson) {
		this.ignoredPathsJson = ignoredPathsJson;
	}

	public String getFetchWayJson() {
		return fetchWayJson;
	}

	public void setFetchWayJson(String fetchWayJson) {
		this.fetchWayJson = fetchWayJson;
	}

}
