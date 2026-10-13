package codergod1337.app1.abas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Die Sperrliste (User 2026-10-10): eine Sternzeit, die hier steht, kommt nie in die Queue. Jeder Weg, der einreiht,
 * also der ERP-Eingang, der Admin von Hand, Import und Resync, prüft vorher {@code existsBySternzeit}; ein Treffer ist
 * ein 403, und nichts wird geschrieben. Dasselbe Maß und dieselbe Collation wie am Stammsatz, der eine eindeutige Index
 * trägt die Prüfung, sie kostet bei jeder Größe gleich viel.
 */
@Entity
@Table(name = "abas_ignored_sternzeit", indexes = {
		@Index(name = "idx_abas_ignored_sternzeit_sternzeit", columnList = "sternzeit", unique = true) })
public class AbasIgnoredSternzeit {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, updatable = false)
	private Long id;

	/** Die gesperrte Kennung in Klammerform, eindeutig */
	@Column(name = "sternzeit", nullable = false, updatable = false, columnDefinition = "varchar(30) collate \"C\"")
	private String sternzeit;

	/** Warum gesperrt, freier Text, darf leer sein */
	@Column(name = "reason", columnDefinition = "text")
	private String reason;

	/** Wann gesperrt */
	@Column(name = "ignored_at", nullable = false)
	private Instant ignoredAt;

	/** Für JPA und Jackson. */
	protected AbasIgnoredSternzeit() {
	}

	/** Eine neue Sperre, gesperrt jetzt. Die id vergibt die Datenbank. */
	public AbasIgnoredSternzeit(String sternzeit, String reason) {
		this.sternzeit = sternzeit;
		this.reason = reason;
		this.ignoredAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getSternzeit() {
		return sternzeit;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public Instant getIgnoredAt() {
		return ignoredAt;
	}

}
