package codergod1337.app1.message.messagesubclass.model;

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
 * Eine Nachrichtenart (MSC, User 2026-10-13, nach der MessageSubClass der Vorlage): das Gegenstück zur Dateiart. Jede
 * archivierte Nachricht gehört in genau eine Art, an ihr hängt, wer sie lesen und wer ihre Hooks pflegen darf. Beim
 * Archivieren wird daraus die readAcl der Nachricht. Die Arten sind Vorgangsarten, keine Quellen: Eine Reklamation ist
 * eine Reklamation, ob sie als Mail kam oder am Telefon notiert wurde.
 *
 * Schreiben heißt hier Hookpflege und nichts sonst (Vorlage): Betreff, Text, Beteiligte und Anhänge einer Nachricht
 * bleiben, wie sie kamen, geändert werden nur ihre Hooks, also ihre Suchbarkeit. Keine Endungen, kein Tika, das sind
 * Dinge einer Datei.
 *
 * Das Format des Keys prüft {@code HelperInputs.isValidKey}, nicht diese Klasse.
 */
@Entity
@Table(name = "message_sub_class")
public class MessageSubClass {

	private static final String HEX_COLOR = "#[0-9a-fA-F]{6}";

	/** Der Schlüssel, z. B. REKLAMATION. Von Hand vergeben, ändert sich nie: Nachrichten verweisen darüber hierher. */
	@Id
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, updatable = false, length = 200)
	private String key;

	/** Mehrsprachiges JSON, z. B. {"de":"Reklamation"}. Pflicht. */
	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String displayName;

	/** Mehrsprachiges JSON wie displayName, optional. */
	@Column(columnDefinition = "text")
	private String description;

	/** Reihenfolge in Listen, nicht eindeutig. Sortiert wird nach Position und dann nach key. */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	/** Symbol der Nachrichtenart, z. B. {"pack":"bootstrap","id":"envelope"}, wie an der Dateiart. */
	@Valid
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private AccessRoleSymbol symbol;

	/** Farbe des Symbols, immer Hex. */
	@Pattern(regexp = HEX_COLOR)
	@Column(length = 7)
	private String color;

	/** Wer Nachrichten dieser Art lesen darf: AR-Keys. Enthält immer auch alle Schreibrollen, das setzt der Service durch. */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> readAccessRoleKeys;

	/** Wer die Hooks einer Nachricht dieser Art pflegen darf: AR-Keys. Nie die Nachricht selbst. */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> writeAccessRoleKeys;

	/**
	 * Die Anzeigeorte (User 2026-10-13): Keys der Stellen, die Nachrichten dieser Art als Abschnitt zeigen, aus demselben
	 * Katalog wie bei den Dateiarten (FileSubClassDestination), z. B. KUNDENMAPPE. So findet eine Mappe Dateien und
	 * Nachrichten über denselben Ort. Leer heißt: nirgends als Abschnitt.
	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> displayContextKeys;

	/**
	 * Die Adressen der Postfächer, die an dieser Nachrichtenart hängen (User 2026-10-13, ein Versuch, ob wir das
	 * brauchen). Später wird damit eine Nachricht anhand ihres Postfachs dieser Art zugeordnet oder die Art anhand des
	 * Postfachs vorausgewählt. Lose gekoppelt über die Adresse, ohne Verweis auf eine Postfach-Entity; klein geschrieben,
	 * ohne Doppelte. Ein Postfach hängt höchstens an einer Nachrichtenart, sonst wäre die Zuordnung nicht eindeutig, das
	 * setzt der Service durch.
	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> mailboxEmailAddresses;

	/**
	 * Die Triggerwörter (User 2026-10-14, die Rubriken der Vorlage): Wörter in Betreff und Mailtext, die für diese Art
	 * sprechen. Beim Archivieren zählt jeder Treffer einen Punkt; die Art mit den meisten Punkten wird vorgeschlagen.
	 * Getrimmt, ohne Doppelte (Groß- und Kleinschreibung egal), das setzt der Service durch.
	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> triggerWords;

	/**
	 * Die Trigger-Hooks (User 2026-10-14): Hooks, deren Wert für diese Art spricht, z. B. auftragsNummer für AUFTRAG.
	 * Jeder dieser Hooks mit Wert an der Nachricht zählt zwei Punkte. Hook-Keys, angelegte prüft der Service; wird ein
	 * Hook gelöscht, bleibt sein Key hier stehen und wirkt nicht mehr.
	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> triggerHookKeys;

	/** Für JPA und Jackson. */
	protected MessageSubClass() {
	}

	/** Admin und Import: jedes Feld. */
	public MessageSubClass(String key, String displayName, String description, int listingPosition,
			AccessRoleSymbol symbol, String color, List<String> readAccessRoleKeys, List<String> writeAccessRoleKeys,
			List<String> displayContextKeys, List<String> mailboxEmailAddresses, List<String> triggerWords,
			List<String> triggerHookKeys) {
		this.key = key;
		this.displayName = displayName;
		this.description = description;
		this.listingPosition = listingPosition;
		this.symbol = symbol;
		this.color = color;
		this.readAccessRoleKeys = readAccessRoleKeys;
		this.writeAccessRoleKeys = writeAccessRoleKeys;
		this.displayContextKeys = displayContextKeys;
		this.mailboxEmailAddresses = mailboxEmailAddresses;
		this.triggerWords = triggerWords;
		this.triggerHookKeys = triggerHookKeys;
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

	/** Nie null: eine Zeile ohne Wert oder ein Import ohne das Feld gilt als nirgends gezeigt. */
	public List<String> getDisplayContextKeys() {
		return displayContextKeys != null ? displayContextKeys : List.of();
	}

	public void setDisplayContextKeys(List<String> displayContextKeys) {
		this.displayContextKeys = displayContextKeys;
	}

	/** Nie null: ohne Wert hängt kein Postfach an dieser Art. */
	public List<String> getMailboxEmailAddresses() {
		return mailboxEmailAddresses != null ? mailboxEmailAddresses : List.of();
	}

	public void setMailboxEmailAddresses(List<String> mailboxEmailAddresses) {
		this.mailboxEmailAddresses = mailboxEmailAddresses;
	}

	/** Nie null: ohne Wert keine Triggerwörter. */
	public List<String> getTriggerWords() {
		return triggerWords != null ? triggerWords : List.of();
	}

	public void setTriggerWords(List<String> triggerWords) {
		this.triggerWords = triggerWords;
	}

	/** Nie null: ohne Wert keine Trigger-Hooks. */
	public List<String> getTriggerHookKeys() {
		return triggerHookKeys != null ? triggerHookKeys : List.of();
	}

	public void setTriggerHookKeys(List<String> triggerHookKeys) {
		this.triggerHookKeys = triggerHookKeys;
	}

}
