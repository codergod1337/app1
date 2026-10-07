package codergod1337.app1.solr.grant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Die Hook-Freigabe, serverweit (Vorgabe 2026-10-07): das Recht, nach einem Hook aufzulösen, obwohl der User
 * {@code HOOK_FULL} nicht hat. Ohne Kern, denn jeder Kern trägt sämtliche Hooks und ein Hook ist in jedem Kern dasselbe
 * Feld: Wer einen Hook freigegeben bekommt, löst in jedem Kern danach auf, in dem er die Tür {@code <KERN>} hat. Der
 * Hook wird damit auch lesbar. {@code HOOK_FULL} ist dieselbe Freigabe für alle Hooks auf einmal. Die Feldfreigabe
 * (SolrAdvancedFieldGrantAccessPerCore) ist das Gegenstück für normale Felder, je Kern.
 *
 * Nichts davon in Solr. Eindeutig ist das Paar Empfänger und Hook nur unter den aktiven Zeilen, das prüft der Service:
 * Eine widerrufene Zeile bleibt als Protokoll stehen, danach darf derselbe Hook neu freigegeben werden.
 *
 * Nichts wird gelöscht, eine Freigabe wechselt nur den Status (EntityState im Bereich GRANT: ACTIVE, REVOKED, EXPIRED).
 * Keine JPA-Beziehungen, User und Hook stehen als guid und key.
 */
@Entity
@Table(name = "solr_hook_grant_access")
public class SolrHookGrantAccess {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Wer die Freigabe bekommt */
	@NotNull
	@Column(nullable = false, updatable = false)
	private UUID granteeUsersGuid;

	/** Der Hook (SolrHook.key), zugleich der Name des Feldes in jedem Kern. Ändert sich nie. */
	@NotBlank
	@Size(max = 100)
	@Column(nullable = false, updatable = false, length = 100)
	private String hookKey;

	/** Wer freigegeben hat */
	@NotNull
	@Column(nullable = false, updatable = false)
	private UUID grantedByUsersGuid;

	@NotNull
	@Column(nullable = false, updatable = false)
	private Instant grantedAt;

	/** null heißt unbegrenzt. Der Ablauf setzt abgelaufene Freigaben auf EXPIRED. */
	private Instant validUntil;

	/** key des EntityState im Bereich GRANT: ACTIVE, REVOKED oder EXPIRED */
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, length = 200)
	private String status;

	/** Beim Anlegen gleich grantedAt, danach der letzte Statuswechsel */
	@NotNull
	@Column(nullable = false)
	private Instant statusChangedAt;

	/** Beim Anlegen der Freigebende, beim Entzug wer entzogen hat, beim Ablauf der System-User */
	@NotNull
	@Column(nullable = false)
	private UUID statusChangedByUsersGuid;

	/** Für JPA und Jackson. */
	protected SolrHookGrantAccess() {
	}

	/** Neue Freigabe: aktiv, Statuswechsel gleich dem Anlegen. */
	public SolrHookGrantAccess(UUID granteeUsersGuid, String hookKey, UUID grantedByUsersGuid, Instant grantedAt,
			Instant validUntil, String status) {
		this.granteeUsersGuid = granteeUsersGuid;
		this.hookKey = hookKey;
		this.grantedByUsersGuid = grantedByUsersGuid;
		this.grantedAt = grantedAt;
		this.validUntil = validUntil;
		this.status = status;
		this.statusChangedAt = grantedAt;
		this.statusChangedByUsersGuid = grantedByUsersGuid;
	}

	public Long getId() {
		return id;
	}

	public UUID getGranteeUsersGuid() {
		return granteeUsersGuid;
	}

	public String getHookKey() {
		return hookKey;
	}

	public UUID getGrantedByUsersGuid() {
		return grantedByUsersGuid;
	}

	public Instant getGrantedAt() {
		return grantedAt;
	}

	public Instant getValidUntil() {
		return validUntil;
	}

	public String getStatus() {
		return status;
	}

	/** Statuswechsel immer mit wann und wer, nie der Status allein */
	public void changeStatus(String status, Instant statusChangedAt, UUID statusChangedByUsersGuid) {
		this.status = status;
		this.statusChangedAt = statusChangedAt;
		this.statusChangedByUsersGuid = statusChangedByUsersGuid;
	}

	public Instant getStatusChangedAt() {
		return statusChangedAt;
	}

	public UUID getStatusChangedByUsersGuid() {
		return statusChangedByUsersGuid;
	}

}
