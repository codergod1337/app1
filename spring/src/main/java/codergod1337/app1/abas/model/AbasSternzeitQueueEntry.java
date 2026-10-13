package codergod1337.app1.abas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Ein Eintrag der Warteschlange: Diese Sternzeit soll geholt oder aufgelöst werden. Wer schreibt, wer liest: Der
 * ERP-Eingang legt die Sternzeit direkt im Request-Thread ab, genau ein Dispatcher liest die freien Einträge und
 * verteilt sie an die Worker (deshalb kein DB-Locking), der Worker meldet zurück: fertig heißt Eintrag löschen, Fehler
 * heißt attempts hochzählen und die Meldung ablegen.
 *
 * Primärschlüssel ist die Sternzeit selbst: Pusht ABAS dieselbe Kennung zehnmal, steht sie trotzdem nur einmal hier,
 * Einreihen ist von selbst idempotent. Bewusst kein Fremdschlüssel auf {@link AbasSternzeit}: Die Queue muss Kennungen
 * tragen, die wir noch nie gesehen haben, der Stammsatz entsteht erst nach dem ersten erfolgreichen Fetch. Der
 * Fehlerzähler lebt nur hier, ein Fetch-Versuch gehört zum Arbeitsauftrag, nicht zum Stammsatz.
 *
 * Ein Import kann auch hier 10^8 Zeilen hineinkippen, deshalb dieselbe Collation wie beim Stammsatz. Der Index
 * {@code (claimed_at, queued_at)} bedient die eine Abfrage des Dispatchers, „die freien, älteste zuerst“, vollständig;
 * {@code (queued_at)} die Anzeige, die ungefiltert nach Alter blättert. Die Teilindizes für „in Arbeit“ und „mit
 * Fehlern“ stehen in docs/backend/abas-indizes.sql.
 */
@Entity
@Table(name = "abas_sternzeit_queue", indexes = {
		@Index(name = "idx_abas_sternzeit_queue_queued", columnList = "queued_at"),
		@Index(name = "idx_abas_sternzeit_queue_free", columnList = "claimed_at, queued_at") })
public class AbasSternzeitQueueEntry {

	/** Die ABAS-Kennung in Klammerform, dasselbe Maß wie am Stammsatz */
	@Id
	@Column(name = "sternzeit", nullable = false, updatable = false, columnDefinition = "varchar(30) collate \"C\"")
	private String sternzeit;

	/**
	 * FETCH oder DELETE. Der Letzte gewinnt: Kommt dieselbe Sternzeit mit der anderen Aktion, stellt das Einreihen den
	 * wartenden Eintrag um, queuedAt und ein laufender Claim bleiben; der Worker löscht beim Abschluss nur, wenn die Aktion
	 * noch seine ist.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 6)
	private AbasSternzeitQueueAction action;

	/**
	 * Wer eingereiht hat. Stellt mit der Aktion um: Wer zuletzt eingereiht hat, dem gehört der Eintrag. 10 Zeichen seit
	 * IMPORT (2026-10-13); eine bestehende Tabelle zieht ddl-auto nicht nach, Spalte und Check-Constraint hat ein
	 * Einmal-Skript erweitert.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false, length = 10)
	private AbasSternzeitQueueSource source;

	/** Wann der Eintrag in die Queue kam, die FIFO-Ordnung des Dispatchers */
	@Column(name = "queued_at", nullable = false)
	private Instant queuedAt;

	/**
	 * null heißt frei, gesetzt heißt ein Worker arbeitet daran. Nur für die Absturz-Erholung, nicht fürs Locking: Stirbt
	 * das Backend mit vergebenen Einträgen, gibt der Watcher Claims frei, die älter als die Frist sind.
	 */
	@Column(name = "claimed_at")
	private Instant claimedAt;

	/**
	 * Fehlversuche dieser Sternzeit. Ab max-attempts zieht der Dispatcher den Eintrag nicht mehr, er bleibt mit seinem
	 * Fehler stehen, damit man ihn ansehen kann; der Zähler ist der Zustand, kein zweites Kennzeichen.
	 */
	@Column(name = "attempts", nullable = false)
	private int attempts;

	/** Die letzte Fehlermeldung im Klartext, direkt am Eintrag statt nur im Server-Log */
	@Column(name = "last_error_message", columnDefinition = "text")
	private String lastErrorMessage;

	/** Für JPA und Jackson. */
	protected AbasSternzeitQueueEntry() {
	}

	/** Ein neuer Eintrag: frei, ohne Fehlversuch, eingereiht jetzt. */
	public AbasSternzeitQueueEntry(String sternzeit, AbasSternzeitQueueAction action, AbasSternzeitQueueSource source) {
		this.sternzeit = sternzeit;
		this.action = action;
		this.source = source;
		this.queuedAt = Instant.now();
		this.attempts = 0;
	}

	public String getSternzeit() {
		return sternzeit;
	}

	public AbasSternzeitQueueAction getAction() {
		return action;
	}

	public void setAction(AbasSternzeitQueueAction action) {
		this.action = action;
	}

	public AbasSternzeitQueueSource getSource() {
		return source;
	}

	public void setSource(AbasSternzeitQueueSource source) {
		this.source = source;
	}

	public Instant getQueuedAt() {
		return queuedAt;
	}

	public Instant getClaimedAt() {
		return claimedAt;
	}

	public void setClaimedAt(Instant claimedAt) {
		this.claimedAt = claimedAt;
	}

	public int getAttempts() {
		return attempts;
	}

	public void setAttempts(int attempts) {
		this.attempts = attempts;
	}

	public String getLastErrorMessage() {
		return lastErrorMessage;
	}

	public void setLastErrorMessage(String lastErrorMessage) {
		this.lastErrorMessage = lastErrorMessage;
	}

}
