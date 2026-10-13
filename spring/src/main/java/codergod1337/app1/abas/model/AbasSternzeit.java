package codergod1337.app1.abas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Der Stammsatz einer ABAS-Objektkennung, hausintern Sternzeit, z. B. (87448,19,0): jede Sternzeit, die je erfolgreich
 * geholt wurde, steht genau einmal hier. Kein Statusfeld, kein gespeicherter Sync-Zustand, deshalb übersteht die Tabelle
 * jeden Neustart.
 *
 * Gebaut für 10^8 bis 10^9 Zeilen (User 2026-10-10): Primärschlüssel ist ein Long mit Autoincrement, der
 * Primärschlüssel-Index trägt damit 8 Byte je Zeile statt des Texts. Die Sternzeit selbst hat den einen eindeutigen
 * Index mit {@code COLLATE "C"} (byteweiser Vergleich, Keyset über {@code sternzeit > :ab} trifft ihn). Keine Indizes
 * auf den Datumsfeldern. Der Typ als smallint auf {@link AbasObjectType} statt als Text je Zeile. Die Teilindizes für
 * die kleinen Mengen stehen in docs/backend/abas-indizes.sql, JPA kann sie nicht. Regeln für die Services: kein count(*)
 * über diese Tabelle, nur die Schätzung aus pg_class; keine LIKE-Suche, nur der Treffer über den Sternzeit-Index;
 * Blättern nur per Keyset; Massen-Einfügen nur per Batch mit ON CONFLICT DO NOTHING.
 */
@Entity
@Table(name = "abas_sternzeit", indexes = {
		@Index(name = "idx_abas_sternzeit_sternzeit", columnList = "sternzeit", unique = true) })
public class AbasSternzeit {

	/** So lang darf eine Sternzeit höchstens sein, dasselbe Maß in Queue und Protokollen */
	public static final int MAX_LENGTH = 30;

	/** Die volle Klammerform (Nummer,Datenbank,Gruppe), und nur sie: kein Teiltreffer, keine Ergänzung */
	private static final Pattern VALID_STERNZEIT = Pattern.compile("\\(\\d+,\\d+,\\d+\\)");

	/**
	 * true, wenn der Text eine Sternzeit in voller Klammerform ist, z. B. (87448,19,0), und ins Spaltenmaß passt.
	 * {@code null} ist keine gültige Sternzeit. Dieselbe Regel steht im Frontend in modul/abasmanager/AbasSternzeit.ts.
	 */
	public static boolean isValidSternzeit(String sternzeitText) {
		return sternzeitText != null && sternzeitText.length() <= MAX_LENGTH
				&& VALID_STERNZEIT.matcher(sternzeitText).matches();
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, updatable = false)
	private Long id;

	/** Die ABAS-Kennung in Klammerform, z. B. (87448,19,0), eindeutig */
	@Column(name = "sternzeit", nullable = false, updatable = false, columnDefinition = "varchar(30) collate \"C\"")
	private String sternzeit;

	/** Verweis auf {@link AbasObjectType}, von Hand aufgelöst. null heißt noch nie geholt. */
	@Column(name = "object_type_id")
	private Short objectTypeId;

	/**
	 * „Im Kern seit“: wann ABAS diese Sternzeit zum ersten Mal gepusht hat. Wird genau einmal geschrieben, beim Anlegen
	 * der Zeile, danach nie mehr (User 2026-10-10).
	 */
	@Column(name = "first_pushed_at", nullable = false, updatable = false)
	private Instant firstPushedAt;

	/** Wann wir sie zuletzt erfolgreich geholt haben; der Resync stempelt hier als Cursor */
	@Column(name = "last_sync_at")
	private Instant lastSyncAt;

	/** Wie oft insgesamt geholt, nur Statistik */
	@Column(name = "fetch_count", nullable = false)
	private int fetchCount;

	/** Für JPA und Jackson. */
	protected AbasSternzeit() {
	}

	/**
	 * Ein neuer Stammsatz, noch ohne Sync-Stempel und ohne Typ. firstPushedAt ist jetzt und bleibt so, die id vergibt
	 * die Datenbank.
	 */
	public AbasSternzeit(String sternzeit) {
		this.sternzeit = sternzeit;
		this.firstPushedAt = Instant.now();
		this.fetchCount = 0;
	}

	public Long getId() {
		return id;
	}

	public String getSternzeit() {
		return sternzeit;
	}

	public Short getObjectTypeId() {
		return objectTypeId;
	}

	public void setObjectTypeId(Short objectTypeId) {
		this.objectTypeId = objectTypeId;
	}

	public Instant getFirstPushedAt() {
		return firstPushedAt;
	}

	public Instant getLastSyncAt() {
		return lastSyncAt;
	}

	public void setLastSyncAt(Instant lastSyncAt) {
		this.lastSyncAt = lastSyncAt;
	}

	public int getFetchCount() {
		return fetchCount;
	}

	public void setFetchCount(int fetchCount) {
		this.fetchCount = fetchCount;
	}

}
