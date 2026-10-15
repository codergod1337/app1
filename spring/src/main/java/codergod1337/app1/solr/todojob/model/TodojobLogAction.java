package codergod1337.app1.solr.todojob.model;

/**
 * Die Aktionen im Log eines Jobs (User 2026-10-16): je Eintrag ein JSON-String mit at, action, user und je nach Aktion
 * text, nr, rueckmeldungBis oder snapshot. Nur angehängt per Atomic Update ADD, nie ersetzt. Beiträge zur Rückmeldung
 * stehen nicht hier, sie tragen ihren Stempel selbst in der Liste rueckmeldung.
 */
public enum TodojobLogAction {

	/** erteilt */
	CREATED,
	/** zum ersten Mal geöffnet, je Servant einmal */
	READ,
	CLAIMED,
	CLAIM_RELEASED,
	/** ein anderes Gruppenmitglied hat den Claim an sich gezogen, text ist seine Begründung */
	CLAIM_TAKEN,
	/** Rückfrage des Servants, nr zeigt in die Liste rueckfragen */
	QUESTION,
	/** Rückantwort des Owners, nr wie die Rückfrage */
	ANSWER,
	/** fertig gemeldet */
	RESPONDED,
	/** der Owner hat die Rückmeldung nicht angenommen: text seine Begründung, rueckmeldungBis die letzte abgelehnte nr */
	RETURNED,
	/** der Owner hat bestätigt, Ausgang GUT_GEMACHT */
	CONFIRMED,
	/** die Frist ist abgelaufen, Ausgang VERFALLEN */
	EXPIRED,
	/** der Owner hat den Job zurückgezogen, Ausgang ZURUECKGEZOGEN */
	WITHDRAWN,
	DUE_DATE_CHANGED,
	/** Labels oder dauerhaftRelevant geändert */
	LABELS_CHANGED,
	/** ein Kommentar am Job, von Owner oder Servant */
	COMMENT

}
