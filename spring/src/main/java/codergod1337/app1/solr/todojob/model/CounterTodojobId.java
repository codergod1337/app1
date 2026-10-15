package codergod1337.app1.solr.todojob.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Der Zähler der Job-ids (User 2026-10-16, wie CounterNachrichtenId): Jeder Job im Kern TODOJOB bekommt eine laufende
 * Nummer, „Job 4711“, und die braucht es nur, weil Solr allein keine vergibt. Je Anfrage nach der nächsten id entsteht
 * eine Zeile, ihre id ist die nächste Nummer. So kommt jede Nummer genau einmal vor, auch wenn zwei gleichzeitig erteilen.
 */
@Entity
@Table(name = "counter_todojob_id")
public class CounterTodojobId {

	/** Die laufende Nummer, vergibt die Datenbank */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	public Long getId() {
		return id;
	}

}
