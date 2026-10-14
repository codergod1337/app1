package codergod1337.app1.solr.nachrichten.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Der Zähler der Nachrichten-ids (User 2026-10-14): Jede Nachricht im Kern NACHRICHTEN bekommt eine laufende Nummer, und
 * die braucht es nur, weil Solr allein keine vergibt. Je Anfrage nach der nächsten id entsteht eine Zeile, ihre id ist die
 * nächste Nummer, um eins höher als die letzte. So kommt jede Nummer genau einmal vor, auch wenn zwei gleichzeitig
 * archivieren.
 */
@Entity
@Table(name = "counter_nachrichten_id")
public class CounterNachrichtenId {

	/** Die laufende Nummer, vergibt die Datenbank */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	public Long getId() {
		return id;
	}

}
